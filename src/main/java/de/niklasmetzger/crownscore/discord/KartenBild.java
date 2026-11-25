package de.niklasmetzger.crownscore.discord;

import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.Nation;
import de.niklasmetzger.crownscore.data.Stadt;
import de.niklasmetzger.crownscore.world.WorldMap;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/** Weltkarte als PNG fuer /karte: Laender in der Farbe der Nation, freie Laender grau, Staedte als Punkte. */
final class KartenBild {
    private static final int BREITE = 1600;
    private static final double LAT_OBEN = 84, LAT_UNTEN = -58;
    private static final int MEER = 0x1d3557, FREI = 0x8d99ae, GRENZE = 0x1b1b1b, STADT = 0xffffff;

    /** Was das Bild braucht, auf dem Server-Thread eingesammelt. */
    record Stand(int[] farben, List<double[]> staedte) {}

    private KartenBild() {}

    static Stand stand(CrownsData d) {
        int[] farben = new int[256];
        java.util.Arrays.fill(farben, FREI);
        for (WorldMap.Country c : WorldMap.countries()) {
            String key = WorldMap.nationFor(d, c);
            Nation n = key == null ? null : d.nations.get(key);
            if (n != null && n.gespielt()) farben[c.index()] = WorldMap.colorOf(n);
        }
        List<double[]> staedte = new ArrayList<>();
        for (Stadt s : d.staedte) {
            if (s.bestaetigt) staedte.add(new double[]{WorldMap.lon(s.pos.getX()), WorldMap.lat(s.pos.getZ())});
        }
        return new Stand(farben, staedte);
    }

    static byte[] png(Stand st) throws java.io.IOException {
        int hoehe = (int) Math.round(BREITE * (LAT_OBEN - LAT_UNTEN) / 360.0);
        int[] idx = new int[BREITE * hoehe];
        for (int y = 0; y < hoehe; y++) {
            double lat = LAT_OBEN - (y + 0.5) * (LAT_OBEN - LAT_UNTEN) / hoehe;
            for (int x = 0; x < BREITE; x++) {
                idx[y * BREITE + x] = WorldMap.indexAt(-180.0 + (x + 0.5) * 360.0 / BREITE, lat);
            }
        }
        BufferedImage img = new BufferedImage(BREITE, hoehe, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < hoehe; y++) {
            for (int x = 0; x < BREITE; x++) {
                int i = idx[y * BREITE + x];
                int farbe;
                if (i == 0) farbe = MEER;
                else if ((x + 1 < BREITE && idx[y * BREITE + x + 1] != i) || (y + 1 < hoehe && idx[(y + 1) * BREITE + x] != i)) farbe = GRENZE;
                else farbe = st.farben()[i];
                img.setRGB(x, y, farbe);
            }
        }
        for (double[] s : st.staedte()) {
            int cx = (int) Math.round((s[0] + 180.0) / 360.0 * BREITE);
            int cy = (int) Math.round((LAT_OBEN - s[1]) / (LAT_OBEN - LAT_UNTEN) * hoehe);
            for (int dy = -3; dy <= 3; dy++) {
                for (int dx = -3; dx <= 3; dx++) {
                    int px = cx + dx, py = cy + dy;
                    if (px < 0 || py < 0 || px >= BREITE || py >= hoehe) continue;
                    int r2 = dx * dx + dy * dy;
                    if (r2 <= 9) img.setRGB(px, py, r2 >= 6 ? GRENZE : STADT);
                }
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
