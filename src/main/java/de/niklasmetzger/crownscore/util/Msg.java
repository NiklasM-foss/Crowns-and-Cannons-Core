package de.niklasmetzger.crownscore.util;

import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.Nation;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/** Einheitliche Chat-Ausgaben mit Praefix und Farben. */
public final class Msg {
    public static final String PREFIX = "§6[CC] §r";

    private Msg() {}

    public static Component text(String s) {
        return Component.literal(PREFIX + s);
    }

    public static void ok(CommandSourceStack src, String s) {
        src.sendSuccess(() -> Component.literal(PREFIX + "§a" + s), false);
    }

    public static void info(CommandSourceStack src, String s) {
        src.sendSuccess(() -> Component.literal(PREFIX + s), false);
    }

    public static void line(CommandSourceStack src, String s) {
        src.sendSuccess(() -> Component.literal(s), false);
    }

    public static void err(CommandSourceStack src, String s) {
        src.sendFailure(Component.literal(PREFIX + "§c" + s));
    }

    public static void player(ServerPlayer p, String s) {
        p.sendSystemMessage(Component.literal(PREFIX + s));
    }

    public static void broadcast(MinecraftServer server, String s) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(PREFIX + "§e" + s), false);
    }

    /** Nachricht an alle Online-Mitglieder einer Nation. */
    public static void nation(MinecraftServer server, Nation n, String s) {
        if (n == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (n.isMember(p.getUUID())) player(p, s);
        }
    }

    public static void nation(MinecraftServer server, CrownsData data, String nationKey, String s) {
        nation(server, data.nation(nationKey), s);
    }

    public static String money(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.005) return String.format(Locale.GERMANY, "%,.0f", v);
        return String.format(Locale.GERMANY, "%,.2f", v);
    }

    public static String hours(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        return h + "h " + String.format("%02d", m) + "m";
    }
}
