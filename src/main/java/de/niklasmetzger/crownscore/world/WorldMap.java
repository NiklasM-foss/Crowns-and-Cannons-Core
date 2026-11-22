package de.niklasmetzger.crownscore.world;

import de.niklasmetzger.crownscore.CrownsCore;
import de.niklasmetzger.crownscore.config.CrownsConfig;
import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.Nation;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Laendergrenzen der Erdkarte. Die Server-Map ist eine Plattkarte: Laenge und Breite sind gleichmaessig
 * auf x und z verteilt, 0/0 liegt am Schnittpunkt von Aequator und Nullmeridian, Norden ist -z.
 * Die Grenzen stammen aus Natural Earth (gemeinfrei) und liegen geografisch in 3072 x 1536 Feldern vor.
 */
public final class WorldMap {
    public record Country(int index, String code, String name, int color, int fields) {}

    private static int width, height;
    private static byte[] cells;
    private static List<Country> countries;
    private static Country[] byIndex;
    /** Feldgrenzen je Land: minI, maxI, minJ, maxJ. */
    private static int[][] bounds;
    private static boolean failed;

    private WorldMap() {}

    public static synchronized boolean loaded() {
        if (cells == null && !failed) load();
        return cells != null;
    }

    private static void load() {
        try (InputStream in = WorldMap.class.getResourceAsStream("/crownscore/weltkarte/karte.bin.gz");
             InputStream tsv = WorldMap.class.getResourceAsStream("/crownscore/weltkarte/laender.tsv")) {
            if (in == null || tsv == null) throw new IllegalStateException("Weltkarte fehlt im JAR");
            DataInputStream data = new DataInputStream(new GZIPInputStream(in));
            int w = data.readInt();
            int h = data.readInt();
            byte[] c = new byte[w * h];
            data.readFully(c);
            List<Country> list = new ArrayList<>();
            Country[] idx = new Country[256];
            BufferedReader r = new BufferedReader(new InputStreamReader(tsv, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] f = line.split("\t");
                Country co = new Country(Integer.parseInt(f[0]), f[1], f[2], Integer.parseInt(f[3], 16), Integer.parseInt(f[4]));
                idx[co.index()] = co;
                if (co.fields() > 0) list.add(co);
            }
            int[][] b = new int[256][];
            for (int j = 0; j < h; j++) {
                for (int i = 0; i < w; i++) {
                    int v = c[j * w + i] & 0xFF;
                    if (v == 0) continue;
                    if (b[v] == null) b[v] = new int[]{i, i, j, j};
                    else {
                        if (i < b[v][0]) b[v][0] = i;
                        if (i > b[v][1]) b[v][1] = i;
                        if (j < b[v][2]) b[v][2] = j;
                        if (j > b[v][3]) b[v][3] = j;
                    }
                }
            }
            bounds = b;
            width = w;
            height = h;
            cells = c;
            byIndex = idx;
            countries = Collections.unmodifiableList(list);
            CrownsCore.LOGGER.info("Weltkarte geladen: {} Laender", list.size());
        } catch (Exception e) {
            failed = true;
            CrownsCore.LOGGER.error("Weltkarte konnte nicht geladen werden", e);
        }
    }

    public static List<Country> countries() {
        return loaded() ? countries : List.of();
    }

    public static Country byCode(String code) {
        for (Country c : countries()) if (c.code().equalsIgnoreCase(code)) return c;
        return null;
    }

    /** Land an Laenge/Breite in Grad, oder null fuer Meer. */
    public static Country at(double lon, double lat) {
        if (!loaded() || lon < -180 || lon >= 180 || lat <= -90 || lat > 90) return null;
        int i = (int) Math.floor((lon + 180.0) / 360.0 * width);
        int j = (int) Math.floor((90.0 - lat) / 180.0 * height);
        if (i < 0 || j < 0 || i >= width || j >= height) return null;
        return byIndex[cells[j * width + i] & 0xFF];
    }

    public static double blocksPerDegree() {
        return CrownsConfig.KARTE_BLOECKE_PRO_GRAD.get();
    }

