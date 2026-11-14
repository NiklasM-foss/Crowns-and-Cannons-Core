package de.niklasmetzger.crownscore.log;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import de.niklasmetzger.crownscore.config.CrownsConfig;
import de.niklasmetzger.crownscore.help.CommandCatalog.Role;
import de.niklasmetzger.crownscore.util.CommandUtil;
import de.niklasmetzger.crownscore.util.Msg;
import de.niklasmetzger.crownscore.util.Perms;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** /cccore admin: Blockprotokoll abfragen, Inspektor, Rollback, Restore, Undo, Purge. */
public final class LogCommand {
    private static final int PAGE = 8;
    private static final int DEFAULT_RADIUS = 10;
    private static final Set<LogAction> ROLLBACKABLE = EnumSet.of(LogAction.BREAK, LogAction.PLACE, LogAction.REMOVE, LogAction.ADD, LogAction.KILL);

    /** Letzte Trefferliste je Betrachter fuer /cccore admin seite. */
    private static final Map<String, Result> RESULTS = new HashMap<>();

    private record Result(String titel, List<LogEntry> entries, boolean withPos) {}

    private LogCommand() {}

    private interface Action {
        int run(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;
    }

    private static com.mojang.brigadier.Command<CommandSourceStack> guard(String node, Action a) {
        return ctx -> {
            if (!Perms.allowed(ctx.getSource(), "crownscore.cccore.admin." + node, Role.ADMIN)) {
                Msg.err(ctx.getSource(), "Dafuer fehlt dir das Recht crownscore.cccore.admin." + node + ".");
                return 0;
            }
            return a.run(ctx);
        };
    }

    /** Vorschlaege fuer die Parameter, passend zum angefangenen Wort. */
    private static final SuggestionProvider<CommandSourceStack> PARAMS = LogCommand::suggestParams;

