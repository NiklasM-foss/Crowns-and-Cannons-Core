package de.niklasmetzger.crownscore.evidence;

import de.niklasmetzger.crownscore.claims.Claims;
import de.niklasmetzger.crownscore.config.CrownsConfig;
import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.Nation;
import de.niklasmetzger.crownscore.data.War;
import de.niklasmetzger.crownscore.util.Msg;
import de.niklasmetzger.crownscore.util.Perms;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Beobachtet jede Sekunde, wer sich wo aufhaelt:
 * Camping vor fremden Basen (Regel 4.5) und Einreise ohne Visum (Regel 5.1).
 */
public final class PresenceTracker {
    private static final class State {
        String nearNation;
        int nearSeconds;
        int awaySeconds;
        long lastCampingReport;
        String inNation;
        int groundedSeconds;
        long lastVisaReport;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private PresenceTracker() {}

    public static void forget(UUID id) {
        STATES.remove(id);
    }

    public static void everySecond(MinecraftServer server) {
        CrownsData d = CrownsData.get(server);
        long now = System.currentTimeMillis();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isSpectator()) continue;
            State s = STATES.computeIfAbsent(p.getUUID(), k -> new State());
            ServerLevel level = p.serverLevel();
            String own = d.nationKeyOf(p.getUUID());
            String in = Claims.nationAt(level, p.blockPosition());
            // Camping zaehlt nur vor echten Bases (Claims, Stadtgebiete), nicht im ganzen fremden Land
            String near = Claims.baseNear(level, p.blockPosition());
            boolean admin = Perms.isAdmin(p);

            // ---- Camping: in oder direkt neben fremdem Gebiet ----
            if (near != null && !near.equals(own)) {
                if (near.equals(s.nearNation)) {
                    s.nearSeconds++;
                } else {
                    s.nearNation = near;
                    s.nearSeconds = 1;
                }
                s.awaySeconds = 0;
                int limit = CrownsConfig.CAMPING_MINUTEN.get() * 60;
                if (s.nearSeconds >= limit && now - s.lastCampingReport > CrownsConfig.CAMPING_MELDUNG_MINUTEN.get() * 60_000L && !admin) {
                    s.lastCampingReport = now;
                    War w = own == null ? null : d.activeWar(own, near);
                    String ctx = w != null ? " (waehrend " + w.art.anzeige + " Nr. " + w.id + ")" : "";
                    EvidenceLog.log(p, level, p.blockPosition(), "CAMPING", near,
                            "seit " + (s.nearSeconds / 60) + " Minuten vor oder in der Base" + ctx);
                    Nation owner = d.nations.get(near);
                    Msg.nation(server, owner, "§e" + p.getGameProfile().getName() + " haelt sich seit " + (s.nearSeconds / 60)
                            + " Minuten in eurem Gebiet oder direkt davor auf (bei " + p.blockPosition().toShortString() + ").");
                    Msg.player(p, "§eDu stehst seit " + (s.nearSeconds / 60) + " Minuten vor einer fremden Base. Regel 4.5, das ist jetzt in der Akte.");
                }
            } else if (s.nearNation != null) {
                s.awaySeconds++;
                if (s.awaySeconds > 60) {
                    s.nearNation = null;
                    s.nearSeconds = 0;
                    s.awaySeconds = 0;
                }
            }

            // ---- Einreise ohne Visum: nur im Gebiet und nur am Boden ----
            if (in != null && !in.equals(own) && !admin) {
                if (!in.equals(s.inNation)) {
                    s.inNation = in;
                    s.groundedSeconds = 0;
                }
                if (p.onGround() && !p.isPassenger()) s.groundedSeconds++;
                boolean krieg = own != null && d.activeWar(own, in) != null;
                boolean visum = d.visa(p.getUUID(), in) != null;
                if (!krieg && !visum && s.groundedSeconds >= CrownsConfig.VISUM_SEKUNDEN.get()
                        && now - s.lastVisaReport > CrownsConfig.VISUM_MELDUNG_MINUTEN.get() * 60_000L) {
                    s.lastVisaReport = now;
                    EvidenceLog.log(p, level, p.blockPosition(), "EINREISE", in, "ohne Visum, " + s.groundedSeconds + " s am Boden");
                    Nation owner = d.nations.get(in);
                    Msg.nation(server, owner, "§c" + p.getGameProfile().getName()
                            + (own == null ? "" : " (" + d.anzeige(own) + ")") + " ist ohne Visum in eurem Gebiet bei "
                            + p.blockPosition().toShortString() + ". Visum erteilen: /visum erteilen " + p.getGameProfile().getName());
                    Msg.player(p, "§cDu bist ohne Visum im Gebiet von " + d.anzeige(in) + ". Die Nation wurde informiert (Regel 5.1).");
                }
            } else {
                s.inNation = null;
                s.groundedSeconds = 0;
            }
        }
    }
}
