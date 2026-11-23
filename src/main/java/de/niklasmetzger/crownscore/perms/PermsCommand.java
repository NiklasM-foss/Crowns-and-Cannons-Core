package de.niklasmetzger.crownscore.perms;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import de.niklasmetzger.crownscore.help.CommandCatalog.Role;
import de.niklasmetzger.crownscore.perms.PermData.Group;
import de.niklasmetzger.crownscore.perms.PermData.Holder;
import de.niklasmetzger.crownscore.perms.PermData.Node;
import de.niklasmetzger.crownscore.perms.PermData.Parent;
import de.niklasmetzger.crownscore.perms.PermData.User;
import de.niklasmetzger.crownscore.perms.PermissionManager.Result;
import de.niklasmetzger.crownscore.perms.PermissionManager.Tri;
import de.niklasmetzger.crownscore.util.Durations;
import de.niklasmetzger.crownscore.util.Msg;
import de.niklasmetzger.crownscore.util.Perms;
import de.niklasmetzger.crownscore.util.TimeUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.server.permission.PermissionAPI;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** /cccore perms: Gruppen, Spielerrechte, Praefixe und Laufbahnen, angelehnt an LuckPerms. */
public final class PermsCommand {
    private PermsCommand() {}

    private static final SuggestionProvider<CommandSourceStack> GROUPS =
            (c, b) -> SharedSuggestionProvider.suggest(PermissionManager.data().groups.keySet(), b);
    private static final SuggestionProvider<CommandSourceStack> TRACKS =
            (c, b) -> SharedSuggestionProvider.suggest(PermissionManager.data().tracks.keySet(), b);
    private static final SuggestionProvider<CommandSourceStack> DIMS = (c, b) -> {
        List<String> out = new ArrayList<>();
        for (ResourceKey<Level> k : c.getSource().getServer().levelKeys()) out.add(k.location().toString());
        return SharedSuggestionProvider.suggest(out, b);
    };
    private static final SuggestionProvider<CommandSourceStack> NODES = (c, b) -> SharedSuggestionProvider.suggest(
            List.of("crownscore.admin", "\"crownscore.*\"", "\"crownscore.cccore.admin.*\"", "\"crownscore.cccore.perms.*\"",
                    "crownscore.krieg.erklaeren", "command.gamemode", "command.tp", "\"*\""), b);
    private static final SuggestionProvider<CommandSourceStack> DURATIONS =
            (c, b) -> SharedSuggestionProvider.suggest(List.of("1h", "1d", "7d", "30d"), b);

    private interface Action {
        int run(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;
    }

