package de.niklasmetzger.crownscore.perms;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import de.niklasmetzger.crownscore.CrownsCore;
import de.niklasmetzger.crownscore.help.CommandCatalog;
import de.niklasmetzger.crownscore.help.CommandCatalog.Entry;
import de.niklasmetzger.crownscore.util.Perms;
import net.minecraft.commands.CommandSourceStack;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Sichtbarkeit der eigenen Befehle: Jeder Knoten im Befehlsbaum bekommt die Rolle und den Rechteknoten
 * aus der Befehlsliste. Wer etwas nicht darf, sieht es in der Tab-Vervollstaendigung nicht und kann es
 * auch nicht ausfuehren ("Unbekannter Befehl").
 */
public final class CommandGate {
    private static Field requirementField;

    private CommandGate() {}

    static Field field() throws ReflectiveOperationException {
        if (requirementField == null) {
            Field f = CommandNode.class.getDeclaredField("requirement");
            f.setAccessible(true);
            requirementField = f;
        }
        return requirementField;
    }

    private record Gate(List<Entry> entries, Predicate<CommandSourceStack> original) implements Predicate<CommandSourceStack> {
        @Override
        public boolean test(CommandSourceStack src) {
            if (!original.test(src)) return false;
            for (Entry e : entries) {
                if (Perms.allowed(src, e.node(), e.role())) return true;
            }
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public static int install(CommandDispatcher<CommandSourceStack> dispatcher) {
        Map<CommandNode<CommandSourceStack>, List<Entry>> map = new IdentityHashMap<>();
        for (Entry e : CommandCatalog.entries()) {
            CommandNode<CommandSourceStack> node = dispatcher.getRoot();
            boolean found = true;
            for (String lit : e.literals()) {
                CommandNode<CommandSourceStack> child = node.getChild(lit);
                if (child == null) {
                    CrownsCore.LOGGER.warn("Befehlsliste: {} nicht im Befehlsbaum gefunden", e.syntax());
                    found = false;
                    break;
                }
                map.computeIfAbsent(child, k -> new ArrayList<>()).add(e);
                node = child;
            }
            if (found && e.hasArguments()) {
                for (CommandNode<CommandSourceStack> child : node.getChildren()) {
                    if (child instanceof ArgumentCommandNode) map.computeIfAbsent(child, k -> new ArrayList<>()).add(e);
                }
            }
        }
        try {
            Field f = field();
            for (Map.Entry<CommandNode<CommandSourceStack>, List<Entry>> me : map.entrySet()) {
                Predicate<CommandSourceStack> req = (Predicate<CommandSourceStack>) f.get(me.getKey());
                if (req instanceof Gate) continue;
                f.set(me.getKey(), new Gate(List.copyOf(me.getValue()), req));
            }
            return map.size();
        } catch (Throwable t) {
            CrownsCore.LOGGER.warn("Befehle koennen nicht nach Rechten ausgeblendet werden: {}", t.toString());
            return 0;
        }
    }
}