    public static double lon(double blockX) {
        return (blockX - CrownsConfig.KARTE_MITTE_X.get()) / blocksPerDegree();
    }

    public static double lat(double blockZ) {
        return -(blockZ - CrownsConfig.KARTE_MITTE_Z.get()) / blocksPerDegree();
    }

    /** Land im Mittelpunkt eines Chunks. */
    public static Country atChunk(int cx, int cz) {
        return at(lon(cx * 16 + 8), lat(cz * 16 + 8));
    }

    public static boolean active() {
        return CrownsConfig.KARTE_AKTIV.get() && loaded();
    }

    /** Nation, die mit diesem Land verknuepft ist, oder null. */
    public static String nationFor(CrownsData data, Country c) {
        if (c == null) return null;
        for (Nation n : data.nations.values()) {
            for (String l : n.laender) if (c.code().equalsIgnoreCase(l)) return n.key();
        }
        return null;
    }

    /** Chunkbereich {minX, maxX, minZ, maxZ}, der alle genannten Laender umschliesst, oder null. */
    public static int[] chunkBounds(java.util.Collection<String> codes) {
        if (!loaded()) return null;
        double bpg = blocksPerDegree();
        int[] r = null;
        for (String code : codes) {
            Country c = byCode(code);
            if (c == null || bounds[c.index()] == null) continue;
            int[] b = bounds[c.index()];
            double lonMin = -180.0 + b[0] * 360.0 / width, lonMax = -180.0 + (b[1] + 1) * 360.0 / width;
            double latMax = 90.0 - b[2] * 180.0 / height, latMin = 90.0 - (b[3] + 1) * 180.0 / height;
            int x0 = (int) Math.floor((CrownsConfig.KARTE_MITTE_X.get() + lonMin * bpg) / 16.0) - 1;
            int x1 = (int) Math.floor((CrownsConfig.KARTE_MITTE_X.get() + lonMax * bpg) / 16.0) + 1;
            int z0 = (int) Math.floor((CrownsConfig.KARTE_MITTE_Z.get() - latMax * bpg) / 16.0) - 1;
            int z1 = (int) Math.floor((CrownsConfig.KARTE_MITTE_Z.get() - latMin * bpg) / 16.0) + 1;
            r = r == null ? new int[]{x0, x1, z0, z1} : new int[]{Math.min(r[0], x0), Math.max(r[1], x1), Math.min(r[2], z0), Math.max(r[3], z1)};
        }
        return r;
    }

    /** Laendernummer an Laenge/Breite (0 = Meer). Braucht keine Config, laeuft auch auf dem Client. */
    public static int indexAt(double lon, double lat) {
        if (!loaded() || lon < -180 || lon >= 180 || lat <= -90 || lat > 90) return 0;
        int i = (int) Math.floor((lon + 180.0) / 360.0 * width);
        int j = (int) Math.floor((90.0 - lat) / 180.0 * height);
        if (i < 0 || j < 0 || i >= width || j >= height) return 0;
        return cells[j * width + i] & 0xFF;
    }

    public static Country byIndex(int index) {
        return loaded() && index > 0 && index < 256 ? byIndex[index] : null;
    }

    /** Grenzen eines Landes in Grad {lonMin, lonMax, latMin, latMax}, oder null. */
    public static double[] degBounds(int index) {
        if (!loaded() || index <= 0 || index >= 256 || bounds[index] == null) return null;
        int[] b = bounds[index];
        return new double[]{-180.0 + b[0] * 360.0 / width, -180.0 + (b[1] + 1) * 360.0 / width,
                90.0 - (b[3] + 1) * 180.0 / height, 90.0 - b[2] * 180.0 / height};
    }

