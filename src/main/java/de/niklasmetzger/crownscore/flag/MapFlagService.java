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

import java.awt.image.BufferedImage;
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
 * Flaggen auf Xaero's Karten: Jede Nation mit Gebiet bekommt ihr Flaggenbild an das Staatskonto gehaengt, dem in
 * Open Parties and Claims ihre Chunks gehoeren. Der Client malt damit die Flagge ueber jedes Land.
 * Verteilt wird wie bei den Umhaengen: jedes Bild einmal pro Spieler, danach nur noch Aenderungen.
 */
public final class MapFlagService {
    /** Hoechstens so viele Bilder pro Sekunde und Spieler, damit das Einloggen nicht ruckelt. */
    private static final int BILDER_PRO_SEKUNDE = 24;

    private static final Map<String, byte[]> TEXTURES = new ConcurrentHashMap<>();
    private static final Map<byte[], String> BY_SOURCE = new IdentityHashMap<>();
    private static int breite = -1;

    private static final class Viewer {
        boolean init;
        final Set<String> sent = new HashSet<>();
        Map<UUID, String> known = new HashMap<>();
        String settings = "";
        /** Es fehlen noch Bilder (Drosselung). */
        boolean offen;
        /** Zuletzt geschickte Staedteliste. */
        String staedte = "";
    }

    private static final Map<UUID, Viewer> VIEWERS = new ConcurrentHashMap<>();

    private MapFlagService() {}

    public static void reset() {
        VIEWERS.clear();
        TEXTURES.clear();
        synchronized (BY_SOURCE) {
            BY_SOURCE.clear();
        }
    }

    /** Flaggenbild einer Nation fuer die Karte: eigene Flagge, sonst die Landesflagge. */
    public static byte[] flagge(Nation n) {
        byte[] eigene = FlagStore.eigene(n);
        if (eigene != null) return eigene;
        byte[] land = FlagStore.land(n.land);
        if (land == null) {
            for (String c : n.laender) {
                land = FlagStore.land(c);
                if (land != null) break;
            }
        }
        return land;
    }

    /** Verkleinertes Kartenbild zu einem Flaggenbild, Hash oder leer. */
    static String texture(byte[] flagPng) {
        if (flagPng == null) return "";
        int w = CrownsConfig.KARTENFLAGGEN_BREITE.get();
        synchronized (BY_SOURCE) {
            if (w != breite) {
                BY_SOURCE.clear();
                breite = w;
            }
            String h = BY_SOURCE.get(flagPng);
            if (h != null) return h;
            try {
                BufferedImage img = javax.imageio.ImageIO.read(new ByteArrayInputStream(flagPng));
                if (img == null) return "";
                int nw = Math.min(w, img.getWidth());
                int nh = Math.max(1, Math.round(img.getHeight() * (nw / (float) img.getWidth())));
                if (nh > w) {
                    nh = w;
                    nw = Math.max(1, Math.round(img.getWidth() * (nh / (float) img.getHeight())));
                }
                BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g = out.createGraphics();
                g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
                g.drawImage(img, 0, 0, nw, nh, null);
                g.dispose();
                byte[] png = FlagImages.png(out);
                h = FlagImages.hash(png);
                TEXTURES.put(h, png);
                BY_SOURCE.put(flagPng, h);
                return h;
            } catch (Exception e) {
                CrownsCore.LOGGER.warn("Kartenflagge konnte nicht erzeugt werden: {}", e.toString());
                return "";
            }
        }
    }

    /** Staatskonto -> Hash des Kartenbilds fuer alle Nationen mit Land. */
    public static Map<UUID, String> current(CrownsData d) {
        Map<UUID, String> m = new LinkedHashMap<>();
        if (!CrownsConfig.KARTENFLAGGEN_AKTIV.get()) return m;
        for (Nation n : d.nations.values()) {
            if (n.laender.isEmpty()) continue;
            String h = texture(flagge(n));
            if (!h.isEmpty()) m.put(n.staat(), h);
        }
        return m;
    }

    private static String settings() {
        return CrownsConfig.KARTENFLAGGEN_AKTIV.get() + "|" + CrownsConfig.KARTE_MITTE_X.get() + "|" + CrownsConfig.KARTE_MITTE_Z.get()
                + "|" + CrownsConfig.KARTE_BLOECKE_PRO_GRAD.get() + "|" + CrownsConfig.KARTENFLAGGEN_DECKKRAFT.get();
    }

