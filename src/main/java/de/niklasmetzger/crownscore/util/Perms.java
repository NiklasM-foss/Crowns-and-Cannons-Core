package de.niklasmetzger.crownscore.util;

import de.niklasmetzger.crownscore.config.CrownsConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Rechteprüfung: OP-Level 2 oder Name in der Admin-Liste der Config. */
public final class Perms {
    private Perms() {}

    public static boolean isAdmin(CommandSourceStack src) {
        if (src.hasPermission(2)) return true;
        Entity e = src.getEntity();
        return e instanceof ServerPlayer p && isAdmin(p);
    }

    public static boolean isAdmin(ServerPlayer p) {
        if (p.hasPermissions(2)) return true;
        String name = p.getGameProfile().getName();
        for (String s : CrownsConfig.ADMINS.get()) {
            if (s.equalsIgnoreCase(name)) return true;
        }
        return false;
    }
}