    private static CompletableFuture<Suggestions> suggestParams(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder b) {
        String rem = b.getRemaining();
        int sp = rem.lastIndexOf(' ');
        String prefix = sp < 0 ? "" : rem.substring(0, sp + 1);
        String cur = sp < 0 ? rem : rem.substring(sp + 1);
        List<String> opts = new ArrayList<>();
        if (cur.startsWith("u:")) {
            for (String n : ctx.getSource().getServer().getPlayerNames()) opts.add("u:" + n);
            opts.add("u:#tnt");
            opts.add("u:#creeper");
        } else if (cur.startsWith("t:")) {
            opts.addAll(List.of("t:30m", "t:1h", "t:6h", "t:1d", "t:3d", "t:1w"));
        } else if (cur.startsWith("r:")) {
            opts.addAll(List.of("r:5", "r:10", "r:20", "r:50", "r:#world", "r:#global", "r:#overworld", "r:#nether", "r:#end"));
        } else if (cur.startsWith("a:")) {
            opts.addAll(List.of("a:block", "a:+block", "a:-block", "a:container", "a:+container", "a:-container", "a:item",
                    "a:kill", "a:chat", "a:command", "a:session", "a:click"));
        } else {
            opts.addAll(List.of("u:", "t:", "r:", "a:", "b:", "e:", "#preview", "#count"));
        }
        SuggestionsBuilder off = b.createOffset(b.getStart() + prefix.length());
        for (String o : opts) if (o.toLowerCase(Locale.ROOT).startsWith(cur.toLowerCase(Locale.ROOT))) off.suggest(o);
        return off.buildFuture();
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("admin")
                .executes(guard("status", LogCommand::status))
                .then(Commands.literal("status").executes(guard("status", LogCommand::status)))
                .then(Commands.literal("inspect").executes(guard("inspect", LogCommand::inspect)))
                .then(Commands.literal("lookup")
                        .executes(guard("lookup", ctx -> lookup(ctx, "")))
                        .then(Commands.argument("parameter", StringArgumentType.greedyString()).suggests(PARAMS)
                                .executes(guard("lookup", ctx -> lookup(ctx, StringArgumentType.getString(ctx, "parameter"))))))
                .then(Commands.literal("near")
                        .executes(guard("near", ctx -> near(ctx, 5)))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 100))
                                .executes(guard("near", ctx -> near(ctx, IntegerArgumentType.getInteger(ctx, "radius"))))))
                .then(Commands.literal("seite").then(Commands.argument("seite", IntegerArgumentType.integer(1))
                        .executes(guard("seite", ctx -> page(ctx, IntegerArgumentType.getInteger(ctx, "seite"))))))
                .then(Commands.literal("rollback").then(Commands.argument("parameter", StringArgumentType.greedyString()).suggests(PARAMS)
                        .executes(guard("rollback", ctx -> rollback(ctx, true)))))
                .then(Commands.literal("restore").then(Commands.argument("parameter", StringArgumentType.greedyString()).suggests(PARAMS)
                        .executes(guard("restore", ctx -> rollback(ctx, false)))))
                .then(Commands.literal("anwenden").executes(guard("anwenden", LogCommand::apply)))
                .then(Commands.literal("abbrechen").executes(guard("abbrechen", LogCommand::cancel)))
                .then(Commands.literal("undo").executes(guard("undo", LogCommand::undo)))
                .then(Commands.literal("purge").then(Commands.argument("parameter", StringArgumentType.greedyString())
                        .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(List.of("t:30d", "t:90d", "t:1w"), b))
                        .executes(guard("purge", LogCommand::purge))));
    }

    // ---- Hilfen -----------------------------------------------------------

    static String key(CommandSourceStack src) {
        return src.getEntity() instanceof ServerPlayer p ? p.getUUID().toString() : "#konsole";
    }

    private static UUID owner(CommandSourceStack src) {
        return src.getEntity() instanceof ServerPlayer p ? p.getUUID() : null;
    }

    private static boolean ready(CommandSourceStack src) {
        if (!LogDatabase.ready()) {
            Msg.err(src, "Das Blockprotokoll ist aus oder konnte nicht starten (Config protokoll.aktiv, Server-Log).");
            return false;
        }
        return true;
    }

    private static LogQuery query(CommandSourceStack src, String params) {
        ServerLevel level = src.getEntity() != null ? src.getLevel() : null;
        BlockPos center = src.getEntity() != null ? BlockPos.containing(src.getPosition()) : null;
        return LogQuery.parse(params, level, center);
    }

    private static void fail(CommandSourceStack src, Exception e) {
        Msg.err(src, "Abfrage fehlgeschlagen: " + e.getMessage());
    }

    static void show(CommandSourceStack src, String titel, List<LogEntry> entries, boolean withPos) {
        RESULTS.put(key(src), new Result(titel, entries, withPos));
        showPage(src, 1);
    }

    private static void showPage(CommandSourceStack src, int seite) {
        Result r = RESULTS.get(key(src));
        if (r == null) {
            Msg.err(src, "Keine Abfrage offen. Erst /cccore admin lookup ...");
            return;
        }
        if (r.entries().isEmpty()) {
            Msg.info(src, r.titel() + ": §7keine Eintraege.");
            return;
        }
        int pages = CommandUtil.pages(r.entries().size(), PAGE);
        int s = Math.max(1, Math.min(seite, pages));
        Msg.info(src, "§f" + r.titel() + " §7(" + r.entries().size() + " Treffer)");
        long now = System.currentTimeMillis();
        for (LogEntry e : CommandUtil.page(r.entries(), s, PAGE)) {
            src.sendSuccess(() -> LogFormat.line(e, now, r.withPos()), false);
        }
        if (pages > 1) {
            MutableComponent nav = Component.literal(" ");
            if (s > 1) nav.append(Component.literal("§e[« zurück] ").withStyle(Style.EMPTY
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cccore admin seite " + (s - 1)))));
            nav.append(Component.literal("§7Seite " + s + "/" + pages + " "));
            if (s < pages) nav.append(Component.literal("§e[weiter »]").withStyle(Style.EMPTY
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cccore admin seite " + (s + 1)))));
            src.sendSuccess(() -> nav, false);
        }
    }

    // ---- Befehle ----------------------------------------------------------

    private static int status(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (!ready(src)) return 0;
        MinecraftServer server = src.getServer();
        LogDatabase.async(server, c -> LogDatabase.count(c, "", List.of()), n -> {
            long size = 0;
            try {
                Path f = server.getWorldPath(LevelResource.ROOT).resolve("crownscore").resolve("protokoll.mv.db");
                if (Files.exists(f)) size = Files.size(f);
            } catch (Exception ignored) {
                // Groesse ist nur Zusatzinfo
            }
            Msg.info(src, "§fBlockprotokoll");
            Msg.line(src, " §7Eintraege: §f" + n + "§7, Datenbank: §f" + (size / 1024 / 1024) + " MB§7, Warteschlange: §f" + LogDatabase.queued());
            Msg.line(src, " §7Inspektor: §e/cccore admin inspect§7, Abfrage: §e/cccore admin lookup u:Name t:1d r:20");
            Msg.line(src, " §7Zuruecksetzen: §e/cccore admin rollback u:Name t:2h r:20 #preview");
        }, e -> fail(src, e));
        return 1;
    }

    private static int inspect(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        if (!ready(ctx.getSource())) return 0;
        boolean on = Inspector.toggle(p.getUUID());
        Msg.info(ctx.getSource(), on ? "§aInspektor an.§r Linksklick: wer hat diesen Block gesetzt oder abgebaut. Rechtsklick: Behaelter"
                + " und der Platz davor. Nochmal /cccore admin inspect zum Ausschalten." : "§cInspektor aus.");
        return 1;
    }

    private static int lookup(CommandContext<CommandSourceStack> ctx, String params) {
        CommandSourceStack src = ctx.getSource();
        if (!ready(src)) return 0;
        LogQuery q = query(src, params);
        if (q.error != null) {
            Msg.err(src, q.error);
            return 0;
        }
        List<Object> args = new ArrayList<>();
        String where = q.where(args, EnumSet.allOf(LogAction.class), null);
        int max = CrownsConfig.LOG_MAX_ERGEBNISSE.get();
        if (q.count) {
            LogDatabase.async(src.getServer(), c -> LogDatabase.count(c, where, args),
                    n -> Msg.info(src, "Treffer fuer " + q.describe() + ": §f" + n), e -> fail(src, e));
        } else {
            LogDatabase.async(src.getServer(), c -> LogDatabase.select(c, where, args, max, false),
                    list -> show(src, "Protokoll " + q.describe() + (list.size() >= max ? " (nur die neuesten " + max + ")" : ""), list, true),
                    e -> fail(src, e));
        }
        return 1;
    }

    private static int near(CommandContext<CommandSourceStack> ctx, int r) throws CommandSyntaxException {
        CommandUtil.player(ctx);
        return lookup(ctx, "r:" + r);
    }

    private static int page(CommandContext<CommandSourceStack> ctx, int seite) {
        showPage(ctx.getSource(), seite);
        return 1;
    }

    private static int rollback(CommandContext<CommandSourceStack> ctx, boolean rollback) {
        CommandSourceStack src = ctx.getSource();
        if (!ready(src)) return 0;
        if (RollbackService.busy()) {
            Msg.err(src, "Es laeuft gerade schon ein Rollback. Kurz warten.");
            return 0;
        }
        LogQuery q = query(src, StringArgumentType.getString(ctx, "parameter"));
        if (q.error != null) {
            Msg.err(src, q.error);
            return 0;
        }
        q.defaultRadius(DEFAULT_RADIUS);
        if (!q.hasArea() && !q.global) {
            Msg.err(src, "Gib ein Gebiet an: r:20, r:#world oder r:#global.");
            return 0;
        }
        if (q.users.isEmpty() && q.since == 0 && q.include.isEmpty() && (q.global || q.dimOnly != null)) {
            Msg.err(src, "Zu breit: Fuer eine ganze Welt brauchst du u:Spieler, t:Zeit oder b:Block.");
            return 0;
        }
        if (q.preview && !(src.getEntity() instanceof ServerPlayer)) {
            Msg.err(src, "Die Vorschau geht nur im Spiel.");
            return 0;
        }
        List<Object> args = new ArrayList<>();
        String where = q.where(args, ROLLBACKABLE, !rollback);
        String was = rollback ? "Rollback" : "Wiederherstellung";
        MinecraftServer server = src.getServer();
        LogDatabase.async(server, c -> LogDatabase.select(c, where, args, 0, !rollback), list -> {
            if (list.isEmpty()) {
                Msg.info(src, was + ": §7nichts gefunden fuer " + q.describe() + ".");
                return;
            }
            if (q.preview && src.getEntity() instanceof ServerPlayer p) {
                int n = RollbackService.preview(p, list, rollback, q.describe());
                Msg.info(src, "§eVorschau§r: " + n + " Bloecke siehst nur du so (" + list.size() + " Eintraege insgesamt). §e/cccore admin anwenden§r oder §e/cccore admin abbrechen§r.");
                return;
            }
            Msg.info(src, was + " von " + list.size() + " Eintraegen gestartet (" + q.describe() + ").");
            RollbackService.start(server, list, rollback, key(src), RollbackService.reporter(server, owner(src)));
        }, e -> fail(src, e));
        return 1;
    }

    private static int apply(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        RollbackService.Preview pv = RollbackService.takePreview(p);
        if (pv == null) {
            Msg.err(ctx.getSource(), "Keine Vorschau offen.");
            return 0;
        }
        Msg.info(ctx.getSource(), (pv.rollback() ? "Rollback" : "Wiederherstellung") + " von " + pv.entries().size() + " Eintraegen gestartet (" + pv.beschreibung() + ").");
        RollbackService.start(p.server, pv.entries(), pv.rollback(), key(ctx.getSource()), RollbackService.reporter(p.server, p.getUUID()));
        return 1;
    }

    private static int cancel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        if (!RollbackService.cancelPreview(p)) {
            Msg.err(ctx.getSource(), "Keine Vorschau offen.");
            return 0;
        }
        Msg.ok(ctx.getSource(), "Vorschau verworfen, du siehst wieder die echten Bloecke.");
        return 1;
    }

    private static int undo(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (!ready(src)) return 0;
        RollbackService.LastOp op = RollbackService.last(key(src));
        if (op == null || op.ids().isEmpty()) {
            Msg.err(src, "Nichts zum Rueckgaengigmachen.");
            return 0;
        }
        if (RollbackService.busy()) {
            Msg.err(src, "Es laeuft gerade schon ein Rollback. Kurz warten.");
            return 0;
        }
        MinecraftServer server = src.getServer();
        boolean inverse = !op.rollback();
        LogDatabase.async(server, c -> LogDatabase.byIds(c, op.ids()), list -> {
            if (inverse) Collections.reverse(list); // Rollback laeuft von neu nach alt
            Msg.info(src, "Mache den letzten " + (op.rollback() ? "Rollback" : "Restore") + " rueckgaengig (" + list.size() + " Eintraege).");
            RollbackService.start(server, list, inverse, key(src), RollbackService.reporter(server, owner(src)));
        }, e -> fail(src, e));
        return 1;
    }

    private static int purge(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (!ready(src)) return 0;
        LogQuery q = query(src, StringArgumentType.getString(ctx, "parameter"));
        if (q.error != null || q.since == 0) {
            Msg.err(src, q.error != null ? q.error : "Gib an, was aelter ist als: /cccore admin purge t:30d");
            return 0;
        }
        if (System.currentTimeMillis() - q.since < 86_400_000L) {
            Msg.err(src, "Purge geht erst ab einem Tag (t:1d), damit nichts Aktuelles verschwindet.");
            return 0;
        }
        LogDatabase.async(src.getServer(), c -> LogDatabase.purge(c, q.since),
                n -> Msg.ok(src, n + " Eintraege aelter als " + q.describe().replace("t:", "") + " geloescht."), e -> fail(src, e));
        return 1;
    }
}
