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
            if (c.code().equalsIgnoreCase(n.land)) return n.key();
        }
        return null;
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