    /**
     * Liegt das Land hoechstens {@code abstand} Felder (ein Feld = ein Chunk bei Standardgroesse) von einem der
     * genannten Laender entfernt? 0 = muss direkt angrenzen. Laeuft nur im Umkreis des Ziellands.
     */
    public static boolean nahe(String code, java.util.Collection<String> codes, int abstand) {
        if (!loaded()) return false;
        Country ziel = byCode(code);
        if (ziel == null || bounds[ziel.index()] == null) return false;
        boolean[] eigen = new boolean[256];
        for (String c : codes) {
            Country x = byCode(c);
            if (x != null) eigen[x.index()] = true;
        }
        int r = Math.max(1, abstand + 1);
        int[] b = bounds[ziel.index()];
        int i0 = Math.max(0, b[0] - r), i1 = Math.min(width - 1, b[1] + r);
        int j0 = Math.max(0, b[2] - r), j1 = Math.min(height - 1, b[3] + r);
        int w = i1 - i0 + 1, h = j1 - j0 + 1;
        int[] dist = new int[w * h];
        java.util.Arrays.fill(dist, -1);
        int[] queue = new int[w * h];
        int head = 0, tail = 0;
        for (int j = j0; j <= j1; j++) {
            for (int i = i0; i <= i1; i++) {
                if (eigen[cells[j * width + i] & 0xFF]) {
                    int k = (j - j0) * w + (i - i0);
                    dist[k] = 0;
                    queue[tail++] = k;
                }
            }
        }
        int zi = ziel.index();
        while (head < tail) {
            int k = queue[head++];
            int i = k % w, j = k / w;
            if ((cells[(j + j0) * width + (i + i0)] & 0xFF) == zi) return true;
            if (dist[k] >= r) continue;
            int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : nb) {
                int ni = i + d[0], nj = j + d[1];
                if (ni < 0 || nj < 0 || ni >= w || nj >= h) continue;
                int nk = nj * w + ni;
                if (dist[nk] >= 0) continue;
                dist[nk] = dist[k] + 1;
                queue[tail++] = nk;
            }
        }
        return false;
    }

    /** Naechster Punkt (Laenge, Breite) in einem der Laender zu einem Ort, oder null. */
    public static double[] naechsterPunkt(java.util.Collection<String> codes, double lon, double lat) {
        if (!loaded()) return null;
        int pi = (int) Math.floor((lon + 180.0) / 360.0 * width), pj = (int) Math.floor((90.0 - lat) / 180.0 * height);
        long best = Long.MAX_VALUE;
        int bi = -1, bj = -1;
        for (String code : codes) {
            Country c = byCode(code);
            if (c == null || bounds[c.index()] == null) continue;
            int[] b = bounds[c.index()];
            for (int j = b[2]; j <= b[3]; j++) {
                for (int i = b[0]; i <= b[1]; i++) {
                    if ((cells[j * width + i] & 0xFF) != c.index()) continue;
                    long d = (long) (i - pi) * (i - pi) + (long) (j - pj) * (j - pj);
                    if (d < best) {
                        best = d;
                        bi = i;
                        bj = j;
                    }
                }
            }
        }
        if (bi < 0) return null;
        return new double[]{-180.0 + (bi + 0.5) * 360.0 / width, 90.0 - (bj + 0.5) * 180.0 / height};
    }

    /** Farbe einer Nation auf der Karte: die ihres Stammlands, sonst aus dem Namen abgeleitet. */
    public static int colorOf(Nation n) {
        Country c = n.land == null || n.land.isEmpty() ? null : byCode(n.land);
        if (c != null) return c.color();
        return 0x404040 | (n.key().hashCode() & 0xBFBFBF);
    }

    /** Blockbereich der Karte, abhaengig von der Kalibrierung. */
    public static int minChunkX() {
        return (int) Math.floor((CrownsConfig.KARTE_MITTE_X.get() - 180 * blocksPerDegree()) / 16.0);
    }

    public static int maxChunkX() {
        return (int) Math.floor((CrownsConfig.KARTE_MITTE_X.get() + 180 * blocksPerDegree() - 1) / 16.0);
    }

    public static int minChunkZ() {
        return (int) Math.floor((CrownsConfig.KARTE_MITTE_Z.get() - 90 * blocksPerDegree()) / 16.0);
    }

    public static int maxChunkZ() {
        return (int) Math.floor((CrownsConfig.KARTE_MITTE_Z.get() + 90 * blocksPerDegree() - 1) / 16.0);
    }
}
