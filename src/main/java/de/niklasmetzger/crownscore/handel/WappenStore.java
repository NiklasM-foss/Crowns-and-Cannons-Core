package de.niklasmetzger.crownscore.handel;

import de.niklasmetzger.crownscore.CrownsCore;
import de.niklasmetzger.crownscore.data.Stadt;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wappen der Staedte: world/crownscore/wappen/&lt;stadt&gt;.png (freigegeben) und wartend/&lt;stadt&gt;.png.
 * Ohne Wappen zeigt die Karte die Flagge der Nation, der die Stadt gehoert.
 */
public final class WappenStore {
    private static Path dir;
    private static final Map<String, byte[]> FREI = new ConcurrentHashMap<>();
    private static final Map<String, byte[]> WARTEND = new ConcurrentHashMap<>();

    private WappenStore() {}

    /** Eindeutiger Dateiname: Hex der UTF-8-Bytes des klein geschriebenen Namens. */
    public static String key(String stadt) {
        return java.util.HexFormat.of().formatHex(stadt.trim().toLowerCase(Locale.ROOT).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Stadt aufgeloest: freigegebenes und wartendes Wappen loeschen. */
    public static void loeschen(Stadt s) {
        try {
            entfernen(s);
            ablehnen(s);
        } catch (IOException e) {
            CrownsCore.LOGGER.warn("Wappen von {} nicht geloescht: {}", s.name, e.toString());
        }
    }

    public static void load(MinecraftServer server) {
        FREI.clear();
        WARTEND.clear();
        dir = server.getWorldPath(LevelResource.ROOT).resolve("crownscore").resolve("wappen");
        try {
            Files.createDirectories(dir.resolve("wartend"));
            try (var s = Files.list(dir)) {
                for (Path p : s.filter(x -> x.toString().endsWith(".png")).toList()) FREI.put(name(p), Files.readAllBytes(p));
            }
            try (var s = Files.list(dir.resolve("wartend"))) {
                for (Path p : s.filter(x -> x.toString().endsWith(".png")).toList()) WARTEND.put(name(p), Files.readAllBytes(p));
            }
        } catch (IOException e) {
            CrownsCore.LOGGER.error("Wappen konnten nicht geladen werden", e);
        }
        if (!FREI.isEmpty() || !WARTEND.isEmpty()) CrownsCore.LOGGER.info("Wappen geladen: {} freigegeben, {} warten", FREI.size(), WARTEND.size());
    }

    private static String name(Path p) {
        String f = p.getFileName().toString();
        return f.substring(0, f.length() - 4);
    }

    public static byte[] wappen(Stadt s) {
        return FREI.get(key(s.name));
    }

    public static byte[] wartend(Stadt s) {
        return WARTEND.get(key(s.name));
    }

    public static List<String> wartendeKeys() {
        return new ArrayList<>(WARTEND.keySet());
    }

    public static void einreichen(Stadt s, byte[] png, boolean freigabe) throws IOException {
        String k = key(s.name);
        if (freigabe) {
            Files.write(dir.resolve("wartend").resolve(k + ".png"), png);
            WARTEND.put(k, png);
        } else {
            Files.write(dir.resolve(k + ".png"), png);
            FREI.put(k, png);
        }
    }

    public static boolean annehmen(Stadt s) throws IOException {
        String k = key(s.name);
        byte[] png = WARTEND.remove(k);
        if (png == null) return false;
        Files.write(dir.resolve(k + ".png"), png);
        Files.deleteIfExists(dir.resolve("wartend").resolve(k + ".png"));
        FREI.put(k, png);
        return true;
    }

    public static boolean ablehnen(Stadt s) throws IOException {
        String k = key(s.name);
        boolean da = WARTEND.remove(k) != null;
        Files.deleteIfExists(dir.resolve("wartend").resolve(k + ".png"));
        return da;
    }

    public static boolean entfernen(Stadt s) throws IOException {
        String k = key(s.name);
        boolean da = FREI.remove(k) != null;
        Files.deleteIfExists(dir.resolve(k + ".png"));
        return da;
    }
}