    /** Sicherung, falls die Befehle nicht ausgeblendet werden konnten: ohne Recht keine Ausfuehrung. */
    private static com.mojang.brigadier.Command<CommandSourceStack> guard(String node, Action a) {
        return ctx -> {
            if (!Perms.allowed(ctx.getSource(), "crownscore.cccore.perms." + node, Role.ADMIN)) {
                Msg.err(ctx.getSource(), "Dafuer fehlt dir das Recht crownscore.cccore.perms." + node + ".");
                return 0;
            }
            return a.run(ctx);
        };
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("perms")
                .executes(guard("info", PermsCommand::info))
                .then(Commands.literal("info").executes(guard("info", PermsCommand::info)))
                .then(Commands.literal("listgroups").executes(guard("listgroups", PermsCommand::listGroups)))
                .then(Commands.literal("creategroup").then(Commands.argument("gruppe", StringArgumentType.word())
                        .executes(guard("creategroup", PermsCommand::createGroup))))
                .then(Commands.literal("deletegroup").then(Commands.argument("gruppe", StringArgumentType.word()).suggests(GROUPS)
                        .executes(guard("deletegroup", PermsCommand::deleteGroup))))
                .then(Commands.literal("listtracks").executes(guard("listtracks", PermsCommand::listTracks)))
                .then(Commands.literal("createtrack").then(Commands.argument("track", StringArgumentType.word())
                        .executes(guard("createtrack", PermsCommand::createTrack))))
                .then(Commands.literal("deletetrack").then(Commands.argument("track", StringArgumentType.word()).suggests(TRACKS)
                        .executes(guard("deletetrack", PermsCommand::deleteTrack))))
                .then(Commands.literal("search").then(Commands.argument("knoten", StringArgumentType.string()).suggests(NODES)
                        .executes(guard("search", PermsCommand::search))))
                .then(Commands.literal("editor").executes(guard("editor", ctx -> WebEditor.open(ctx.getSource()))))
                .then(Commands.literal("applyedits").then(Commands.argument("code", StringArgumentType.word())
                        .executes(guard("applyedits", ctx -> WebEditor.apply(ctx.getSource(), StringArgumentType.getString(ctx, "code"), false)))
                        .then(Commands.literal("--force")
                                .executes(guard("applyedits", ctx -> WebEditor.apply(ctx.getSource(), StringArgumentType.getString(ctx, "code"), true))))))
                .then(Commands.literal("reload").executes(guard("reload", ctx -> {
                    PermissionManager.load(ctx.getSource().getServer());
                    PermissionManager.changed();
                    Msg.ok(ctx.getSource(), "Rechte neu aus der Datei geladen.");
                    return 1;
                })))
                .then(Commands.literal("user").then(Commands.argument("spieler", GameProfileArgument.gameProfile())
                        .then(Commands.literal("info").executes(guard("user", PermsCommand::userInfo)))
                        .then(permissionBranch("user", true))
                        .then(Commands.literal("parent")
                                .then(Commands.literal("add").then(Commands.argument("gruppe", StringArgumentType.word()).suggests(GROUPS)
                                        .executes(guard("user", ctx -> userParent(ctx, "add", 0)))
                                        .then(Commands.argument("dauer", StringArgumentType.word()).suggests(DURATIONS)
                                                .executes(guard("user", ctx -> userParent(ctx, "add", duration(ctx)))))))
                                .then(Commands.literal("set").then(Commands.argument("gruppe", StringArgumentType.word()).suggests(GROUPS)
                                        .executes(guard("user", ctx -> userParent(ctx, "set", 0)))))
                                .then(Commands.literal("remove").then(Commands.argument("gruppe", StringArgumentType.word()).suggests(GROUPS)
                                        .executes(guard("user", ctx -> userParent(ctx, "remove", 0))))))
                        .then(metaBranch("user", true))
                        .then(Commands.literal("promote").then(Commands.argument("track", StringArgumentType.word()).suggests(TRACKS)
                                .executes(guard("user", ctx -> promote(ctx, 1)))))
                        .then(Commands.literal("demote").then(Commands.argument("track", StringArgumentType.word()).suggests(TRACKS)
                                .executes(guard("user", ctx -> promote(ctx, -1)))))))
                .then(Commands.literal("group").then(Commands.argument("gruppe", StringArgumentType.word()).suggests(GROUPS)
                        .then(Commands.literal("info").executes(guard("group", PermsCommand::groupInfo)))
                        .then(permissionBranch("group", false))
                        .then(Commands.literal("parent")
                                .then(Commands.literal("add").then(Commands.argument("eltern", StringArgumentType.word()).suggests(GROUPS)
                                        .executes(guard("group", ctx -> groupParent(ctx, true)))))
                                .then(Commands.literal("remove").then(Commands.argument("eltern", StringArgumentType.word()).suggests(GROUPS)
                                        .executes(guard("group", ctx -> groupParent(ctx, false))))))
                        .then(Commands.literal("setweight").then(Commands.argument("gewicht", IntegerArgumentType.integer(0, 100000))
                                .executes(guard("group", PermsCommand::setWeight))))
                        .then(Commands.literal("setdisplayname").then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(guard("group", PermsCommand::setDisplayName))))
                        .then(metaBranch("group", false))
                        .then(Commands.literal("listmembers").executes(guard("group", PermsCommand::listMembers)))))
                .then(Commands.literal("track").then(Commands.argument("track", StringArgumentType.word()).suggests(TRACKS)
                        .then(Commands.literal("info").executes(guard("track", PermsCommand::trackInfo)))
                        .then(Commands.literal("append").then(Commands.argument("gruppe", StringArgumentType.word()).suggests(GROUPS)
                                .executes(guard("track", ctx -> trackEdit(ctx, "append")))))
                        .then(Commands.literal("insert").then(Commands.argument("gruppe", StringArgumentType.word()).suggests(GROUPS)
                                .then(Commands.argument("position", IntegerArgumentType.integer(1, 100))
                                        .executes(guard("track", ctx -> trackEdit(ctx, "insert"))))))
                        .then(Commands.literal("remove").then(Commands.argument("gruppe", StringArgumentType.word()).suggests(GROUPS)
                                .executes(guard("track", ctx -> trackEdit(ctx, "remove")))))));
    }

