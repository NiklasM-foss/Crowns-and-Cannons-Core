package de.niklasmetzger.crownscore.perms;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.tree.CommandNode;
import de.niklasmetzger.crownscore.CrownsCore;
import de.niklasmetzger.crownscore.config.CrownsConfig;
import de.niklasmetzger.crownscore.help.CommandCatalog;
import de.niklasmetzger.crownscore.perms.PermData.Group;
import de.niklasmetzger.crownscore.perms.PermData.Holder;
import de.niklasmetzger.crownscore.perms.PermData.Node;
import de.niklasmetzger.crownscore.perms.PermData.Parent;
import de.niklasmetzger.crownscore.perms.PermData.User;
import de.niklasmetzger.crownscore.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.nodes.PermissionNode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Anbindung an den LuckPerms-Web-Editor. Der Server laedt die Rechte im Format von LuckPerms auf einen
 * Bytebin-Server, der Editor zeigt sie im Browser an, und gespeicherte Aenderungen holt
 * /cccore perms applyedits wieder ab. Es wird das Datenformat von LuckPerms 5 verwendet.
 */
public final class WebEditor {
    public static final String ALIAS = "cccore perms";
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final long SESSION_MAX_MS = 24L * 3600 * 1000;

    /** Ein Eintrag im LuckPerms-Format. Ablauf in Sekunden (0 = dauerhaft), world = Dimension (leer = ueberall). */
    record LpNode(String key, boolean value, long expiry, String world, boolean foreignContext) {
        String canon() {
            return key + "|" + value + "|" + expiry + "|" + world + (foreignContext ? "|fremd" : "");
        }
    }

    /** Was beim Oeffnen hochgeladen wurde, damit gleichzeitige Aenderungen im Spiel nicht ueberschrieben werden. */
    private record Session(Map<String, Set<String>> before, long time, boolean[] done) {}

    private static final Map<String, Session> SESSIONS = new ConcurrentHashMap<>();

    private WebEditor() {}

    /** Basisadresse des Datenaustauschs (bytebin), endet mit /. */
    public static String bytebin() {
        String u = CrownsConfig.RECHTE_BYTEBIN_URL.get().trim();
        if (u.isEmpty()) {
            // Standardaufbau aus editor/: Editor unter /editor/, Daten unter /data/
            String e = editorUrl();
            u = e.replaceFirst("editor/?$", "") + "data/";
        }
        return u.endsWith("/") ? u : u + "/";
    }

    /** Ist ein eigener Editor eingetragen? */
    public static boolean configured(CommandSourceStack src) {
        if (!CrownsConfig.RECHTE_EDITOR_URL.get().trim().isEmpty()) return true;
        Msg.err(src, "Der Rechte-Editor ist nicht eingerichtet. Ordner editor/ auf dem Server starten und in der Config"
                + " rechte.editorUrl eintragen, z.B. https://rechte.example.de/editor/");
        return false;
    }

    public static String editorUrl() {
        String u = CrownsConfig.RECHTE_EDITOR_URL.get().trim();
        return u.endsWith("/") ? u : u + "/";
    }

    public static String userAgent() {
        String v = ModList.get().getModContainerById(CrownsCore.MODID).map(c -> c.getModInfo().getVersion().toString()).orElse("?");
        return "crownscore/" + v;
    }

    // ---- Umwandlung Crowns Core <-> LuckPerms -----------------------------

