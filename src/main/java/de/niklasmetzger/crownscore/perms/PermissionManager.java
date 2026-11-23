package de.niklasmetzger.crownscore.perms;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.niklasmetzger.crownscore.CrownsCore;
import de.niklasmetzger.crownscore.mode.ModeService;
import de.niklasmetzger.crownscore.perms.PermData.Group;
import de.niklasmetzger.crownscore.perms.PermData.Holder;
import de.niklasmetzger.crownscore.perms.PermData.Node;
import de.niklasmetzger.crownscore.perms.PermData.Parent;
import de.niklasmetzger.crownscore.perms.PermData.User;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rechteverwaltung nach dem Vorbild von LuckPerms: Gruppen mit Gewicht und Vererbung, Rechte pro
 * Spieler und Gruppe, Platzhalter mit *, befristete Eintraege, Praefixe und Laufbahnen (Tracks).
 * Reihenfolge: Spieler vor Gruppen, Gruppen nach Gewicht, im selben Halter exakter Knoten vor Platzhalter.
 */
public final class PermissionManager {
    public static final String DEFAULT = "default";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public enum Tri {
        TRUE, FALSE, UNDEFINED;

        public static Tri of(boolean b) {
            return b ? TRUE : FALSE;
        }
    }

    /** Ergebnis mit Herkunft fuer /cccore perms ... check. */
    public record Result(Tri value, String quelle, String knoten) {}

    private static PermData data = new PermData();
    private static Path file;
    private static MinecraftServer server;
    private static final Map<UUID, Map<String, Tri>> CACHE = new ConcurrentHashMap<>();

    private PermissionManager() {}

    public static PermData data() {
        return data;
    }

    // ---- Laden und Speichern --------------------------------------------

    public static synchronized void load(MinecraftServer srv) {
        server = srv;
        file = srv.getWorldPath(LevelResource.ROOT).resolve("crownscore").resolve("permissions.json");
        PermData loaded = null;
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                loaded = GSON.fromJson(r, PermData.class);
            } catch (Exception e) {
                CrownsCore.LOGGER.error("Rechte: {} ist kaputt, starte mit leerem Stand. Die Datei bleibt liegen.", file, e);
                try {
                    Files.copy(file, file.resolveSibling("permissions.kaputt.json"), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {
                    // nur Sicherungskopie
                }
            }
        }
        data = loaded == null ? new PermData() : loaded;
        if (data.groups == null) data.groups = new LinkedHashMap<>();
        if (data.users == null) data.users = new LinkedHashMap<>();
        if (data.tracks == null) data.tracks = new LinkedHashMap<>();
        data.groups.computeIfAbsent(DEFAULT, k -> new Group());
        CACHE.clear();
        save();
        CrownsCore.LOGGER.info("Rechte geladen: {} Gruppen, {} Spieler, {} Laufbahnen", data.groups.size(), data.users.size(), data.tracks.size());
    }

