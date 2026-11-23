package de.niklasmetzger.crownscore.util;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.Nation;
import de.niklasmetzger.crownscore.data.WarType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/** Gemeinsame Bausteine fuer alle Commands. */
public final class CommandUtil {
    private CommandUtil() {}

    public static final SuggestionProvider<CommandSourceStack> NATIONS = (ctx, b) -> {
        CrownsData d = CrownsData.get(ctx.getSource().getServer());
        List<String> out = new ArrayList<>();
        for (Nation n : d.nations.values()) {
            out.add(n.kuerzel);
            out.add(com.mojang.brigadier.arguments.StringArgumentType.escapeIfRequired(n.name));
        }
        return SharedSuggestionProvider.suggest(out, b);
    };

    public static final SuggestionProvider<CommandSourceStack> WAR_TYPES = (ctx, b) -> {
        List<String> out = new ArrayList<>();
        for (WarType t : WarType.values()) out.add(t.name());
        return SharedSuggestionProvider.suggest(out, b);
    };

    public static final SuggestionProvider<CommandSourceStack> MEMBERS_OF_OWN_NATION = (ctx, b) -> {
        CrownsData d = CrownsData.get(ctx.getSource().getServer());
        List<String> out = new ArrayList<>();
        if (ctx.getSource().getEntity() instanceof ServerPlayer p) {
            Nation n = d.nationOf(p.getUUID());
            if (n != null) out.addAll(n.mitglieder.values());
        }
        return SharedSuggestionProvider.suggest(out, b);
    };

    public static CrownsData data(CommandContext<CommandSourceStack> ctx) {
        return CrownsData.get(ctx.getSource().getServer());
    }

    public static ServerPlayer player(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ctx.getSource().getPlayerOrException();
    }

    /** Nation nach Name oder Kuerzel, sonst Fehlermeldung und null. */
    public static Nation nation(CommandSourceStack src, CrownsData d, String arg) {
        Nation n = d.nation(arg);
        if (n == null) Msg.err(src, "Nation \"" + arg + "\" gibt es nicht. /nation liste zeigt alle.");
        return n;
    }

    /** Eigene Nation des Spielers, sonst Fehlermeldung und null. */
    public static Nation own(CommandSourceStack src, CrownsData d, ServerPlayer p) {
        Nation n = d.nationOf(p.getUUID());
        if (n == null) Msg.err(src, "Du gehoerst keiner Nation an. /nation beitreten <Land>");
        return n;
    }

    public static boolean chef(CommandSourceStack src, Nation n, ServerPlayer p) {
        if (n.isChef(p.getUUID()) || Perms.isAdmin(src)) return true;
        Msg.err(src, "Das darf nur der Staatschef von " + n.name + ".");
        return false;
    }

    public static boolean beamter(CommandSourceStack src, Nation n, ServerPlayer p) {
        if (n.isBeamter(p.getUUID()) || Perms.isAdmin(src)) return true;
        Msg.err(src, "Das duerfen nur Staatschef und Beamte von " + n.name + ".");
        return false;
    }

    public static boolean admin(CommandSourceStack src) {
        if (Perms.isAdmin(src)) return true;
        Msg.err(src, "Nur fuer Admins.");
        return false;
    }

    public static <T> List<T> page(List<T> list, int page, int size) {
        int from = Math.max(0, (page - 1) * size);
        if (from >= list.size()) return List.of();
        return list.subList(from, Math.min(list.size(), from + size));
    }

    public static int pages(int count, int size) {
        return Math.max(1, (count + size - 1) / size);
    }
}
