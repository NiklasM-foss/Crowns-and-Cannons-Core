package de.niklasmetzger.crownscore.flag;

import de.niklasmetzger.crownscore.CrownsCore;
import de.niklasmetzger.crownscore.config.CrownsConfig;
import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.Nation;
import de.niklasmetzger.crownscore.net.CcNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verteilt die Umhaenge: jeder Buerger traegt die Flagge seiner Nation (eigene Flagge, sonst die
 * Landesflagge seines Stammlands). Einmal pro Sekunde bekommt jeder Client mit Crowns Core nur das,
 * was sich geaendert hat, und jede Textur nur einmal.
 */
public final class CapeService {
    /** Fertige Umhang-Texturen: Hash -> PNG. */
    private static final Map<String, byte[]> TEXTURES = new ConcurrentHashMap<>();
    /** Quelle (Flaggenbild + Drehung) -> Hash der fertigen Textur. */
    private static final Map<String, String> BY_SOURCE = new ConcurrentHashMap<>();
    private static final Map<byte[], String> SOURCE_HASH = new IdentityHashMap<>();

    private static final class Viewer {
        boolean init;
        final Set<String> sent = new HashSet<>();
        Map<UUID, String> known = new HashMap<>();
    }

    private record Preview(String hash, long until) {}

    private static final Map<UUID, Viewer> VIEWERS = new ConcurrentHashMap<>();
    private static final Map<UUID, Preview> PREVIEWS = new ConcurrentHashMap<>();

    private CapeService() {}

    public static void reset() {
        VIEWERS.clear();
        PREVIEWS.clear();
        TEXTURES.clear();
        BY_SOURCE.clear();
        synchronized (SOURCE_HASH) {
            SOURCE_HASH.clear();
        }
    }

    private static boolean rechts() {
        return !"LINKS".equalsIgnoreCase(CrownsConfig.UMHANG_DREHUNG.get().trim());
    }

    /** Umhang-Textur zu einem Flaggenbild (wird einmal berechnet und gemerkt). */
    public static String texture(byte[] flagPng) {
        if (flagPng == null) return "";
        String src;
        synchronized (SOURCE_HASH) {
            src = SOURCE_HASH.computeIfAbsent(flagPng, FlagImages::hash);
        }
        boolean rechts = rechts();
        return BY_SOURCE.computeIfAbsent(src + (rechts ? "R" : "L"), k -> {
            try {
                byte[] png = FlagImages.png(FlagImages.cape(javax.imageio.ImageIO.read(new ByteArrayInputStream(flagPng)), rechts));
                String h = FlagImages.hash(png);
                TEXTURES.put(h, png);
                return h;
            } catch (Exception e) {
                CrownsCore.LOGGER.warn("Umhang konnte nicht erzeugt werden: {}", e.toString());
                return "";
            }
        });
    }

    public static byte[] texturePng(String hash) {
        return TEXTURES.get(hash);
    }

    /** Flaggenbild, das eine Nation gerade traegt: eigene Flagge, sonst Landesflagge, sonst null. */
    public static byte[] flagge(Nation n) {
        if (n == null) return null;
        byte[] eigene = FlagStore.eigene(n);
        if (eigene != null) return eigene;
        if (!CrownsConfig.UMHANG_LANDESFLAGGEN.get()) return null;
        byte[] land = FlagStore.land(n.land);
        if (land == null) {
            for (String c : n.laender) {
                land = FlagStore.land(c);
                if (land != null) break;
            }
        }
        return land;
    }

    /** Hash des Umhangs, den der Spieler gerade traegt, leer = keiner. */
    public static String hashFor(CrownsData d, UUID player) {
        if (!CrownsConfig.UMHANG_AKTIV.get() || FlagStore.aus(player)) return "";
        return texture(flagge(d.nationOf(player)));
    }

    /** Admin sieht eine wartende Flagge fuer eine Weile auf dem eigenen Ruecken. */
    public static void preview(ServerPlayer admin, byte[] flagPng, int seconds) {
        String h = texture(flagPng);
        if (!h.isEmpty()) PREVIEWS.put(admin.getUUID(), new Preview(h, System.currentTimeMillis() + seconds * 1000L));
    }

    /** Einmal pro Sekunde aus dem Scheduler. */
    public static void everySecond(MinecraftServer server) {
        if (TEXTURES.size() > 1024) {
            // zwischen zwei Durchlaeufen leeren, die Texturen entstehen bei Bedarf neu
            TEXTURES.clear();
            BY_SOURCE.clear();
            synchronized (SOURCE_HASH) { SOURCE_HASH.clear(); }
        }
        if (VIEWERS.isEmpty() && server.getPlayerList().getPlayers().stream().noneMatch(CcNetwork::present)) return;
        CrownsData d = CrownsData.get(server);
        Map<UUID, String> global = new HashMap<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            String h = hashFor(d, p.getUUID());
            if (!h.isEmpty()) global.put(p.getUUID(), h);
        }
        long now = System.currentTimeMillis();
        PREVIEWS.values().removeIf(pv -> pv.until() < now);
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            if (!CcNetwork.present(viewer)) continue;
            Viewer st = VIEWERS.computeIfAbsent(viewer.getUUID(), k -> new Viewer());
            Map<UUID, String> want = global;
            Preview pv = PREVIEWS.get(viewer.getUUID());
            if (pv != null) {
                want = new HashMap<>(global);
                want.put(viewer.getUUID(), pv.hash());
            }
            Map<UUID, String> delta = new LinkedHashMap<>();
            for (Map.Entry<UUID, String> e : want.entrySet()) {
                if (!e.getValue().equals(st.known.get(e.getKey()))) delta.put(e.getKey(), e.getValue());
            }
            for (UUID k : st.known.keySet()) {
                if (!want.containsKey(k)) delta.put(k, "");
            }
            if (st.init && delta.isEmpty()) continue;
            for (String h : (st.init ? delta : want).values()) {
                if (h.isEmpty() || st.sent.contains(h)) continue;
                byte[] png = TEXTURES.get(h);
                if (png == null) continue;
                CcNetwork.send(viewer, new CcNetwork.CapeTexture(h, png));
                st.sent.add(h);
            }
            CcNetwork.send(viewer, new CcNetwork.CapeMap(!st.init, st.init ? delta : new LinkedHashMap<>(want)));
            st.known = new HashMap<>(want);
            st.init = true;
        }
    }

    /** Wie viele Clients bekommen gerade Umhaenge (fuer Status und Selbsttest). */
    public static int viewers() {
        return VIEWERS.size();
    }

    public static class UmhangEvents {
        @SubscribeEvent
        public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
            VIEWERS.remove(e.getEntity().getUUID());
            PREVIEWS.remove(e.getEntity().getUUID());
        }
    }
}
