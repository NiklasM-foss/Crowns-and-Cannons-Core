package de.niklasmetzger.crownscore.perms;

import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.Nation;
import de.niklasmetzger.crownscore.util.Perms;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Wer Staatschef, Beamter, Buerger oder Admin wird, sieht danach andere Befehle. Der Client kennt nur den
 * Befehlsbaum, den der Server ihm schickt, deshalb wird er bei jeder Rollenaenderung neu gesendet.
 */
public final class RoleWatcher {
    private static final Map<UUID, String> LAST = new HashMap<>();

    private RoleWatcher() {}

    public static void everySecond(MinecraftServer server) {
        CrownsData d = CrownsData.get(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Nation n = d.nationOf(p.getUUID());
            String sig = (Perms.isAdmin(p) ? "A" : "-")
                    + (n == null ? "" : n.key() + (n.isChef(p.getUUID()) ? ":C" : n.isBeamter(p.getUUID()) ? ":B" : ":M"));
            String old = LAST.put(p.getUUID(), sig);
            if (old != null && !old.equals(sig)) server.getCommands().sendCommands(p);
        }
        LAST.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
    }
}
