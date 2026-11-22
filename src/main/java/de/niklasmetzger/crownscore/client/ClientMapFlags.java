package de.niklasmetzger.crownscore.client;

import de.niklasmetzger.crownscore.CrownsCore;
import de.niklasmetzger.crownscore.net.CcNetwork;
import de.niklasmetzger.crownscore.world.WorldMap;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Flaggen fuer Xaero's Karten auf dem Client. Der Server schickt, welches Staatskonto (Besitzer der Claims in
 * OPAC) welche Flagge hat, und die Kalibrierung der Weltkarte. Hier wird pro Block ausgerechnet, welcher Pixel
 * der Flagge dort liegt: die Flagge wird ueber das Land gespannt, in dem der Chunk liegt.
 * Die Karten fragen aus ihren eigenen Threads, deshalb ist alles threadsicher.
 */
public final class ClientMapFlags {
    private record Bild(int w, int h, int[] argb) {}

    private static final Map<String, byte[]> PNGS = new ConcurrentHashMap<>();
    private static final Map<UUID, String> OWNERS = new ConcurrentHashMap<>();
    /** Dekodierte Bilder, hoechstens CACHE Stueck (die Karte braucht selten mehr gleichzeitig). */
    private static final int CACHE = 96;
    private static final Map<String, Bild> DECODED = java.util.Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Bild> e) {
            return size() > CACHE;
        }
    });

    private static volatile boolean aktiv;
    private static volatile int mitteX, mitteZ, alpha = 140;
    private static volatile double bpg = 49152.0 / 360.0;
    /** Steigt bei jeder Aenderung, damit die Karten neu zeichnen. */
    private static volatile int version;

    private ClientMapFlags() {}

    public static void texture(String hash, byte[] png) {
        if (!hash.matches("[0-9a-f]{8,64}") || png.length > (1 << 21)) return;
        PNGS.put(hash, png);
        changed();
    }

    public static void map(CcNetwork.MapFlags msg) {
        if (msg.full()) OWNERS.clear();
        msg.flags().forEach((id, h) -> {
            if (h == null || h.isEmpty()) OWNERS.remove(id);
            else OWNERS.put(id, h);
        });
        aktiv = msg.aktiv();
        mitteX = msg.mitteX();
        mitteZ = msg.mitteZ();
        bpg = msg.bloeckeProGrad() > 0 ? msg.bloeckeProGrad() : bpg;
        alpha = Math.max(0, Math.min(255, Math.round(msg.deckkraft() * 255 / 100f)));
        changed();
    }

    private static void changed() {
        version++;
        XaeroBridge.refresh();
    }

    public static int version() {
        return version;
    }

    public static void clear() {
        staedte = java.util.List.of();
        verbunden = false;
        PNGS.clear();
        OWNERS.clear();
        DECODED.clear();
        aktiv = false;
        changed();
    }

    /** Malen wir fuer dieses Staatskonto eine Flagge (dann blendet die normale OPAC-Flaeche aus)? */
    public static boolean hat(UUID owner) {
        if (!aktiv || owner == null) return false;
        String h = OWNERS.get(owner);
        return h != null && PNGS.containsKey(h);
    }

    /** Hash der Flagge eines Besitzers oder 0 (auch solange das Bild fehlt), fuer die Neuzeichnen-Pruefung der World Map. */
    public static int hashOf(UUID owner) {
        if (!aktiv || owner == null) return 0;
        String h = OWNERS.get(owner);
        return h == null || !PNGS.containsKey(h) ? 0 : h.hashCode();
    }

    /** Aendert sich, wenn Deckkraft, Kalibrierung, An/Aus oder die Staedte wechseln. */
    public static int settingsHash() {
        return java.util.Objects.hash(aktiv, mitteX, mitteZ, bpg, alpha, staedteHash);
    }

    // ---- Staedte --------------------------------------------------------

    private static volatile java.util.List<CcNetwork.StadtEintrag> staedte = java.util.List.of();
    private static volatile java.util.List<CcNetwork.Belagerung> belagerungen = java.util.List.of();
    private static volatile int gebiet = 4, staedteHash;
    private static volatile boolean verbunden;

    public static void staedte(CcNetwork.StadtKarte msg) {
        staedte = java.util.List.copyOf(msg.staedte());
        belagerungen = java.util.List.copyOf(msg.belagerungen());
        gebiet = msg.gebiet();
        staedteHash = (msg.staedte().hashCode() * 31 + msg.belagerungen().hashCode()) * 31 + gebiet;
        verbunden = true;
        changed();
    }

    /** Gibt es ueberhaupt etwas zu malen (Flaggen oder Staedte)? */
    public static boolean etwasZuMalen() {
        return aktiv() || !staedte.isEmpty();
    }

    /** Hat der Server Crowns Core 1.5.0 (dann gibt es den Stadtantrag auf der Karte)? */
    public static boolean serverHatStaedte() {
        return verbunden;
    }

    /** Abstand eines Punkts zur Strecke, im Quadrat. */
    private static double abstand2(double px, double pz, double ax, double az, double bx, double bz) {
        double dx = bx - ax, dz = bz - az, l = dx * dx + dz * dz;
        double t = l == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (pz - az) * dz) / l));
        double qx = ax + t * dx - px, qz = az + t * dz - pz;
        return qx * qx + qz * qz;
    }

    private static final double LINIE = 2.0;

    private static boolean linieImChunk(CcNetwork.Belagerung b, int cx, int cz) {
        return abstand2(cx * 16 + 8, cz * 16 + 8, b.vonX(), b.vonZ(), b.zielX(), b.zielZ()) <= (12 + LINIE) * (12 + LINIE);
    }

    private static boolean belagert(String stadt) {
        for (CcNetwork.Belagerung b : belagerungen) if (b.stadt().equalsIgnoreCase(stadt)) return true;
        return false;
    }

    /** Halbe Kantenlaenge des Wappens auf der Karte in Bloecken. */
    private static final int WAPPEN = 24;

    /** Staedte fuer die Wegpunkte. */
    public static java.util.List<CcNetwork.StadtEintrag> staedteListe() {
        return staedte;
    }

    public static int staedteStand() {
        return staedteHash;
    }

    /** Liegt im Chunk ein Rand, das Wappen einer Stadt oder eine Angriffslinie? */
    public static boolean stadtImChunk(int cx, int cz) {
        for (CcNetwork.Belagerung b : belagerungen) if (linieImChunk(b, cx, cz)) return true;
        for (CcNetwork.StadtEintrag s : staedte) {
            if (Math.abs(cx * 16 + 8 - s.x()) <= WAPPEN + 8 && Math.abs(cz * 16 + 8 - s.z()) <= WAPPEN + 8) return true;
        }
        int r = gebiet;
        for (CcNetwork.StadtEintrag s : staedte) {
            int sx = s.x() >> 4, sz = s.z() >> 4;
            if (Math.abs(cx - sx) > r || Math.abs(cz - sz) > r) continue;
            if (Math.abs(cx - sx) == r || Math.abs(cz - sz) == r || (cx == sx && cz == sz)) return true;
        }
        return false;
    }

    /** Stadt, in deren Gebiet der Block liegt, oder null. */
    public static CcNetwork.StadtEintrag stadtAn(int bx, int bz) {
        int cx = bx >> 4, cz = bz >> 4, r = gebiet;
        for (CcNetwork.StadtEintrag s : staedte) {
            if (Math.abs(cx - (s.x() >> 4)) <= r && Math.abs(cz - (s.z() >> 4)) <= r) return s;
        }
        return null;
    }

    /**
     * Malt Stadtrahmen (2 Bloecke breit, gold fuer anerkannte, grau gestrichelt fuer wartende Antraege) und die
     * Stadtmitte in {@code out}. true = es wurde etwas gemalt.
     */
    public static boolean staedteMalen(int cx, int cz, int[] out) {
        boolean any = false;
        int rot = (0xE0 << 24) | (0x20 << 16) | (0x20 << 8) | 0xE6;
        for (CcNetwork.Belagerung b : belagerungen) {
            if (!linieImChunk(b, cx, cz)) continue;
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (abstand2(cx * 16 + x + 0.5, cz * 16 + z + 0.5, b.vonX(), b.vonZ(), b.zielX(), b.zielZ()) <= LINIE * LINIE) {
                        out[(z << 4) | x] = rot;
                        any = true;
                    }
                }
            }
        }
        int r = gebiet;
        for (CcNetwork.StadtEintrag s : staedte) {
            int sx = s.x() >> 4, sz = s.z() >> 4;
            int dx = cx - sx, dz = cz - sz;
            if (Math.abs(dx) > r || Math.abs(dz) > r) continue;
            int farbe = belagert(s.name()) ? (0xE0 << 24) | (0x20 << 16) | (0x20 << 8) | 0xF0
                    : s.anerkannt() ? (0xF2 << 24) | (0xC2 << 16) | (0x30 << 8) | 0xF0 : (0xD0 << 24) | (0xD0 << 16) | (0xD0 << 8) | 0xC8;
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    boolean rand = (dx == -r && x < 2) || (dx == r && x > 13) || (dz == -r && z < 2) || (dz == r && z > 13);
                    if (rand && !s.anerkannt() && (((cx * 16 + x) + (cz * 16 + z)) / 4) % 2 == 0) rand = false;
                    if (rand) {
                        out[(z << 4) | x] = farbe;
                        any = true;
                    }
                }
            }
            Bild w = s.bild().isEmpty() ? null : bild(s.bild());
            if (w != null) {
                // Wappen mittig, Seitenverhaeltnis behalten, dunkler Rand
                double sc = Math.min(2.0 * WAPPEN / w.w(), 2.0 * WAPPEN / w.h());
                double bw = w.w() * sc, bh = w.h() * sc;
                double x0 = s.x() - bw / 2, z0 = s.z() - bh / 2;
                int dunkel = (0x18 << 24) | (0x12 << 16) | (0x0C << 8) | 0xF0;
                for (int z = 0; z < 16; z++) {
                    double wz = cz * 16 + z + 0.5;
                    for (int x = 0; x < 16; x++) {
                        double wx = cx * 16 + x + 0.5;
                        if (wx < x0 - 1 || wx > x0 + bw + 1 || wz < z0 - 1 || wz > z0 + bh + 1) continue;
                        if (wx < x0 || wx > x0 + bw || wz < z0 || wz > z0 + bh) {
                            out[(z << 4) | x] = dunkel;
                        } else {
                            int px = Math.min(w.w() - 1, (int) ((wx - x0) / sc)), pz = Math.min(w.h() - 1, (int) ((wz - z0) / sc));
                            int c = w.argb()[pz * w.w() + px];
                            if ((c >>> 24) < 40) continue;
                            out[(z << 4) | x] = ((c >> 16 & 255) << 24) | ((c >> 8 & 255) << 16) | ((c & 255) << 8) | 0xF8;
                        }
                        any = true;
                    }
                }
                continue;
            }
            if (dx == 0 && dz == 0) {
                int lx = s.x() & 15, lz = s.z() & 15;
                for (int z = Math.max(0, lz - 2); z <= Math.min(15, lz + 2); z++) {
                    for (int x = Math.max(0, lx - 2); x <= Math.min(15, lx + 2); x++) {
                        boolean kern = Math.abs(x - lx) <= 1 && Math.abs(z - lz) <= 1;
                        out[(z << 4) | x] = kern ? farbe : (0x20 << 24) | (0x18 << 16) | (0x10 << 8) | 0xF0;
                    }
                }
                any = true;
            }
        }
        return any;
    }

    public static boolean aktiv() {
        return aktiv && !OWNERS.isEmpty();
    }

    private static Bild bild(String hash) {
        Bild b = DECODED.get(hash);
        if (b != null) return b;
        byte[] png = PNGS.get(hash);
        if (png == null) return null;
        try {
            BufferedImage img = javax.imageio.ImageIO.read(new ByteArrayInputStream(png));
            if (img == null) return null;
            int w = img.getWidth(), h = img.getHeight();
            b = new Bild(w, h, img.getRGB(0, 0, w, h, null, 0, w));
            DECODED.put(hash, b);
            return b;
        } catch (Exception e) {
            CrownsCore.LOGGER.warn("Kartenflagge {} nicht lesbar: {}", hash, e.toString());
            PNGS.remove(hash);
            return null;
        }
    }

    /**
     * Fuellt {@code out} (256 Werte, Index z*16+x) mit den Farben eines Chunks im Format von Xaero (RGBA, Alpha im
     * untersten Byte). {@code randN/O/S/W} = Nachbarchunk gehoert jemand anderem, dort kommt eine dunkle Linie hin.
     * false = keine Flagge fuer diesen Besitzer.
     */
    public static boolean fill(UUID owner, int cx, int cz, boolean randN, boolean randO, boolean randS, boolean randW, int[] out) {
        if (!aktiv || owner == null) return false;
        String hash = OWNERS.get(owner);
        if (hash == null) return false;
        Bild b = bild(hash);
        if (b == null) return false;
        double bpg = ClientMapFlags.bpg;
        int mx = mitteX, mz = mitteZ, a = alpha;
        // Land in der Chunkmitte bestimmt, ueber welche Flaeche die Flagge gespannt wird
        double lon = (cx * 16 + 8 - mx) / bpg, lat = -(cz * 16 + 8 - mz) / bpg;
        double[] g = WorldMap.degBounds(WorldMap.indexAt(lon, lat));
        double x0, z0, bw, bh;
        if (g != null) {
            x0 = mx + g[0] * bpg;
            bw = (g[1] - g[0]) * bpg;
            z0 = mz - g[3] * bpg;
            bh = (g[3] - g[2]) * bpg;
        } else {
            // Kein Land (Meer, einzeln zugewiesene Chunks): Flagge gekachelt, 64 Chunks breit
            bw = 1024;
            bh = 1024.0 * b.h() / b.w();
            x0 = Math.floor((cx * 16.0) / bw) * bw;
            z0 = Math.floor((cz * 16.0) / bh) * bh;
        }
        // Wie "cover": Flagge so skalieren, dass sie die Flaeche fuellt, mittig zuschneiden
        double s = Math.max(bw / b.w(), bh / b.h());
        double ox = x0 + (bw - b.w() * s) / 2, oz = z0 + (bh - b.h() * s) / 2;
        int rand = (20 << 24) | (20 << 16) | (20 << 8) | Math.min(255, a + 90);
        for (int z = 0; z < 16; z++) {
            double wz = cz * 16 + z + 0.5;
            int py = (int) Math.floor((wz - oz) / s);
            py = Math.max(0, Math.min(b.h() - 1, py));
            for (int x = 0; x < 16; x++) {
                int idx = (z << 4) | x;
                if ((randN && z == 0) || (randS && z == 15) || (randW && x == 0) || (randO && x == 15)) {
                    out[idx] = rand;
                    continue;
                }
                double wx = cx * 16 + x + 0.5;
                int px = (int) Math.floor((wx - ox) / s);
                px = Math.max(0, Math.min(b.w() - 1, px));
                int c = b.argb()[py * b.w() + px];
                int pa = (c >>> 24) * a / 255;
                out[idx] = ((c >> 16 & 255) << 24) | ((c >> 8 & 255) << 16) | ((c & 255) << 8) | pa;
            }
        }
        return true;
    }
}
