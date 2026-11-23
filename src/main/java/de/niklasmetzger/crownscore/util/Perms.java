package de.niklasmetzger.crownscore.util;

import de.niklasmetzger.crownscore.config.CrownsConfig;
import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.Nation;
import de.niklasmetzger.crownscore.help.CommandCatalog.Role;
import de.niklasmetzger.crownscore.perms.PermissionManager;
import de.niklasmetzger.crownscore.perms.PermissionManager.Tri;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Rechtepruefung. Reihenfolge: ausdrueckliches Recht aus /cccore perms, sonst OP-Level 2
 * oder Name in der Admin-Liste der Config.
 */
public final class Perms {
    public static final String ADMIN_NODE = "crownscore.admin";

    private Perms() {}

    public static boolean isAdmin(CommandSourceStack src) {
        Entity e = src.getEntity();
        if (e instanceof ServerPlayer p) return isAdmin(p);
        return src.hasPermission(2);
    }

    public static boolean isAdmin(ServerPlayer p) {
        Tri t = PermissionManager.check(p, ADMIN_NODE);
        if (t != Tri.UNDEFINED) return t == Tri.TRUE;
        if (p.hasPermissions(2)) return true;
        String name = p.getGameProfile().getName();
        for (String s : CrownsConfig.ADMINS.get()) {
            if (s.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    /** Recht mit Rueckfall: ohne Eintrag entscheidet, ob der Spieler Admin ist. */
    public static boolean has(CommandSourceStack src, String node) {
        if (!(src.getEntity() instanceof ServerPlayer p)) return src.hasPermission(2);
        Tri t = PermissionManager.check(p, node);
        if (t != Tri.UNDEFINED) return t == Tri.TRUE;
        return isAdmin(p);
    }

    /**
     * Darf die Quelle einen Befehl mit dieser Rolle und diesem Rechteknoten nutzen? Ein ausdrueckliches
     * Recht gewinnt, sonst zaehlt die Rolle in der Nation. Admins duerfen alles, die Konsole auch.
     */
    public static boolean allowed(CommandSourceStack src, String node, Role role) {
        if (!(src.getEntity() instanceof ServerPlayer p)) return true;
        Tri t = PermissionManager.check(p, node);
        if (t == Tri.TRUE && role == Role.ADMIN && !isAdmin(p)) {
            // Admin-Befehle gibt nur der genaue Knoten frei, nie crownscore.* oder * (sonst Rechteausweitung)
            PermissionManager.Result r = PermissionManager.checkDetailed(p.getUUID(), node, p.level().dimension().location().toString());
            return PermissionManager.norm(r.knoten()).equals(PermissionManager.norm(node));
        }
        if (t != Tri.UNDEFINED) return t == Tri.TRUE;
        if (role == Role.ALLE) return true;
        if (isAdmin(p)) return true;
        if (role == Role.ADMIN) return false;
        Nation n = CrownsData.get(p.server).nationOf(p.getUUID());
        if (n == null) return false;
        return switch (role) {
            case BUERGER -> true;
            case BEAMTER -> n.isBeamter(p.getUUID());
            case CHEF -> n.isChef(p.getUUID());
            default -> false;
        };
    }
}