    static List<LpNode> toNodes(Holder h, boolean group) {
        List<LpNode> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Parent p : h.parents) {
            if (p.active(now)) out.add(new LpNode("group." + PermissionManager.norm(p.group), true, p.expiry / 1000, "", false));
        }
        for (Node n : h.permissions) {
            if (n.active(now)) out.add(new LpNode(PermissionManager.norm(n.node), n.value, n.expiry / 1000, n.dim == null ? "" : n.dim, false));
        }
        int prio = group ? ((Group) h).weight : 100;
        if (h.prefix != null && !h.prefix.isEmpty()) out.add(new LpNode("prefix." + prio + "." + h.prefix, true, 0, "", false));
        if (h.suffix != null && !h.suffix.isEmpty()) out.add(new LpNode("suffix." + prio + "." + h.suffix, true, 0, "", false));
        if (group) {
            Group g = (Group) h;
            if (g.weight != 0) out.add(new LpNode("weight." + g.weight, true, 0, "", false));
            if (g.displayName != null && !g.displayName.isEmpty()) out.add(new LpNode("displayname." + g.displayName, true, 0, "", false));
        }
        return out;
    }

    /** Baut einen Halter aus LuckPerms-Eintraegen neu auf. Unbekannte Kontexte werden uebersprungen und gemeldet. */
    static void fromNodes(Holder h, boolean group, Iterable<LpNode> nodes, List<String> warnings) {
        h.parents.clear();
        h.permissions.clear();
        h.prefix = "";
        h.suffix = "";
        int prefixPrio = Integer.MIN_VALUE, suffixPrio = Integer.MIN_VALUE;
        if (group) {
            ((Group) h).weight = 0;
            ((Group) h).displayName = "";
        }
        for (LpNode n : nodes) {
            if (n.foreignContext()) {
                warnings.add(n.key() + " (Kontext ausser world wird nicht unterstuetzt)");
                continue;
            }
            long expiryMs = n.expiry() * 1000;
            String k = n.key();
            String lower = k.toLowerCase(java.util.Locale.ROOT);
            if (lower.startsWith("group.") && n.value()) {
                h.parents.add(new Parent(lower.substring(6), expiryMs));
            } else if (lower.startsWith("prefix.") || lower.startsWith("suffix.")) {
                String[] parts = k.split("\\.", 3);
                if (parts.length < 3 || !n.value()) continue;
                int prio;
                try {
                    prio = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    continue;
                }
                if (lower.startsWith("prefix.") && prio > prefixPrio) {
                    prefixPrio = prio;
                    h.prefix = parts[2];
                } else if (lower.startsWith("suffix.") && prio > suffixPrio) {
                    suffixPrio = prio;
                    h.suffix = parts[2];
                }
            } else if (group && lower.startsWith("weight.")) {
                try {
                    ((Group) h).weight = Integer.parseInt(k.substring(7));
                } catch (NumberFormatException ignored) {
                    warnings.add(k + " (kein gueltiges Gewicht)");
                }
            } else if (group && lower.startsWith("displayname.")) {
                ((Group) h).displayName = k.substring(12);
            } else {
                h.permissions.add(new Node(lower, n.value(), expiryMs, n.world()));
            }
        }
    }

    static JsonObject toJson(LpNode n) {
        JsonObject o = new JsonObject();
        String lower = n.key().toLowerCase(java.util.Locale.ROOT);
        String type = lower.startsWith("group.") ? "inheritance" : lower.startsWith("prefix.") ? "prefix" : lower.startsWith("suffix.") ? "suffix"
                : lower.startsWith("weight.") ? "weight" : lower.startsWith("displayname.") ? "display_name" : "permission";
        o.addProperty("type", type);
        o.addProperty("key", n.key());
        o.addProperty("value", n.value());
        if (n.expiry() > 0) o.addProperty("expiry", n.expiry());
        if (!n.world().isEmpty()) {
            JsonObject ctx = new JsonObject();
            ctx.addProperty("world", n.world());
            o.add("context", ctx);
        }
        return o;
    }

    static LpNode fromJson(JsonObject o) {
        String key = o.has("key") ? o.get("key").getAsString() : "";
        boolean value = !o.has("value") || o.get("value").getAsBoolean();
        long expiry = o.has("expiry") && !o.get("expiry").isJsonNull() ? o.get("expiry").getAsLong() : 0;
        String world = "";
        boolean foreign = false;
        if (o.has("context") && o.get("context").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("context").entrySet()) {
                if (e.getKey().equals("world") && e.getValue().isJsonPrimitive()) world = e.getValue().getAsString();
                else foreign = true;
            }
        }
        return new LpNode(key, value, expiry, world, foreign);
    }

    private static Set<String> canonSet(List<LpNode> nodes) {
        Set<String> out = new LinkedHashSet<>();
        for (LpNode n : nodes) out.add(n.canon());
        return out;
    }

    // ---- Editor oeffnen ---------------------------------------------------

    public static int open(CommandSourceStack src) {
        if (!configured(src)) return 0;
        MinecraftServer server = src.getServer();
        PermData data = PermissionManager.data();

        JsonArray holders = new JsonArray();
        Map<String, Set<String>> before = new LinkedHashMap<>();

        // Gruppen nach Gewicht, wie im LuckPerms-Editor
        List<Map.Entry<String, Group>> groups = new ArrayList<>(data.groups.entrySet());
        groups.sort(Comparator.comparingInt((Map.Entry<String, Group> e) -> -e.getValue().weight).thenComparing(Map.Entry::getKey));
        for (Map.Entry<String, Group> e : groups) {
            List<LpNode> nodes = toNodes(e.getValue(), true);
            holders.add(holderJson("group", e.getKey(), e.getValue().displayName.isEmpty() ? e.getKey() : e.getValue().displayName, nodes));
            before.put("group:" + e.getKey(), canonSet(nodes));
        }
        // Spieler mit Eintraegen plus alle, die gerade online sind
        Map<String, User> users = new LinkedHashMap<>(data.users);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            users.computeIfAbsent(p.getUUID().toString(), k -> {
                User u = new User();
                u.name = p.getGameProfile().getName();
                u.parents.add(new Parent(PermissionManager.DEFAULT, 0));
                return u;
            });
        }
        for (Map.Entry<String, User> e : users.entrySet()) {
            List<LpNode> nodes = toNodes(e.getValue(), false);
            if (nodes.isEmpty()) nodes.add(new LpNode("group." + PermissionManager.DEFAULT, true, 0, "", false));
            holders.add(holderJson("user", e.getKey(), e.getValue().name.isEmpty() ? e.getKey() : e.getValue().name, nodes));
            before.put("user:" + e.getKey(), canonSet(nodes));
        }

        JsonArray tracks = new JsonArray();
        for (Map.Entry<String, List<String>> t : data.tracks.entrySet()) {
            JsonObject o = new JsonObject();
            o.addProperty("type", "track");
            o.addProperty("id", t.getKey());
            JsonArray g = new JsonArray();
            t.getValue().forEach(g::add);
            o.add("groups", g);
            tracks.add(o);
        }

        JsonObject metadata = new JsonObject();
        metadata.addProperty("commandAlias", ALIAS);
        JsonObject uploader = new JsonObject();
        uploader.addProperty("name", src.getTextName());
        uploader.addProperty("uuid", src.getEntity() instanceof ServerPlayer p ? p.getUUID().toString() : new UUID(0, 0).toString());
        metadata.add("uploader", uploader);
        metadata.addProperty("time", System.currentTimeMillis());
        // Der Editor schaltet Funktionen (z.B. Spieler loeschen) nach der LuckPerms-Protokollversion frei.
        // Crowns Core spricht das Format von LuckPerms 5.5, die eigene Version steht in platform.
        metadata.addProperty("pluginVersion", "5.5.0");
        metadata.addProperty("platform", "Crowns Core " + userAgent().substring(userAgent().indexOf('/') + 1) + " (Forge)");

        JsonObject potential = new JsonObject();
        JsonArray worlds = new JsonArray();
        for (ResourceKey<Level> k : server.levelKeys()) worlds.add(k.location().toString());
        potential.add("world", worlds);

        JsonObject payload = new JsonObject();
        payload.add("metadata", metadata);
        payload.add("permissionHolders", holders);
        payload.add("tracks", tracks);
        payload.add("knownPermissions", knownPermissions(server));
        payload.add("potentialContexts", potential);

        Msg.info(src, "Lade die Rechte zum Web-Editor hoch ...");
        CompletableFuture.supplyAsync(() -> {
            try {
                return upload(gzip(GSON.toJson(payload)));
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }).whenComplete((key, err) -> server.execute(() -> {
            if (err != null) {
                Throwable c = err.getCause() != null ? err.getCause() : err;
                Msg.err(src, "Hochladen fehlgeschlagen: " + c.getMessage());
                CrownsCore.LOGGER.warn("Web-Editor: Hochladen fehlgeschlagen", c);
                return;
            }
            SESSIONS.entrySet().removeIf(e -> System.currentTimeMillis() - e.getValue().time() > SESSION_MAX_MS);
            SESSIONS.put(key, new Session(before, System.currentTimeMillis(), new boolean[1]));
            String url = editorUrl() + key;
            Msg.ok(src, "Web-Editor bereit. Link anklicken, Rechte bearbeiten, dort auf Speichern und den angezeigten Befehl hier eingeben:");
            src.sendSuccess(() -> Component.literal(" " + url).withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA).withUnderlined(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Im Browser oeffnen")))), false);
        }));
        return 1;
    }

    private static JsonObject holderJson(String type, String id, String display, List<LpNode> nodes) {
        JsonObject o = new JsonObject();
        o.addProperty("type", type);
        o.addProperty("id", id);
        o.addProperty("displayName", display);
        JsonArray arr = new JsonArray();
        for (LpNode n : nodes) arr.add(toJson(n));
        o.add("nodes", arr);
        return o;
    }

    /** Vorschlaege im Editor: eigene Befehlsknoten, command.<name> fuer jeden Befehl, Forge-Knoten. */
    private static JsonArray knownPermissions(MinecraftServer server) {
        Set<String> out = new TreeSet<>();
        out.add(de.niklasmetzger.crownscore.util.Perms.ADMIN_NODE);
        out.add("crownscore.*");
        for (CommandCatalog.Entry e : CommandCatalog.entries()) out.add(e.node());
        for (CommandNode<CommandSourceStack> n : server.getCommands().getDispatcher().getRoot().getChildren()) out.add("command." + n.getName());
        try {
            for (PermissionNode<?> n : PermissionAPI.getRegisteredNodes()) out.add(n.getNodeName());
        } catch (Exception ignored) {
            // vor dem Serverstart nicht verfuegbar
        }
        JsonArray arr = new JsonArray();
        out.forEach(arr::add);
        return arr;
    }

    // ---- Aenderungen uebernehmen ------------------------------------------

    public static int apply(CommandSourceStack src, String code, boolean force) {
        if (!configured(src)) return 0;
        MinecraftServer server = src.getServer();
        if (!code.matches("[A-Za-z0-9]{3,32}")) {
            Msg.err(src, "Ungueltiger Code. Er steht im Web-Editor nach dem Speichern.");
            return 0;
        }
        Msg.info(src, "Hole die Aenderungen aus dem Web-Editor ...");
        CompletableFuture.supplyAsync(() -> {
            try {
                return download(code);
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }).whenComplete((json, err) -> server.execute(() -> {
            if (err != null) {
                Throwable c = err.getCause() != null ? err.getCause() : err;
                Msg.err(src, "Abholen fehlgeschlagen: " + c.getMessage());
                return;
            }
            try {
                applyPayload(src, json, force);
            } catch (Exception e) {
                Msg.err(src, "Die Aenderungen konnten nicht gelesen werden: " + e.getMessage());
                CrownsCore.LOGGER.warn("Web-Editor: Aenderungen unlesbar", e);
            }
        }));
        return 1;
    }

    static void applyPayload(CommandSourceStack src, JsonObject payload, boolean force) {
        String sessionId = payload.has("sessionId") && !payload.get("sessionId").isJsonNull() ? payload.get("sessionId").getAsString() : "";
        Session session = SESSIONS.get(sessionId);
        if (session == null && !force) {
            Msg.err(src, "Diese Editor-Sitzung kennt der Server nicht (Neustart oder aelter als 24 Stunden). Dann wuerden die Rechte"
                    + " komplett durch den Stand im Editor ersetzt. Wenn das so gewollt ist: Befehl mit --force am Ende.");
            return;
        }
        if (session != null && session.done()[0] && !force) {
            Msg.err(src, "Diese Aenderungen wurden schon uebernommen. Erneut mit --force am Ende.");
            return;
        }
        PermData data = PermissionManager.data();
        MinecraftServer server = src.getServer();
        int changed = 0;
        List<String> warnings = new ArrayList<>();

        if (payload.has("changes")) {
            for (JsonElement el : payload.getAsJsonArray("changes")) {
                JsonObject ch = el.getAsJsonObject();
                String type = ch.get("type").getAsString();
                String id = ch.get("id").getAsString();
                if (type.equals("track")) {
                    String name = PermissionManager.norm(id);
                    List<String> after = new ArrayList<>();
                    ch.getAsJsonArray("groups").forEach(g -> after.add(PermissionManager.norm(g.getAsString())));
                    List<String> old = data.tracks.get(name);
                    if (after.equals(old)) continue;
                    data.tracks.put(name, after);
                    Msg.line(src, " §6Laufbahn " + name + "§7: " + (old == null ? "neu" : String.join(" > ", old)) + " §7=> §f" + String.join(" > ", after));
                    changed++;
                    continue;
                }
                if (!type.equals("user") && !type.equals("group")) {
                    warnings.add("unbekannter Typ " + type);
                    continue;
                }
                boolean group = type.equals("group");
                Holder h;
                String label;
                if (group) {
                    String name = PermissionManager.norm(id);
                    if (!name.matches("[a-z0-9_\\-]+")) {
                        warnings.add("Gruppenname " + id + " ungueltig");
                        continue;
                    }
                    boolean neu = !data.groups.containsKey(name);
                    h = data.groups.computeIfAbsent(name, k -> new Group());
                    label = "Gruppe " + name + (neu ? " (neu)" : "");
                    id = name;
                } else {
                    UUID uuid;
                    try {
                        uuid = UUID.fromString(id);
                    } catch (IllegalArgumentException e) {
                        warnings.add("Spieler-ID " + id + " ist keine UUID");
                        continue;
                    }
                    String name = server.getProfileCache().get(uuid).map(GameProfile::getName).orElse("");
                    h = PermissionManager.userOrCreate(uuid, name.isEmpty() ? null : name);
                    label = "Spieler " + (((User) h).name.isEmpty() ? id : ((User) h).name);
                }
                Map<String, LpNode> editor = new LinkedHashMap<>();
                for (JsonElement n : ch.getAsJsonArray("nodes")) {
                    LpNode ln = fromJson(n.getAsJsonObject());
                    if (!ln.key().isEmpty()) editor.put(ln.canon(), ln);
                }
                Map<String, LpNode> current = new LinkedHashMap<>();
                for (LpNode ln : toNodes(h, group)) current.put(ln.canon(), ln);

                Set<String> beforeSet = session == null ? null : session.before().get(type + ":" + id);
                Map<String, LpNode> result;
                Set<String> added = new LinkedHashSet<>();
                Set<String> removed = new LinkedHashSet<>();
                if (beforeSet != null) {
                    // Nur das uebernehmen, was im Editor wirklich geaendert wurde
                    for (String k : beforeSet) if (!editor.containsKey(k)) removed.add(k);
                    for (String k : editor.keySet()) if (!beforeSet.contains(k)) added.add(k);
                    result = new LinkedHashMap<>(current);
                    removed.forEach(result::remove);
                    for (String k : added) result.put(k, editor.get(k));
                } else {
                    result = editor;
                    for (String k : current.keySet()) if (!editor.containsKey(k)) removed.add(k);
                    for (String k : editor.keySet()) if (!current.containsKey(k)) added.add(k);
                }
                if (result.keySet().equals(current.keySet())) continue;
                fromNodes(h, group, result.values(), warnings);
                if (!group && h.parents.isEmpty()) h.parents.add(new Parent(PermissionManager.DEFAULT, 0));
                Msg.line(src, " §6" + label + "§7: §a+" + added.size() + " §c-" + removed.size());
                int shown = 0;
                for (String k : added) if (shown++ < 8) Msg.line(src, "   §a+ " + describe(editor.get(k)));
                for (String k : removed) {
                    LpNode n = current.containsKey(k) ? current.get(k) : null;
                    if (n != null && shown++ < 8) Msg.line(src, "   §c- " + describe(n));
                }
                if (shown > 8) Msg.line(src, "   §7... und " + (shown - 8) + " weitere");
                changed++;
            }
        }
        if (payload.has("groupDeletions")) {
            for (JsonElement el : payload.getAsJsonArray("groupDeletions")) {
                String name = PermissionManager.norm(el.getAsString());
                if (name.equals(PermissionManager.DEFAULT)) {
                    warnings.add("default laesst sich nicht loeschen");
                    continue;
                }
                if (data.groups.remove(name) == null) continue;
                for (Group g : data.groups.values()) g.parents.removeIf(p -> PermissionManager.norm(p.group).equals(name));
                for (User u : data.users.values()) u.parents.removeIf(p -> PermissionManager.norm(p.group).equals(name));
                for (List<String> t : data.tracks.values()) t.remove(name);
                Msg.line(src, " §cGruppe " + name + " geloescht");
                changed++;
            }
        }
        if (payload.has("trackDeletions")) {
            for (JsonElement el : payload.getAsJsonArray("trackDeletions")) {
                String name = PermissionManager.norm(el.getAsString());
                if (data.tracks.remove(name) != null) {
                    Msg.line(src, " §cLaufbahn " + name + " geloescht");
                    changed++;
                }
            }
        }
        if (payload.has("userDeletions")) {
            for (JsonElement el : payload.getAsJsonArray("userDeletions")) {
                User u = data.users.remove(el.getAsString());
                if (u != null) {
                    Msg.line(src, " §cEintraege von " + (u.name.isEmpty() ? el.getAsString() : u.name) + " geloescht");
                    changed++;
                }
            }
        }
        if (session != null) session.done()[0] = true;
        for (String w : warnings) Msg.line(src, " §eUebersprungen: " + w);
        if (changed == 0) {
            Msg.info(src, "Keine Aenderungen gefunden.");
            return;
        }
        PermissionManager.changed();
        Msg.ok(src, changed + " Aenderungen aus dem Web-Editor uebernommen.");
    }

    private static String describe(LpNode n) {
        return n.key() + (n.value() ? "" : " = false") + (n.world().isEmpty() ? "" : " in " + n.world())
                + (n.expiry() > 0 ? " bis " + de.niklasmetzger.crownscore.util.TimeUtil.fmt(n.expiry() * 1000) : "");
    }

    // ---- HTTP -------------------------------------------------------------

    static byte[] gzip(String json) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Writer w = new OutputStreamWriter(new GZIPOutputStream(out), StandardCharsets.UTF_8)) {
            w.write(json);
        }
        return out.toByteArray();
    }

    static String upload(byte[] body) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(bytebin() + "post"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("Content-Encoding", "gzip")
                .header("User-Agent", userAgent() + "/editor")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) throw new IOException("Bytebin antwortet mit HTTP " + res.statusCode());
        String key = res.headers().firstValue("Location").orElse(null);
        if (key == null || key.isBlank()) {
            JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
            key = o.get("key").getAsString();
        }
        return key.substring(key.lastIndexOf('/') + 1);
    }

    static JsonObject download(String code) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(bytebin() + code))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", userAgent())
                .header("Accept-Encoding", "gzip")
                .GET()
                .build();
        HttpResponse<byte[]> res = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (res.statusCode() == 404) throw new IOException("Code nicht gefunden (abgelaufen oder vertippt)");
        if (res.statusCode() / 100 != 2) throw new IOException("Bytebin antwortet mit HTTP " + res.statusCode());
        byte[] body = res.body();
        boolean gz = res.headers().firstValue("Content-Encoding").map(v -> v.toLowerCase(java.util.Locale.ROOT).contains("gzip")).orElse(false)
                || (body.length > 2 && (body[0] & 0xff) == 0x1f && (body[1] & 0xff) == 0x8b);
        String text = gz ? new String(new GZIPInputStream(new ByteArrayInputStream(body)).readAllBytes(), StandardCharsets.UTF_8)
                : new String(body, StandardCharsets.UTF_8);
        return JsonParser.parseString(text).getAsJsonObject();
    }
}
