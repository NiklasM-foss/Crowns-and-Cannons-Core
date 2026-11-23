package de.niklasmetzger.crownscore.perms;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import de.niklasmetzger.crownscore.CrownsCore;
import de.niklasmetzger.crownscore.perms.PermissionManager.Tri;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.util.function.Predicate;

/**
 * Haengt an jeden Wurzelbefehl das Recht command.<name>: true gibt den Befehl frei (auch ohne OP),
 * false sperrt ihn, ohne Eintrag gilt die normale Pruefung des Befehls.
 */
public final class CommandPermissionHook {
    private static boolean failed;

    private CommandPermissionHook() {}

    /** Markiert bereits umwickelte Pruefungen, damit /reload sie nicht doppelt einpackt. */
    private record Wrapped(String name, Predicate<CommandSourceStack> original) implements Predicate<CommandSourceStack> {
        @Override
        public boolean test(CommandSourceStack src) {
            if (enabled() && src.getEntity() instanceof ServerPlayer p) {
                Tri t = PermissionManager.check(p, "command." + name);
                if (t == Tri.TRUE) return true;
                if (t == Tri.FALSE) {
                    // Ein Platzhalter-Verbot (z.B. *=false in default) sperrt Admins nicht aus, nur ein genaues Verbot
                    if (!de.niklasmetzger.crownscore.util.Perms.isAdmin(p)) return false;
                    String k = PermissionManager.checkDetailed(p.getUUID(), "command." + name, p.level().dimension().location().toString()).knoten();
                    if (!k.endsWith("*")) return false;
                }
            }
            return original.test(src);
        }
    }

    private static boolean enabled() {
        try {
            return de.niklasmetzger.crownscore.config.CrownsConfig.RECHTE_BEFEHLE.get();
        } catch (IllegalStateException e) {
            return false; // Config noch nicht geladen
        }
    }

    @SuppressWarnings("unchecked")
    public static int install(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (failed) return 0;
        try {
            Field requirementField = CommandGate.field();
            int n = 0;
            for (CommandNode<CommandSourceStack> node : dispatcher.getRoot().getChildren()) {
                Predicate<CommandSourceStack> req = (Predicate<CommandSourceStack>) requirementField.get(node);
                if (req instanceof Wrapped) continue;
                requirementField.set(node, new Wrapped(node.getName().toLowerCase(java.util.Locale.ROOT), req));
                n++;
            }
            return n;
        } catch (Throwable t) {
            failed = true;
            CrownsCore.LOGGER.warn("Befehlsrechte (command.<name>) nicht verfuegbar: {}", t.toString());
            return 0;
        }
    }
}