    // ---- gemeinsame Zweige ------------------------------------------------

    /** permission set <knoten> [true|false] [dauer] [dimension], unset <knoten>, check <knoten>. */
    private static ArgumentBuilder<CommandSourceStack, ?> permissionBranch(String node, boolean user) {
        return Commands.literal("permission")
                .then(Commands.literal("set").then(Commands.argument("knoten", StringArgumentType.string()).suggests(NODES)
                        .executes(guard(node, ctx -> permSet(ctx, user, true, 0, "")))
                        .then(Commands.argument("wert", BoolArgumentType.bool())
                                .executes(guard(node, ctx -> permSet(ctx, user, BoolArgumentType.getBool(ctx, "wert"), 0, "")))
                                .then(Commands.argument("dauer", StringArgumentType.word()).suggests(DURATIONS)
                                        .executes(guard(node, ctx -> permSet(ctx, user, BoolArgumentType.getBool(ctx, "wert"), duration(ctx), "")))
                                        .then(Commands.argument("dimension", StringArgumentType.greedyString()).suggests(DIMS)
                                                .executes(guard(node, ctx -> permSet(ctx, user, BoolArgumentType.getBool(ctx, "wert"), duration(ctx),
                                                        StringArgumentType.getString(ctx, "dimension")))))))))
                .then(Commands.literal("unset").then(Commands.argument("knoten", StringArgumentType.string()).suggests(NODES)
                        .executes(guard(node, ctx -> permUnset(ctx, user)))))
                .then(Commands.literal("check").then(Commands.argument("knoten", StringArgumentType.string()).suggests(NODES)
                        .executes(guard(node, ctx -> permCheck(ctx, user)))));
    }

    /** meta setprefix <text>, setsuffix <text>, clear. */
    private static ArgumentBuilder<CommandSourceStack, ?> metaBranch(String node, boolean user) {
        return Commands.literal("meta")
                .then(Commands.literal("setprefix").then(Commands.argument("text", StringArgumentType.greedyString())
                        .executes(guard(node, ctx -> meta(ctx, user, "prefix", StringArgumentType.getString(ctx, "text"))))))
                .then(Commands.literal("setsuffix").then(Commands.argument("text", StringArgumentType.greedyString())
                        .executes(guard(node, ctx -> meta(ctx, user, "suffix", StringArgumentType.getString(ctx, "text"))))))
                .then(Commands.literal("clear").executes(guard(node, ctx -> {
                    meta(ctx, user, "prefix", "");
                    return meta(ctx, user, "suffix", "");
                })));
    }

    // ---- Hilfen -----------------------------------------------------------

    private static long duration(CommandContext<CommandSourceStack> ctx) {
        long d = Durations.parse(StringArgumentType.getString(ctx, "dauer"));
        return d > 0 ? TimeUtil.now() + d : -1;
    }

    private static GameProfile profile(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return GameProfileArgument.getGameProfiles(ctx, "spieler").iterator().next();
    }

    private static Group groupArg(CommandContext<CommandSourceStack> ctx, String arg) {
        String name = StringArgumentType.getString(ctx, arg);
        Group g = PermissionManager.group(name);
        if (g == null) Msg.err(ctx.getSource(), "Gruppe \"" + name + "\" gibt es nicht. /cccore perms listgroups");
        return g;
    }

    private static String bis(long expiry) {
        return expiry == 0 ? "" : " §7(bis " + TimeUtil.fmt(expiry) + ")";
    }

    private static String nodeLine(Node n) {
        return " " + (n.value ? "§a" : "§c") + n.node + " §7= " + n.value + (n.dim.isEmpty() ? "" : " §7in " + n.dim) + bis(n.expiry);
    }

    private static String holderName(CommandContext<CommandSourceStack> ctx, boolean user) throws CommandSyntaxException {
        return user ? profile(ctx).getName() : "Gruppe " + PermissionManager.norm(StringArgumentType.getString(ctx, "gruppe"));
    }

    private static Holder holder(CommandContext<CommandSourceStack> ctx, boolean user) throws CommandSyntaxException {
        if (user) {
            GameProfile gp = profile(ctx);
            return PermissionManager.userOrCreate(gp.getId(), gp.getName());
        }
        return groupArg(ctx, "gruppe");
    }

