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
    /** Wappen bleiben freigestellt (eigener Zwischenspeicher, ohne Stoff-Hintergrund). */
    private static final Map<byte[], String> BY_SOURCE_FREI = new IdentityHashMap<>();
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
            BY_SOURCE_FREI.clear();
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

    /** Verkleinertes Kartenbild zu einem Flaggenbild (durchsichtige Stellen auf weissem Stoff), Hash oder leer. */
    static String texture(byte[] flagPng) {
        return texture(flagPng, true);
    }

    /** Verkleinertes Kartenbild. tuch = durchsichtige Stellen weiss fuellen (Flaggen), sonst freigestellt lassen (Wappen). */
    public static String texture(byte[] flagPng, boolean tuch) {
        if (flagPng == null) return "";
        int w = CrownsConfig.KARTENFLAGGEN_BREITE.get();
        synchronized (BY_SOURCE) {
            if (w != breite) {
                BY_SOURCE.clear();
                BY_SOURCE_FREI.clear();
                breite = w;
            }
            Map<byte[], String> cache = tuch ? BY_SOURCE : BY_SOURCE_FREI;
            String h = cache.get(flagPng);
            if (h != null) return h;
            try {
                BufferedImage img = javax.imageio.ImageIO.read(new ByteArrayInputStream(flagPng));
                if (img == null) return "";
                if (tuch) img = FlagImages.aufTuch(img);
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
                cache.put(flagPng, h);
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

    /** Staedte fuer die Karten, an jeden Client nur bei Aenderung. Jeder sieht nur die Staedte seiner Nation und die, in denen er schon war. */
    private static void staedte(MinecraftServer server) {
        CrownsData d = CrownsData.get(server);
        String ow = net.minecraft.world.level.Level.OVERWORLD.location().toString();
        // Entdecken: wer im Gebiet einer anerkannten Stadt steht, sieht sie ab jetzt
        if (tick % 5 == 0) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p.level().dimension() != net.minecraft.world.level.Level.OVERWORLD) continue;
                de.niklasmetzger.crownscore.data.Stadt s = de.niklasmetzger.crownscore.handel.StadtService.an(d, ow, p.blockPosition().getX() >> 4, p.blockPosition().getZ() >> 4);
                if (s != null && s.entdeckt.add(p.getUUID())) {
                    d.setDirty();
                    if (!s.nation.equals(d.nationKeyOf(p.getUUID()))) {
                        de.niklasmetzger.crownscore.util.Msg.player(p, "\u00a77Du hast die Stadt \u00a7f" + s.name + "\u00a77 entdeckt, sie steht jetzt auf deiner Karte.");
                    }
                }
            }
        }
        int gebiet = CrownsConfig.STADT_GEBIET.get();
        java.util.List<CcNetwork.Belagerung> bl = new java.util.ArrayList<>();
        for (de.niklasmetzger.crownscore.data.Besatzung b : d.besatzungen) {
            if (!b.dim.equals(ow)) continue;
            BlockPosHolder z = ziel(d, b);
            bl.add(new CcNetwork.Belagerung(b.stadt, b.vonX, b.vonZ, z.x, z.z));
        }
        java.util.Map<String, String> bilder = new java.util.HashMap<>();
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            if (!CcNetwork.present(viewer)) continue;
            Viewer st = VIEWERS.computeIfAbsent(viewer.getUUID(), k -> new Viewer());
            String eigene = d.nationKeyOf(viewer.getUUID());
            boolean admin = de.niklasmetzger.crownscore.util.Perms.isAdmin(viewer);
            java.util.List<CcNetwork.StadtEintrag> l = new java.util.ArrayList<>();
            for (de.niklasmetzger.crownscore.data.Stadt s : d.staedte) {
                if (!s.dim.equals(ow)) continue;
                boolean meine = s.nation.equals(eigene);
                boolean sichtbar = meine || admin || (s.bestaetigt && s.entdeckt.contains(viewer.getUUID()));
                if (!sichtbar) {
                    for (CcNetwork.Belagerung b : bl) if (b.stadt().equalsIgnoreCase(s.name) && eigene != null) sichtbar = true;
                }
                if (!sichtbar) continue;
                String bild = bilder.computeIfAbsent(s.name, k -> {
                    byte[] w = de.niklasmetzger.crownscore.handel.WappenStore.wappen(s);
                    if (w != null) return texture(w, false);
                    Nation n = d.nations.get(s.nation);
                    return texture(n == null ? null : flagge(n), true);
                });
                if (!bild.isEmpty() && !st.sent.contains(bild) && TEXTURES.containsKey(bild)) {
                    CcNetwork.send(viewer, new CcNetwork.MapFlagTexture(bild, TEXTURES.get(bild)));
                    st.sent.add(bild);
                }
                Nation n = d.nations.get(s.nation);
                l.add(new CcNetwork.StadtEintrag(s.name, n == null ? s.nation : n.name, s.pos.getX(), s.pos.getZ(), s.bestaetigt, bild));
            }
            java.util.List<CcNetwork.Belagerung> sichtbareBl = new java.util.ArrayList<>();
            for (de.niklasmetzger.crownscore.data.Besatzung b : d.besatzungen) {
                if (!b.dim.equals(ow)) continue;
                if (admin || b.angreifer.equals(eigene) || b.verteidiger.equals(eigene) || l.stream().anyMatch(e -> e.name().equalsIgnoreCase(b.stadt))) {
                    BlockPosHolder z = ziel(d, b);
                    sichtbareBl.add(new CcNetwork.Belagerung(b.stadt, b.vonX, b.vonZ, z.x, z.z));
                }
            }
            String sig = gebiet + "|" + l + "|" + sichtbareBl;
            if (sig.equals(st.staedte)) continue;
            CcNetwork.send(viewer, new CcNetwork.StadtKarte(gebiet, l, sichtbareBl));
            st.staedte = sig;
        }
    }

    private record BlockPosHolder(int x, int z) {}

    private static BlockPosHolder ziel(CrownsData d, de.niklasmetzger.crownscore.data.Besatzung b) {
        if (!b.stadt.isEmpty()) {
            for (de.niklasmetzger.crownscore.data.Stadt s : d.staedte) if (s.name.equalsIgnoreCase(b.stadt)) return new BlockPosHolder(s.pos.getX(), s.pos.getZ());
        }
        return new BlockPosHolder(b.pos.getX(), b.pos.getZ());
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
