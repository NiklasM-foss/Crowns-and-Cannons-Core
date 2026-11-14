package de.niklasmetzger.crownscore.mode;

import de.niklasmetzger.crownscore.config.CrownsConfig;
import de.niklasmetzger.crownscore.util.Msg;
import de.niklasmetzger.crownscore.util.TimeUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Haelt den Modus jedes Spielers. Beim Einloggen ist jeder im RP-Modus, AFK laeuft wie eine
 * Parkscheibe bis zu einer Uhrzeit und faellt danach von selbst zurueck auf RP.
 */
public final class ModeService {
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    public static final class State {
        public PlayerMode mode = PlayerMode.RP;
        public long seit = TimeUtil.now();
        /** Nur bei AFK: Ende der Parkscheibe. */
        public long bis;
    }

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();
    /** Angreifer zu (Ziel zu Ablaufzeit) fuer angegebene Gruende. */
    private static final Map<UUID, Map<UUID, Long>> GRUENDE = new HashMap<>();
    private static final Map<UUID, Long> LETZTER_KAMPF = new HashMap<>();

    private ModeService() {}

    public static State state(UUID id) {
        return STATES.computeIfAbsent(id, k -> new State());
    }

    public static PlayerMode mode(UUID id) {
        State s = STATES.get(id);
        return s == null ? PlayerMode.RP : s.mode;
    }

    public static void reset(ServerPlayer p) {
        STATES.put(p.getUUID(), new State());
        refresh(p);
    }

    public static void forget(UUID id) {
        STATES.remove(id);
        LETZTER_KAMPF.remove(id);
    }

    /** Sekunden, die der Spieler wegen eines Kampfes noch im RP bleiben muss. */
    public static long kampfSperre(UUID id) {
        Long t = LETZTER_KAMPF.get(id);
        if (t == null) return 0;
        long rest = t + CrownsConfig.MODUS_KAMPFSPERRE_SEKUNDEN.get() * 1000L - TimeUtil.now();
        return Math.max(0, (rest + 999) / 1000);
    }

    public static void kampf(UUID... ids) {
        long now = TimeUtil.now();
        for (UUID id : ids) LETZTER_KAMPF.put(id, now);
    }

    public static void set(ServerPlayer p, PlayerMode mode, long bis) {
        State s = state(p.getUUID());
        s.mode = mode;
        s.seit = TimeUtil.now();
        s.bis = mode == PlayerMode.AFK ? bis : 0;
        refresh(p);
    }

    public static String anzeige(State s) {
        if (s.mode == PlayerMode.AFK && s.bis > 0) return s.mode.color + "AFK bis " + HHMM.format(TimeUtil.zoned(s.bis));
        return s.mode.color + s.mode.label;
    }

    public static String uhrzeit(long millis) {
        return HHMM.format(TimeUtil.zoned(millis));
    }

    public static void grund(UUID angreifer, UUID ziel) {
        GRUENDE.computeIfAbsent(angreifer, k -> new HashMap<>())
                .put(ziel, TimeUtil.now() + CrownsConfig.MODUS_GRUND_MINUTEN.get() * 60_000L);
    }

    public static boolean hatGrund(UUID angreifer, UUID ziel) {
        Map<UUID, Long> m = GRUENDE.get(angreifer);
        if (m == null) return false;
        Long bis = m.get(ziel);
        return bis != null && bis > TimeUtil.now();
    }

    /** Wird jede Sekunde aufgerufen: abgelaufene Parkscheiben und Gruende aufraeumen. */
    public static void everySecond(MinecraftServer server) {
        long now = TimeUtil.now();
        for (Map.Entry<UUID, State> e : STATES.entrySet()) {
            State s = e.getValue();
            if (s.mode != PlayerMode.AFK || s.bis <= 0 || s.bis > now) continue;
            s.mode = PlayerMode.RP;
            s.seit = now;
            s.bis = 0;
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null) {
                refresh(p);
                Msg.player(p, "§eDeine AFK-Zeit ist abgelaufen. Du bist wieder im §aRP-Modus§e.");
            }
        }
        for (Iterator<Map<UUID, Long>> it = GRUENDE.values().iterator(); it.hasNext(); ) {
            Map<UUID, Long> m = it.next();
            m.values().removeIf(t -> t <= now);
            if (m.isEmpty()) it.remove();
        }
    }

    /** Tabliste und Anzeigename neu aufbauen. */
    public static void refresh(ServerPlayer p) {
        p.refreshDisplayName();
        p.refreshTabListName();
    }
}