    public static synchronized void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling("permissions.json.tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(data, w);
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            CrownsCore.LOGGER.error("Rechte konnten nicht gespeichert werden", e);
        }
    }

    /** Nach jeder Aenderung: speichern, Cache leeren, Befehlsbaum und Namen der Spieler neu senden. */
    public static void changed() {
        save();
        CACHE.clear();
        if (server == null) return;
        server.execute(() -> {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                server.getCommands().sendCommands(p);
                ModeService.refresh(p);
            }
        });
    }

    /** Minuetlich: abgelaufene Eintraege entfernen. */
    public static void everyMinute() {
        long now = System.currentTimeMillis();
        boolean any = false;
        for (Holder h : allHolders()) {
            int before = h.permissions.size() + h.parents.size();
            h.prune(now);
            if (h.permissions.size() + h.parents.size() != before) any = true;
        }
        if (any) changed();
        else CACHE.clear();
    }

    private static List<Holder> allHolders() {
        List<Holder> out = new ArrayList<>(data.groups.values());
        out.addAll(data.users.values());
        return out;
    }

    // ---- Zugriff ----------------------------------------------------------

    public static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    public static Group group(String name) {
        return data.groups.get(norm(name));
    }

    public static User user(UUID id) {
        return data.users.get(id.toString());
    }

    public static User userOrCreate(UUID id, String name) {
        User u = data.users.computeIfAbsent(id.toString(), k -> {
            User nu = new User();
            nu.parents.add(new Parent(DEFAULT, 0));
            return nu;
        });
        if (name != null && !name.isEmpty()) u.name = name;
        return u;
    }

    /** Aktive Gruppen eines Spielers, geordnet: hoechstes Gewicht zuerst, bei Gleichstand naeher vererbt zuerst. */
    public static List<String> groupsOf(UUID id) {
        long now = System.currentTimeMillis();
        User u = user(id);
        List<String> start = new ArrayList<>();
        if (u != null) for (Parent p : u.parents) if (p.active(now)) start.add(norm(p.group));
        if (start.isEmpty()) start.add(DEFAULT);
        return inherit(start, now);
    }

    /** Gruppe selbst plus alle geerbten Gruppen in Prioritaetsreihenfolge. */
    public static List<String> groupChain(String group) {
        return inherit(List.of(norm(group)), System.currentTimeMillis());
    }

    private static List<String> inherit(List<String> start, long now) {
        Map<String, Integer> depth = new LinkedHashMap<>();
        Deque<String> queue = new ArrayDeque<>();
        for (String s : start) {
            if (data.groups.containsKey(s) && !depth.containsKey(s)) {
                depth.put(s, 1);
                queue.add(s);
            }
        }
        while (!queue.isEmpty()) {
            String g = queue.poll();
            Group gr = data.groups.get(g);
            if (gr == null) continue;
            for (Parent p : gr.parents) {
                String pn = norm(p.group);
                if (p.active(now) && data.groups.containsKey(pn) && !depth.containsKey(pn)) {
                    depth.put(pn, depth.get(g) + 1);
                    queue.add(pn);
                }
            }
        }
        List<String> out = new ArrayList<>(depth.keySet());
        out.sort(Comparator.comparingInt((String g) -> -data.groups.get(g).weight).thenComparingInt(depth::get));
        return out;
    }

    // ---- Pruefung ---------------------------------------------------------

    /** Knoten plus Platzhalter vom genauesten zum allgemeinsten: a.b.c, a.b.*, a.*, *. */
    static List<String> candidates(String node) {
        List<String> out = new ArrayList<>();
        out.add(node);
        // Adminrechte nie ueber crownscore.* oder * vergeben, nur ausdruecklich
        if (node.equals(de.niklasmetzger.crownscore.util.Perms.ADMIN_NODE)) return out;
        String n = node;
        int i;
        while ((i = n.lastIndexOf('.')) > 0) {
            n = n.substring(0, i);
            out.add(n + ".*");
        }
        if (!node.equals("*")) out.add("*");
        return out;
    }

    private static Node match(Holder h, String node, String dim, long now) {
        if (h == null) return null;
        for (String c : candidates(node)) {
            // Eintraege mit passender Dimension gehen vor allgemeinen
            for (int pass = 0; pass < 2; pass++) {
                for (Node n : h.permissions) {
                    if (!n.active(now) || !norm(n.node).equals(c)) continue;
                    boolean ctx = n.dim != null && !n.dim.isEmpty();
                    if (pass == 0 && ctx && n.dim.equals(dim)) return n;
                    if (pass == 1 && !ctx) return n;
                }
            }
        }
        return null;
    }

    public static Result checkDetailed(UUID id, String node, String dim) {
        String nn = norm(node);
        long now = System.currentTimeMillis();
        User u = user(id);
        Node hit = match(u, nn, dim, now);
        if (hit != null) return new Result(Tri.of(hit.value), "Spieler", hit.node);
        for (String g : groupsOf(id)) {
            hit = match(data.groups.get(g), nn, dim, now);
            if (hit != null) return new Result(Tri.of(hit.value), "Gruppe " + g, hit.node);
        }
        return new Result(Tri.UNDEFINED, "", "");
    }

    public static Result checkGroup(String group, String node, String dim) {
        String nn = norm(node);
        long now = System.currentTimeMillis();
        for (String g : groupChain(group)) {
            Node hit = match(data.groups.get(g), nn, dim, now);
            if (hit != null) return new Result(Tri.of(hit.value), "Gruppe " + g, hit.node);
        }
        return new Result(Tri.UNDEFINED, "", "");
    }

    public static Tri check(UUID id, String node, String dim) {
        Map<String, Tri> m = CACHE.computeIfAbsent(id, k -> new ConcurrentHashMap<>());
        String key = node + "@" + dim;
        Tri t = m.get(key);
        if (t == null) {
            t = checkDetailed(id, node, dim).value();
            m.put(key, t);
        }
        return t;
    }

    public static Tri check(ServerPlayer p, String node) {
        return check(p.getUUID(), node, p.level().dimension().location().toString());
    }

    // ---- Praefix ----------------------------------------------------------

    public static String prefix(UUID id) {
        return meta(id, true);
    }

    public static String suffix(UUID id) {
        return meta(id, false);
    }

    private static String meta(UUID id, boolean pre) {
        User u = user(id);
        if (u != null) {
            String v = pre ? u.prefix : u.suffix;
            if (v != null && !v.isEmpty()) return color(v);
        }
        for (String g : groupsOf(id)) {
            Group gr = data.groups.get(g);
            String v = pre ? gr.prefix : gr.suffix;
            if (v != null && !v.isEmpty()) return color(v);
        }
        return "";
    }

    public static String color(String s) {
        return s == null ? "" : s.replaceAll("&([0-9a-fk-orA-FK-OR])", "§$1");
    }
}