    private static String handlerHinweis() {
        try {
            return String.valueOf(PermissionAPI.getActivePermissionHandler());
        } catch (Exception e) {
            return "?";
        }
    }

    // ---- allgemein --------------------------------------------------------

    private static int info(CommandContext<CommandSourceStack> ctx) {
        PermData d = PermissionManager.data();
        Msg.info(ctx.getSource(), "§fRechteverwaltung");
        Msg.line(ctx.getSource(), " §7Gruppen: §f" + d.groups.size() + "§7, Spieler mit Eintraegen: §f" + d.users.size() + "§7, Laufbahnen: §f" + d.tracks.size());
        Msg.line(ctx.getSource(), " §7Forge-Handler: §f" + handlerHinweis() + (ForgePermHandler.ID.toString().equals(handlerHinweis()) ? " §a(aktiv)" : " §e(andere Mods fragen nicht hier)"));
        Msg.line(ctx.getSource(), " §7Datei: world/crownscore/permissions.json");
        Msg.line(ctx.getSource(), " §e/cccore perms user <Spieler> info§7, §e/cccore perms group <Gruppe> info§7, §e/cccore list perms");
        return 1;
    }

    private static int listGroups(CommandContext<CommandSourceStack> ctx) {
        List<Map.Entry<String, Group>> list = new ArrayList<>(PermissionManager.data().groups.entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue().weight, a.getValue().weight));
        Msg.info(ctx.getSource(), "Gruppen (" + list.size() + "):");
        for (Map.Entry<String, Group> e : list) {
            Group g = e.getValue();
            Msg.line(ctx.getSource(), " §6" + e.getKey() + " §7Gewicht " + g.weight
                    + (g.displayName.isEmpty() ? "" : ", \"" + g.displayName + "\"")
                    + (g.prefix.isEmpty() ? "" : ", Praefix " + PermissionManager.color(g.prefix) + "§7")
                    + ", " + g.permissions.size() + " Rechte");
        }
        return list.size();
    }

    private static int createGroup(CommandContext<CommandSourceStack> ctx) {
        String name = PermissionManager.norm(StringArgumentType.getString(ctx, "gruppe"));
        if (!name.matches("[a-z0-9_\\-]+")) {
            Msg.err(ctx.getSource(), "Gruppennamen nur aus a-z, 0-9, _ und -.");
            return 0;
        }
        if (PermissionManager.data().groups.containsKey(name)) {
            Msg.err(ctx.getSource(), "Gruppe " + name + " gibt es schon.");
            return 0;
        }
        PermissionManager.data().groups.put(name, new Group());
        PermissionManager.changed();
        Msg.ok(ctx.getSource(), "Gruppe " + name + " angelegt.");
        return 1;
    }

    private static int deleteGroup(CommandContext<CommandSourceStack> ctx) {
        String name = PermissionManager.norm(StringArgumentType.getString(ctx, "gruppe"));
        if (name.equals(PermissionManager.DEFAULT)) {
            Msg.err(ctx.getSource(), "Die Gruppe default laesst sich nicht loeschen.");
            return 0;
        }
        PermData d = PermissionManager.data();
        if (d.groups.remove(name) == null) {
            Msg.err(ctx.getSource(), "Gruppe " + name + " gibt es nicht.");
            return 0;
        }
        for (Group g : d.groups.values()) g.parents.removeIf(p -> PermissionManager.norm(p.group).equals(name));
        for (User u : d.users.values()) u.parents.removeIf(p -> PermissionManager.norm(p.group).equals(name));
        for (List<String> t : d.tracks.values()) t.remove(name);
        PermissionManager.changed();
        Msg.ok(ctx.getSource(), "Gruppe " + name + " geloescht.");
        return 1;
    }

    private static int listTracks(CommandContext<CommandSourceStack> ctx) {
        Map<String, List<String>> t = PermissionManager.data().tracks;
        Msg.info(ctx.getSource(), "Laufbahnen (" + t.size() + "):");
        for (Map.Entry<String, List<String>> e : t.entrySet()) {
            Msg.line(ctx.getSource(), " §6" + e.getKey() + "§7: " + String.join(" > ", e.getValue()));
        }
        return t.size();
    }

    private static int createTrack(CommandContext<CommandSourceStack> ctx) {
        String name = PermissionManager.norm(StringArgumentType.getString(ctx, "track"));
        if (PermissionManager.data().tracks.putIfAbsent(name, new ArrayList<>()) != null) {
            Msg.err(ctx.getSource(), "Laufbahn " + name + " gibt es schon.");
            return 0;
        }
        PermissionManager.changed();
        Msg.ok(ctx.getSource(), "Laufbahn " + name + " angelegt. Gruppen hinzufuegen: /cccore perms track " + name + " append <Gruppe>");
        return 1;
    }

    private static int deleteTrack(CommandContext<CommandSourceStack> ctx) {
        String name = PermissionManager.norm(StringArgumentType.getString(ctx, "track"));
        if (PermissionManager.data().tracks.remove(name) == null) {
            Msg.err(ctx.getSource(), "Laufbahn " + name + " gibt es nicht.");
            return 0;
        }
        PermissionManager.changed();
        Msg.ok(ctx.getSource(), "Laufbahn " + name + " geloescht.");
        return 1;
    }

    private static int search(CommandContext<CommandSourceStack> ctx) {
        String node = PermissionManager.norm(StringArgumentType.getString(ctx, "knoten"));
        PermData d = PermissionManager.data();
        int c = 0;
        Msg.info(ctx.getSource(), "Wer hat " + node + " direkt eingetragen:");
        for (Map.Entry<String, Group> e : d.groups.entrySet()) {
            for (Node n : e.getValue().permissions) {
                if (PermissionManager.norm(n.node).equals(node)) {
                    Msg.line(ctx.getSource(), " §6Gruppe " + e.getKey() + nodeLine(n));
                    c++;
                }
            }
        }
        for (User u : d.users.values()) {
            for (Node n : u.permissions) {
                if (PermissionManager.norm(n.node).equals(node)) {
                    Msg.line(ctx.getSource(), " §bSpieler " + u.name + nodeLine(n));
                    c++;
                }
            }
        }
        if (c == 0) Msg.line(ctx.getSource(), " §7niemand");
        return c;
    }

    // ---- Rechte setzen ----------------------------------------------------

    private static int permSet(CommandContext<CommandSourceStack> ctx, boolean user, boolean value, long expiry, String dim) throws CommandSyntaxException {
        if (expiry < 0) {
            Msg.err(ctx.getSource(), "Ungueltige Dauer. Beispiele: 30m, 12h, 7d, 1w.");
            return 0;
        }
        Holder h = holder(ctx, user);
        if (h == null) return 0;
        String node = PermissionManager.norm(StringArgumentType.getString(ctx, "knoten"));
        String d = dim == null ? "" : dim.trim();
        h.permissions.removeIf(n -> PermissionManager.norm(n.node).equals(node) && n.dim.equals(d));
        h.permissions.add(new Node(node, value, expiry, d));
        PermissionManager.changed();
        Msg.ok(ctx.getSource(), holderName(ctx, user) + ": " + node + " = " + value + (d.isEmpty() ? "" : " in " + d) + bis(expiry));
        return 1;
    }

    private static int permUnset(CommandContext<CommandSourceStack> ctx, boolean user) throws CommandSyntaxException {
        Holder h = holder(ctx, user);
        if (h == null) return 0;
        String node = PermissionManager.norm(StringArgumentType.getString(ctx, "knoten"));
        boolean removed = h.permissions.removeIf(n -> PermissionManager.norm(n.node).equals(node));
        if (!removed) {
            Msg.err(ctx.getSource(), holderName(ctx, user) + " hat " + node + " nicht direkt eingetragen.");
            return 0;
        }
        PermissionManager.changed();
        Msg.ok(ctx.getSource(), holderName(ctx, user) + ": " + node + " entfernt.");
        return 1;
    }

    private static int permCheck(CommandContext<CommandSourceStack> ctx, boolean user) throws CommandSyntaxException {
        String node = PermissionManager.norm(StringArgumentType.getString(ctx, "knoten"));
        Result r;
        if (user) {
            GameProfile gp = profile(ctx);
            ServerPlayer online = ctx.getSource().getServer().getPlayerList().getPlayer(gp.getId());
            String dim = online == null ? "" : online.level().dimension().location().toString();
            r = PermissionManager.checkDetailed(gp.getId(), node, dim);
        } else {
            if (groupArg(ctx, "gruppe") == null) return 0;
            r = PermissionManager.checkGroup(StringArgumentType.getString(ctx, "gruppe"), node, "");
        }
        String wert = r.value() == Tri.TRUE ? "§atrue" : r.value() == Tri.FALSE ? "§cfalse" : "§7nicht gesetzt (Standard der Mod oder OP entscheidet)";
        Msg.info(ctx.getSource(), holderName(ctx, user) + " " + node + ": " + wert
                + (r.value() == Tri.UNDEFINED ? "" : " §7aus " + r.quelle() + " (" + r.knoten() + ")"));
        return r.value() == Tri.TRUE ? 1 : 0;
    }

    private static int meta(CommandContext<CommandSourceStack> ctx, boolean user, String key, String text) throws CommandSyntaxException {
        Holder h = holder(ctx, user);
        if (h == null) return 0;
        String t = text == null ? "" : text;
        if (t.length() > 48) {
            Msg.err(ctx.getSource(), "Hoechstens 48 Zeichen.");
            return 0;
        }
        if (key.equals("prefix")) h.prefix = t;
        else h.suffix = t;
        PermissionManager.changed();
        Msg.ok(ctx.getSource(), holderName(ctx, user) + ": " + (key.equals("prefix") ? "Praefix" : "Suffix") + " "
                + (t.isEmpty() ? "entfernt" : "gesetzt auf " + PermissionManager.color(t)));
        return 1;
    }

    // ---- Spieler ----------------------------------------------------------

    private static int userInfo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        GameProfile gp = profile(ctx);
        User u = PermissionManager.user(gp.getId());
        long now = TimeUtil.now();
        Msg.info(ctx.getSource(), "§fSpieler " + gp.getName());
        Msg.line(ctx.getSource(), " §7Gruppen (wirksam, nach Gewicht): §f" + String.join(", ", PermissionManager.groupsOf(gp.getId())));
        if (u != null) {
            for (Parent p : u.parents) if (p.active(now)) Msg.line(ctx.getSource(), " §7Mitglied in §6" + p.group + bis(p.expiry));
            for (Node n : u.permissions) if (n.active(now)) Msg.line(ctx.getSource(), nodeLine(n));
        }
        String pre = PermissionManager.prefix(gp.getId());
        String suf = PermissionManager.suffix(gp.getId());
        Msg.line(ctx.getSource(), " §7Praefix: " + (pre.isEmpty() ? "§8keins" : pre) + "§r §7Suffix: " + (suf.isEmpty() ? "§8keins" : suf));
        return 1;
    }

    private static int userParent(CommandContext<CommandSourceStack> ctx, String mode, long expiry) throws CommandSyntaxException {
        if (expiry < 0) {
            Msg.err(ctx.getSource(), "Ungueltige Dauer. Beispiele: 30m, 12h, 7d, 1w.");
            return 0;
        }
        GameProfile gp = profile(ctx);
        String g = PermissionManager.norm(StringArgumentType.getString(ctx, "gruppe"));
        if (!mode.equals("remove") && PermissionManager.group(g) == null) {
            Msg.err(ctx.getSource(), "Gruppe \"" + g + "\" gibt es nicht. /cccore perms listgroups");
            return 0;
        }
        User u = PermissionManager.userOrCreate(gp.getId(), gp.getName());
        switch (mode) {
            case "set" -> {
                u.parents.clear();
                u.parents.add(new Parent(g, 0));
                Msg.ok(ctx.getSource(), gp.getName() + " ist jetzt nur noch in der Gruppe " + g + ".");
            }
            case "remove" -> {
                if (!u.parents.removeIf(p -> PermissionManager.norm(p.group).equals(g))) {
                    Msg.err(ctx.getSource(), gp.getName() + " ist nicht in der Gruppe " + g + ".");
                    return 0;
                }
                Msg.ok(ctx.getSource(), gp.getName() + " aus der Gruppe " + g + " entfernt.");
            }
            default -> {
                u.parents.removeIf(p -> PermissionManager.norm(p.group).equals(g));
                u.parents.add(new Parent(g, expiry));
                Msg.ok(ctx.getSource(), gp.getName() + " ist jetzt in der Gruppe " + g + bis(expiry) + "§a.");
            }
        }
        PermissionManager.changed();
        return 1;
    }

    private static int promote(CommandContext<CommandSourceStack> ctx, int dir) throws CommandSyntaxException {
        GameProfile gp = profile(ctx);
        String t = PermissionManager.norm(StringArgumentType.getString(ctx, "track"));
        List<String> track = PermissionManager.data().tracks.get(t);
        if (track == null || track.isEmpty()) {
            Msg.err(ctx.getSource(), "Laufbahn " + t + " gibt es nicht oder sie ist leer.");
            return 0;
        }
        User u = PermissionManager.userOrCreate(gp.getId(), gp.getName());
        int idx = -1;
        for (int i = track.size() - 1; i >= 0; i--) {
            final String g = track.get(i);
            if (u.parents.stream().anyMatch(p -> PermissionManager.norm(p.group).equals(g))) {
                idx = i;
                break;
            }
        }
        int next = idx + dir;
        if (dir > 0 && next >= track.size()) {
            Msg.err(ctx.getSource(), gp.getName() + " steht schon ganz oben in " + t + ".");
            return 0;
        }
        if (dir < 0 && idx < 0) {
            Msg.err(ctx.getSource(), gp.getName() + " ist in keiner Gruppe der Laufbahn " + t + ".");
            return 0;
        }
        if (idx >= 0) {
            final String old = track.get(idx);
            u.parents.removeIf(p -> PermissionManager.norm(p.group).equals(old));
        }
        if (next >= 0) {
            u.parents.add(new Parent(track.get(next), 0));
            Msg.ok(ctx.getSource(), gp.getName() + (dir > 0 ? " befoerdert" : " zurueckgestuft") + ": " + (idx >= 0 ? track.get(idx) : "keine") + " > " + track.get(next));
        } else {
            Msg.ok(ctx.getSource(), gp.getName() + " aus der Laufbahn " + t + " entfernt.");
        }
        if (u.parents.isEmpty()) u.parents.add(new Parent(PermissionManager.DEFAULT, 0));
        PermissionManager.changed();
        return 1;
    }

    // ---- Gruppen ----------------------------------------------------------

    private static int groupInfo(CommandContext<CommandSourceStack> ctx) {
        Group g = groupArg(ctx, "gruppe");
        if (g == null) return 0;
        String name = PermissionManager.norm(StringArgumentType.getString(ctx, "gruppe"));
        long now = TimeUtil.now();
        Msg.info(ctx.getSource(), "§fGruppe " + name + (g.displayName.isEmpty() ? "" : " §7(\"" + g.displayName + "\")") + " §7Gewicht " + g.weight);
        List<String> parents = new ArrayList<>();
        for (Parent p : g.parents) if (p.active(now)) parents.add(p.group);
        Msg.line(ctx.getSource(), " §7Erbt von: §f" + (parents.isEmpty() ? "nichts" : String.join(", ", parents)));
        Msg.line(ctx.getSource(), " §7Praefix: " + (g.prefix.isEmpty() ? "§8keins" : PermissionManager.color(g.prefix)) + "§r §7Suffix: "
                + (g.suffix.isEmpty() ? "§8keins" : PermissionManager.color(g.suffix)));
        for (Node n : g.permissions) if (n.active(now)) Msg.line(ctx.getSource(), nodeLine(n));
        if (g.permissions.isEmpty()) Msg.line(ctx.getSource(), " §8keine eigenen Rechte");
        return 1;
    }

    private static int groupParent(CommandContext<CommandSourceStack> ctx, boolean add) {
        Group g = groupArg(ctx, "gruppe");
        if (g == null) return 0;
        String name = PermissionManager.norm(StringArgumentType.getString(ctx, "gruppe"));
        String parent = PermissionManager.norm(StringArgumentType.getString(ctx, "eltern"));
        if (add) {
            if (PermissionManager.group(parent) == null) {
                Msg.err(ctx.getSource(), "Gruppe \"" + parent + "\" gibt es nicht.");
                return 0;
            }
            if (parent.equals(name)) {
                Msg.err(ctx.getSource(), "Eine Gruppe kann nicht von sich selbst erben.");
                return 0;
            }
            if (PermissionManager.groupChain(parent).contains(name)) {
                Msg.err(ctx.getSource(), "Das waere eine Schleife: " + parent + " erbt schon von " + name + ".");
                return 0;
            }
            g.parents.removeIf(p -> PermissionManager.norm(p.group).equals(parent));
            g.parents.add(new Parent(parent, 0));
            Msg.ok(ctx.getSource(), "Gruppe " + name + " erbt jetzt von " + parent + ".");
        } else {
            if (!g.parents.removeIf(p -> PermissionManager.norm(p.group).equals(parent))) {
                Msg.err(ctx.getSource(), name + " erbt nicht von " + parent + ".");
                return 0;
            }
            Msg.ok(ctx.getSource(), "Gruppe " + name + " erbt nicht mehr von " + parent + ".");
        }
        PermissionManager.changed();
        return 1;
    }

    private static int setWeight(CommandContext<CommandSourceStack> ctx) {
        Group g = groupArg(ctx, "gruppe");
        if (g == null) return 0;
        g.weight = IntegerArgumentType.getInteger(ctx, "gewicht");
        PermissionManager.changed();
        Msg.ok(ctx.getSource(), "Gewicht gesetzt auf " + g.weight + ". Hoeheres Gewicht gewinnt bei Widerspruechen und beim Praefix.");
        return 1;
    }

    private static int setDisplayName(CommandContext<CommandSourceStack> ctx) {
        Group g = groupArg(ctx, "gruppe");
        if (g == null) return 0;
        g.displayName = StringArgumentType.getString(ctx, "name").trim();
        PermissionManager.changed();
        Msg.ok(ctx.getSource(), "Anzeigename gesetzt.");
        return 1;
    }

    private static int listMembers(CommandContext<CommandSourceStack> ctx) {
        String name = PermissionManager.norm(StringArgumentType.getString(ctx, "gruppe"));
        if (groupArg(ctx, "gruppe") == null) return 0;
        long now = TimeUtil.now();
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, User> e : PermissionManager.data().users.entrySet()) {
            for (Parent p : e.getValue().parents) {
                if (p.active(now) && PermissionManager.norm(p.group).equals(name)) out.add(e.getValue().name + bis(p.expiry) + "§f");
            }
        }
        Msg.info(ctx.getSource(), "Mitglieder von " + name + " (" + out.size() + ")" + (name.equals(PermissionManager.DEFAULT) ? ", dazu alle ohne Gruppe" : "") + ":");
        Msg.line(ctx.getSource(), " §f" + (out.isEmpty() ? "§7keine eingetragenen" : String.join(", ", out)));
        return out.size();
    }

    // ---- Laufbahnen -------------------------------------------------------

    private static int trackInfo(CommandContext<CommandSourceStack> ctx) {
        String t = PermissionManager.norm(StringArgumentType.getString(ctx, "track"));
        List<String> track = PermissionManager.data().tracks.get(t);
        if (track == null) {
            Msg.err(ctx.getSource(), "Laufbahn " + t + " gibt es nicht.");
            return 0;
        }
        Msg.info(ctx.getSource(), "Laufbahn " + t + ": §f" + (track.isEmpty() ? "leer" : String.join(" > ", track)));
        return track.size();
    }

    private static int trackEdit(CommandContext<CommandSourceStack> ctx, String mode) {
        String t = PermissionManager.norm(StringArgumentType.getString(ctx, "track"));
        List<String> track = PermissionManager.data().tracks.get(t);
        if (track == null) {
            Msg.err(ctx.getSource(), "Laufbahn " + t + " gibt es nicht.");
            return 0;
        }
        String g = PermissionManager.norm(StringArgumentType.getString(ctx, "gruppe"));
        if (!mode.equals("remove") && PermissionManager.group(g) == null) {
            Msg.err(ctx.getSource(), "Gruppe \"" + g + "\" gibt es nicht.");
            return 0;
        }
        switch (mode) {
            case "append" -> {
                if (track.contains(g)) {
                    Msg.err(ctx.getSource(), g + " ist schon in der Laufbahn.");
                    return 0;
                }
                track.add(g);
            }
            case "insert" -> {
                if (track.contains(g)) {
                    Msg.err(ctx.getSource(), g + " ist schon in der Laufbahn.");
                    return 0;
                }
                track.add(Math.min(track.size(), IntegerArgumentType.getInteger(ctx, "position") - 1), g);
            }
            default -> {
                if (!track.remove(g)) {
                    Msg.err(ctx.getSource(), g + " ist nicht in der Laufbahn.");
                    return 0;
                }
            }
        }
        PermissionManager.changed();
        Msg.ok(ctx.getSource(), "Laufbahn " + t + ": " + (track.isEmpty() ? "leer" : String.join(" > ", track)));
        return 1;
    }
}
