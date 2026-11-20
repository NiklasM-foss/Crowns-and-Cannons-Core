package de.niklasmetzger.crownscore.flag;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import de.niklasmetzger.crownscore.CrownsCore;
import de.niklasmetzger.crownscore.data.Nation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Flaggen der Nationen: eigene (freigegeben oder wartend) unter world/crownscore/flaggen,
 * Landesflaggen aus der Mod (crownscore/flaggen/<CODE>.png). Schluessel ist das Staatskonto
 * der Nation, das bleibt auch bei einer Umbenennung gleich.
 */
public final class FlagStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Eine eingereichte Flagge, die auf Freigabe wartet. */
    public static final class Pending {
        public String nation;
        public String von;
        public String vonId;
        public long zeit;
        public String code;
        transient byte[] png;
    }

    private static Path dir;
    private static final Map<UUID, byte[]> EIGENE = new ConcurrentHashMap<>();
    private static final Map<UUID, Pending> WARTEND = new ConcurrentHashMap<>();
    private static final Set<UUID> AUS = ConcurrentHashMap.newKeySet();
    private static final Map<String, byte[]> LAND = new ConcurrentHashMap<>();
    private static final Set<String> LAND_FEHLT = ConcurrentHashMap.newKeySet();

    private FlagStore() {}

    public static void load(MinecraftServer server) {
        EIGENE.clear();
        WARTEND.clear();
        AUS.clear();
        dir = server.getWorldPath(LevelResource.ROOT).resolve("crownscore").resolve("flaggen");
        try {
            Files.createDirectories(dir.resolve("wartend"));
            try (var s = Files.list(dir)) {
                for (Path p : (Iterable<Path>) s::iterator) {
                    UUID id = id(p);
                    if (id != null) EIGENE.put(id, Files.readAllBytes(p));
                }
            }
            try (var s = Files.list(dir.resolve("wartend"))) {
                for (Path p : (Iterable<Path>) s::iterator) {
                    UUID id = id(p);
                    if (id == null) continue;
                    Path meta = p.resolveSibling(id + ".json");
                    Pending w = Files.exists(meta) ? GSON.fromJson(Files.readString(meta, StandardCharsets.UTF_8), Pending.class) : new Pending();
                    w.png = Files.readAllBytes(p);
                    WARTEND.put(id, w);
                }
            }
            Path aus = dir.resolve("umhang-aus.json");
            if (Files.exists(aus)) {
                Set<String> ids = GSON.fromJson(Files.readString(aus, StandardCharsets.UTF_8), new TypeToken<LinkedHashSet<String>>() {}.getType());
                if (ids != null) for (String s : ids) AUS.add(UUID.fromString(s));
            }
        } catch (Exception e) {
            CrownsCore.LOGGER.error("Flaggen konnten nicht geladen werden", e);
        }
        CrownsCore.LOGGER.info("Flaggen geladen: {} eigene, {} warten auf Freigabe", EIGENE.size(), WARTEND.size());
    }

    private static UUID id(Path p) {
        String n = p.getFileName().toString();
        if (!n.endsWith(".png")) return null;
        try {
            return UUID.fromString(n.substring(0, n.length() - 4));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Freigegebene eigene Flagge der Nation (PNG) oder null. */
    public static byte[] eigene(Nation n) {
        return n == null ? null : EIGENE.get(n.staat());
    }

    public static Pending wartend(Nation n) {
        return n == null ? null : WARTEND.get(n.staat());
    }

    public static byte[] wartendPng(Nation n) {
        Pending w = wartend(n);
        return w == null ? null : w.png;
    }

    public static Map<UUID, Pending> alleWartend() {
        return new LinkedHashMap<>(WARTEND);
    }

    /** Landesflagge aus der Mod (z.B. DEU) oder null. */
    public static byte[] land(String code) {
        if (code == null || code.isEmpty() || LAND_FEHLT.contains(code)) return null;
        return LAND.computeIfAbsent(code, c -> {
            try (InputStream in = FlagStore.class.getResourceAsStream("/crownscore/flaggen/" + c + ".png")) {
                if (in != null) return in.readAllBytes();
            } catch (IOException ignored) {
                // unten als fehlend merken
            }
            LAND_FEHLT.add(c);
            return null;
        });
    }

    /** Neue Flagge einreichen. Mit Freigabe wartet sie, sonst gilt sie sofort. */
    public static void einreichen(Nation n, String von, UUID vonId, String code, byte[] png, boolean freigabe) throws IOException {
        if (!freigabe) {
            setzen(n, png);
            return;
        }
        Pending w = new Pending();
        w.nation = n.name;
        w.von = von;
        w.vonId = vonId == null ? "" : vonId.toString();
        w.zeit = System.currentTimeMillis();
        w.code = code;
        w.png = png;
        Files.write(dir.resolve("wartend").resolve(n.staat() + ".png"), png);
        Files.writeString(dir.resolve("wartend").resolve(n.staat() + ".json"), GSON.toJson(w), StandardCharsets.UTF_8);
        WARTEND.put(n.staat(), w);
    }

    /** Wartende Flagge freigeben: sie wird zur Flagge der Nation. */
    public static boolean annehmen(Nation n) throws IOException {
        Pending w = WARTEND.remove(n.staat());
        if (w == null) return false;
        setzen(n, w.png);
        loescheWartend(n);
        return true;
    }

    public static boolean ablehnen(Nation n) throws IOException {
        if (WARTEND.remove(n.staat()) == null) return false;
        loescheWartend(n);
        return true;
    }

    /** Eigene Flagge und wartende entfernen, die Nation traegt wieder die Landesflagge. */
    public static boolean entfernen(Nation n) throws IOException {
        boolean hatte = EIGENE.remove(n.staat()) != null;
        Files.deleteIfExists(dir.resolve(n.staat() + ".png"));
        hatte |= ablehnen(n);
        return hatte;
    }

    private static void setzen(Nation n, byte[] png) throws IOException {
        Files.write(dir.resolve(n.staat() + ".png"), png);
        EIGENE.put(n.staat(), png);
    }

    private static void loescheWartend(Nation n) throws IOException {
        Files.deleteIfExists(dir.resolve("wartend").resolve(n.staat() + ".png"));
        Files.deleteIfExists(dir.resolve("wartend").resolve(n.staat() + ".json"));
    }

    /** Hat der Spieler seinen Umhang ausgeschaltet? */
    public static boolean aus(UUID player) {
        return AUS.contains(player);
    }

    public static void setAus(UUID player, boolean aus) {
        if (aus ? AUS.add(player) : AUS.remove(player)) {
            Set<String> ids = new LinkedHashSet<>();
            for (UUID u : AUS) ids.add(u.toString());
            try {
                Files.writeString(dir.resolve("umhang-aus.json"), GSON.toJson(ids), StandardCharsets.UTF_8);
            } catch (IOException e) {
                CrownsCore.LOGGER.error("umhang-aus.json nicht gespeichert", e);
            }
        }
    }

    /** Anzahl der Landesflaggen in der Mod (fuer Status und Selbsttest). */
    public static int landesflaggen() {
        Map<String, Boolean> seen = new HashMap<>();
        for (var c : de.niklasmetzger.crownscore.world.WorldMap.countries()) seen.put(c.code(), land(c.code()) != null);
        return (int) seen.values().stream().filter(b -> b).count();
    }
}