    private static int tick;

    /** Einmal pro Sekunde aus dem Scheduler. Die Liste wird alle 5 Sekunden neu berechnet, Bilder gehen jede Sekunde raus. */
    public static void everySecond(MinecraftServer server) {
        if (server.getPlayerList().getPlayers().stream().noneMatch(CcNetwork::present)) return;
        tick++;
        staedte(server);
        boolean neu = tick % 5 == 0 || VIEWERS.values().stream().anyMatch(v -> !v.init);
        CrownsData d = CrownsData.get(server);
        Map<UUID, String> want = null;
        String settings = settings();
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            if (!CcNetwork.present(viewer)) continue;
            Viewer st = VIEWERS.computeIfAbsent(viewer.getUUID(), k -> new Viewer());
            if (st.init && !neu && !st.offen) continue;
            if (want == null) want = current(d);
            // Erst die Bilder, gedrosselt; die Liste nennt nur Flaggen, deren Bild schon unterwegs ist
            int budget = BILDER_PRO_SEKUNDE;
            Map<UUID, String> sendbar = new LinkedHashMap<>();
            for (Map.Entry<UUID, String> e : want.entrySet()) {
                String h = e.getValue();
                if (!st.sent.contains(h)) {
                    if (budget <= 0) continue;
                    byte[] png = TEXTURES.get(h);
                    if (png == null) continue;
                    CcNetwork.send(viewer, new CcNetwork.MapFlagTexture(h, png));
                    st.sent.add(h);
                    budget--;
                }
                sendbar.put(e.getKey(), h);
            }
            st.offen = sendbar.size() < want.size();
            boolean full = !st.init || !settings.equals(st.settings);
            Map<UUID, String> delta = new LinkedHashMap<>();
            if (full) delta.putAll(sendbar);
            else {
                for (Map.Entry<UUID, String> e : sendbar.entrySet()) {
                    if (!e.getValue().equals(st.known.get(e.getKey()))) delta.put(e.getKey(), e.getValue());
                }
                for (UUID k : st.known.keySet()) {
                    if (!want.containsKey(k)) delta.put(k, "");
                }
            }
            if (!full && delta.isEmpty()) continue;
            CcNetwork.send(viewer, new CcNetwork.MapFlags(full, CrownsConfig.KARTENFLAGGEN_AKTIV.get(), CrownsConfig.KARTE_MITTE_X.get(),
                    CrownsConfig.KARTE_MITTE_Z.get(), CrownsConfig.KARTE_BLOECKE_PRO_GRAD.get(), CrownsConfig.KARTENFLAGGEN_DECKKRAFT.get(), delta));
            Map<UUID, String> known = full ? new HashMap<>() : new HashMap<>(st.known);
            delta.forEach((k, v) -> {
                if (v.isEmpty()) known.remove(k);
                else known.put(k, v);
            });
            st.known = known;
            st.settings = settings;
            st.init = true;
        }
    }

    /** Staedte fuer die Karten, an jeden Client nur bei Aenderung. */
    private static void staedte(MinecraftServer server) {
        CrownsData d = CrownsData.get(server);
        java.util.List<CcNetwork.StadtEintrag> l = new java.util.ArrayList<>();
        String ow = net.minecraft.world.level.Level.OVERWORLD.location().toString();
        for (de.niklasmetzger.crownscore.data.Stadt s : d.staedte) {
            if (!s.dim.equals(ow)) continue;
            Nation n = d.nations.get(s.nation);
            l.add(new CcNetwork.StadtEintrag(s.name, n == null ? s.nation : n.name, s.pos.getX(), s.pos.getZ(), s.bestaetigt));
        }
        int gebiet = CrownsConfig.STADT_GEBIET.get();
        String sig = gebiet + "|" + l;
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            if (!CcNetwork.present(viewer)) continue;
            Viewer st = VIEWERS.computeIfAbsent(viewer.getUUID(), k -> new Viewer());
            if (sig.equals(st.staedte)) continue;
            CcNetwork.send(viewer, new CcNetwork.StadtKarte(gebiet, l));
            st.staedte = sig;
        }
    }

    /** Wie viele Clients bekommen gerade Kartenflaggen (fuer Status und Selbsttest). */
    public static int viewers() {
        return VIEWERS.size();
    }

    public static class Events {
        @SubscribeEvent
        public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
            VIEWERS.remove(e.getEntity().getUUID());
        }
    }
}
