package de.niklasmetzger.crownscore.war;

import de.niklasmetzger.crownscore.config.CrownsConfig;
import de.niklasmetzger.crownscore.util.Msg;
import de.niklasmetzger.crownscore.util.Names;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Duelle: freiwilliger Zweikampf ausserhalb jedes Krieges, nur im Speicher. */
public final class DuelManager {
    private record Duel(UUID a, UUID b, long ende) {}

    private static final List<Duel> DUELS = new ArrayList<>();
    /** Herausgeforderter -> Herausforderer, mit Ablaufzeit. */
    private static final Map<UUID, UUID> REQUESTS = new HashMap<>();
    private static final Map<UUID, Long> REQUEST_TIME = new HashMap<>();

    private DuelManager() {}

    public static boolean inDuel(UUID a, UUID b) {
        for (Duel d : DUELS) {
            if ((d.a.equals(a) && d.b.equals(b)) || (d.a.equals(b) && d.b.equals(a))) return true;
        }
        return false;
    }

    public static boolean busy(UUID p) {
        for (Duel d : DUELS) if (d.a.equals(p) || d.b.equals(p)) return true;
        return false;
    }

    public static void request(ServerPlayer from, ServerPlayer to) {
        REQUESTS.put(to.getUUID(), from.getUUID());
        REQUEST_TIME.put(to.getUUID(), System.currentTimeMillis());
        Msg.player(from, "Herausforderung an " + to.getGameProfile().getName() + " gesendet (60 Sekunden gueltig).");
        Msg.player(to, "§e" + from.getGameProfile().getName() + " fordert dich zum Duell. Annehmen: /duell annehmen");
    }

    public static boolean accept(ServerPlayer to) {
        UUID from = REQUESTS.remove(to.getUUID());
        REQUEST_TIME.remove(to.getUUID());
        if (from == null) {
            Msg.player(to, "§cKeine offene Herausforderung.");
            return false;
        }
        ServerPlayer challenger = to.server.getPlayerList().getPlayer(from);
        if (challenger == null) {
            Msg.player(to, "§cDer Herausforderer ist nicht mehr online.");
            return false;
        }
        long ende = System.currentTimeMillis() + CrownsConfig.DUELL_MINUTEN.get() * 60_000L;
        DUELS.add(new Duel(from, to.getUUID(), ende));
        Msg.broadcast(to.server, "Duell: " + challenger.getGameProfile().getName() + " gegen " + to.getGameProfile().getName()
                + " (" + CrownsConfig.DUELL_MINUTEN.get() + " Minuten).");
        return true;
    }

    public static boolean cancel(ServerPlayer p) {
        boolean removed = DUELS.removeIf(d -> d.a.equals(p.getUUID()) || d.b.equals(p.getUUID()));
        if (removed) {
            Msg.broadcast(p.server, p.getGameProfile().getName() + " hat das Duell abgebrochen.");
            return true;
        }
        // Offene Forderung zurueckziehen (als Herausforderer) oder ablehnen (als Herausgeforderter)
        UUID an = null;
        for (Map.Entry<UUID, UUID> e : REQUESTS.entrySet()) if (e.getValue().equals(p.getUUID())) an = e.getKey();
        if (an != null) {
            REQUESTS.remove(an);
            REQUEST_TIME.remove(an);
            Msg.player(p, "Herausforderung zurueckgezogen.");
            ServerPlayer ziel = p.server.getPlayerList().getPlayer(an);
            if (ziel != null) Msg.player(ziel, "§e" + p.getGameProfile().getName() + " hat die Herausforderung zurueckgezogen.");
            return true;
        }
        UUID von = REQUESTS.remove(p.getUUID());
        if (von != null) {
            REQUEST_TIME.remove(p.getUUID());
            Msg.player(p, "Herausforderung abgelehnt.");
            ServerPlayer h = p.server.getPlayerList().getPlayer(von);
            if (h != null) Msg.player(h, "§e" + p.getGameProfile().getName() + " lehnt das Duell ab.");
            return true;
        }
        return false;
    }

    public static void onDeath(MinecraftServer server, UUID dead) {
        Iterator<Duel> it = DUELS.iterator();
        while (it.hasNext()) {
            Duel d = it.next();
            if (d.a.equals(dead) || d.b.equals(dead)) {
                UUID winner = d.a.equals(dead) ? d.b : d.a;
                Msg.broadcast(server, "Duell entschieden: " + Names.of(server, winner) + " besiegt " + Names.of(server, dead) + ".");
                it.remove();
            }
        }
    }

    public static void everySecond(MinecraftServer server) {
        long now = System.currentTimeMillis();
        Iterator<Duel> it = DUELS.iterator();
        while (it.hasNext()) {
            Duel d = it.next();
            boolean offline = server.getPlayerList().getPlayer(d.a) == null || server.getPlayerList().getPlayer(d.b) == null;
            if (now > d.ende || offline) {
                Msg.broadcast(server, "Duell zwischen " + Names.of(server, d.a) + " und " + Names.of(server, d.b)
                        + (offline ? " abgebrochen (Spieler offline)." : " endet unentschieden."));
                it.remove();
            }
        }
        REQUEST_TIME.entrySet().removeIf(e -> {
            if (now - e.getValue() > 60_000L) {
                REQUESTS.remove(e.getKey());
                return true;
            }
            return false;
        });
    }
}
