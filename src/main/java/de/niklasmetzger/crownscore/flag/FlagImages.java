package de.niklasmetzger.crownscore.flag;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Bilder fuer Flaggen und Umhaenge. Laeuft nur auf dem Server (java.awt), der Client bekommt fertige PNGs.
 *
 * Ein Umhang ist hochkant (10 x 16), Flaggen sind quer. Die Flagge wird deshalb um 90 Grad gedreht,
 * wie ein Banner am Mast: die Mastseite (links an der Flagge) kommt nach oben. Danach wird sie so
 * skaliert, dass sie die Umhangflaeche ganz fuellt, Ueberstand wird mittig abgeschnitten.
 * Dieselbe Rechnung steckt in der Vorschau der Upload-Seite (editor/flagge/flagge.js).
 */
public final class FlagImages {
    /** Aufloesung des Umhangs: 8 = 512 x 256 statt 64 x 32. */
    public static final int SCALE = 8;
    public static final int FACE_W = 10 * SCALE;
    public static final int FACE_H = 16 * SCALE;
    /** Groesste Datei, die angenommen wird. */
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    /** Laengste Seite einer gespeicherten Flagge. */
    public static final int STORE_SIDE = 1024;

    private FlagImages() {}

    /** PNG/JPG/GIF/BMP einlesen und pruefen. */
    public static BufferedImage decode(byte[] data) throws IOException {
        if (data == null || data.length == 0) throw new IOException("Leere Datei");
        if (data.length > MAX_BYTES) throw new IOException("Datei zu gross (hoechstens " + (MAX_BYTES >> 20) + " MB)");
        BufferedImage img;
        try (javax.imageio.stream.ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            java.util.Iterator<javax.imageio.ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) throw new IOException("Kein Bild, erlaubt sind PNG und JPG");
            javax.imageio.ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                // Abmessungen aus dem Kopf lesen, bevor Speicher fuer die Pixel belegt wird (Dekompressionsbombe)
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w > 8192 || h > 8192) throw new IOException("Bild zu gross (hoechstens 8192 Pixel pro Seite)");
                if ((long) w * h > 16_000_000L) throw new IOException("Bild zu gross (hoechstens 16 Millionen Pixel, z.B. 4000 x 4000)");
                img = reader.read(0);
            } finally {
                reader.dispose();
            }
        }
        if (img == null) throw new IOException("Kein Bild, erlaubt sind PNG und JPG");
        if (img.getWidth() < 8 || img.getHeight() < 8) throw new IOException("Bild zu klein (mindestens 8 x 8 Pixel)");
        if (img.getWidth() > 8192 || img.getHeight() > 8192) throw new IOException("Bild zu gross (hoechstens 8192 Pixel pro Seite)");
        return argb(img);
    }

    /** Auf hoechstens maxSide Pixel an der laengsten Seite verkleinern. */
    public static BufferedImage limit(BufferedImage img, int maxSide) {
        int w = img.getWidth(), h = img.getHeight();
        if (Math.max(w, h) <= maxSide) return img;
        double f = (double) maxSide / Math.max(w, h);
        return scale(img, Math.max(1, (int) Math.round(w * f)), Math.max(1, (int) Math.round(h * f)));
    }

    /**
     * Durchsichtige Stellen auf weissen Stoff legen. Ein Umhang wird massiv gezeichnet und auf der Karte wuerde sonst
     * das Gelaende durchscheinen; die Upload-Seite zeigt die Vorschau genauso auf Weiss.
     */
    public static BufferedImage aufTuch(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = src.getRGB(x, y);
                int a = c >>> 24;
                int r = ((c >> 16 & 255) * a + 255 * (255 - a)) / 255;
                int g = ((c >> 8 & 255) * a + 255 * (255 - a)) / 255;
                int b = ((c & 255) * a + 255 * (255 - a)) / 255;
                dst.setRGB(x, y, 0xFF000000 | r << 16 | g << 8 | b);
            }
        }
        return dst;
    }

    /** Flagge um 90 Grad drehen. rechts = im Uhrzeigersinn (Mast oben), sonst gegen den Uhrzeigersinn (Mast unten). */
    public static BufferedImage rotate(BufferedImage src, boolean rechts) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage dst = new BufferedImage(h, w, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < w; y++) {
            for (int x = 0; x < h; x++) {
                int rgb = rechts ? src.getRGB(y, h - 1 - x) : src.getRGB(w - 1 - y, x);
                dst.setRGB(x, y, rgb);
            }
        }
        return dst;
    }

    /** Die sichtbare Umhangflaeche (FACE_W x FACE_H): gedrehte Flagge, fuellend skaliert und mittig zugeschnitten. */
    public static BufferedImage face(BufferedImage flag, boolean rechts) {
        BufferedImage r = rotate(aufTuch(flag), rechts);
        double f = Math.max((double) FACE_W / r.getWidth(), (double) FACE_H / r.getHeight());
        int sw = Math.max(FACE_W, (int) Math.ceil(r.getWidth() * f));
        int sh = Math.max(FACE_H, (int) Math.ceil(r.getHeight() * f));
        BufferedImage s = scale(r, sw, sh);
        return s.getSubimage((sw - FACE_W) / 2, (sh - FACE_H) / 2, FACE_W, FACE_H);
    }

    /**
     * Fertige Umhang-Textur im Layout von Minecraft (64 x 32, hier SCALE-fach).
     * Aussenseite (1,1)-(11,17), Innenseite (12,1)-(22,17), Kanten oben/unten/seitlich je 1 Pixel.
     */
    public static BufferedImage cape(BufferedImage flag, boolean rechts) {
        int s = SCALE;
        BufferedImage face = face(flag, rechts);
        BufferedImage tex = new BufferedImage(64 * s, 32 * s, BufferedImage.TYPE_INT_ARGB);
        // Aussenseite: von hinten gesehen
        draw(tex, face, s, s, false, 1f);
        // Innenseite: dieselbe Flagge von der anderen Seite, etwas dunkler
        draw(tex, face, 12 * s, s, true, 0.7f);
        // Kanten aus den Randpixeln der Flaeche
        for (int x = 0; x < FACE_W; x++) {
            int top = shade(face.getRGB(x, 0), 0.8f), bottom = shade(face.getRGB(x, FACE_H - 1), 0.8f);
            for (int y = 0; y < s; y++) {
                tex.setRGB(s + x, y, top);                // oben
                tex.setRGB(11 * s + x, y, bottom);        // unten
            }
        }
        for (int y = 0; y < FACE_H; y++) {
            int left = shade(face.getRGB(FACE_W - 1, y), 0.8f), right = shade(face.getRGB(0, y), 0.8f);
            for (int x = 0; x < s; x++) {
                tex.setRGB(x, s + y, left);               // Seite
                tex.setRGB(11 * s + x, s + y, right);     // andere Seite
            }
        }
        return tex;
    }

    public static byte[] png(BufferedImage img) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String hash(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void draw(BufferedImage tex, BufferedImage face, int ox, int oy, boolean mirror, float light) {
        for (int y = 0; y < FACE_H; y++) {
            for (int x = 0; x < FACE_W; x++) {
                int rgb = face.getRGB(mirror ? FACE_W - 1 - x : x, y);
                tex.setRGB(ox + x, oy + y, light == 1f ? opaque(rgb) : shade(rgb, light));
            }
        }
    }

    /** Durchsichtige Stellen werden auf Weiss gelegt, der Umhang ist undurchsichtig. */
    private static int opaque(int argb) {
        int a = argb >>> 24;
        if (a == 255) return argb;
        int r = ((argb >> 16) & 0xff) * a / 255 + 255 - a;
        int g = ((argb >> 8) & 0xff) * a / 255 + 255 - a;
        int b = (argb & 0xff) * a / 255 + 255 - a;
        return 0xff000000 | r << 16 | g << 8 | b;
    }

    private static int shade(int argb, float f) {
        int c = opaque(argb);
        int r = (int) (((c >> 16) & 0xff) * f), g = (int) (((c >> 8) & 0xff) * f), b = (int) ((c & 0xff) * f);
        return 0xff000000 | r << 16 | g << 8 | b;
    }

    private static BufferedImage argb(BufferedImage img) {
        if (img.getType() == BufferedImage.TYPE_INT_ARGB) return img;
        BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return out;
    }

    /** Hochwertig skalieren: beim Verkleinern in Halbierungsschritten, damit nichts flimmert. */
    static BufferedImage scale(BufferedImage src, int w, int h) {
        BufferedImage cur = src;
        int cw = src.getWidth(), ch = src.getHeight();
        while (cw / 2 >= w && ch / 2 >= h) {
            cw /= 2;
            ch /= 2;
            cur = step(cur, cw, ch);
        }
        return step(cur, w, h);
    }

    private static BufferedImage step(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }
}
