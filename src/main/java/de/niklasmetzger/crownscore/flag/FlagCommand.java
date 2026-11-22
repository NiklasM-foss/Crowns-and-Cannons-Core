package de.niklasmetzger.crownscore.flag;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.niklasmetzger.crownscore.CrownsCore;
import de.niklasmetzger.crownscore.config.CrownsConfig;
import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.Nation;
import de.niklasmetzger.crownscore.discord.DiscordWebhook;
import de.niklasmetzger.crownscore.perms.WebEditor;
import de.niklasmetzger.crownscore.util.CommandUtil;
import de.niklasmetzger.crownscore.util.Msg;
import de.niklasmetzger.crownscore.util.Perms;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

/**
 * Flaggen der Nationen als Umhang:
 * /nation flagge [anwenden <Code>|entfernen], /umhang an|aus und fuer Admins /cccore flaggen.
 * Die Flagge wird im Browser hochgeladen (editor/flagge/), der Server holt sie mit dem Code ab.
 */
public final class FlagCommand {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final DateTimeFormatter ZEIT = DateTimeFormatter.ofPattern("dd.MM. HH:mm");

    private FlagCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("nation").then(Commands.literal("flagge")
                .executes(FlagCommand::status)
                .then(Commands.literal("anwenden").then(Commands.argument("code", StringArgumentType.word())
                        .executes(ctx -> anwenden(ctx, StringArgumentType.getString(ctx, "code")))))
                .then(Commands.literal("entfernen").executes(FlagCommand::entfernen))));
        d.register(Commands.literal("umhang")
                .executes(FlagCommand::umhangStatus)
                .then(Commands.literal("an").executes(ctx -> umhang(ctx, true)))
                .then(Commands.literal("aus").executes(ctx -> umhang(ctx, false))));
        d.register(Commands.literal("cccore").then(Commands.literal("flaggen")
                .executes(FlagCommand::liste)
                .then(Commands.literal("annehmen").then(nationArg().executes(ctx -> pruefen(ctx, true, "")) ))
                .then(Commands.literal("ablehnen").then(nationArg().executes(ctx -> pruefen(ctx, false, ""))
                        .then(Commands.argument("grund", StringArgumentType.greedyString())
                                .executes(ctx -> pruefen(ctx, false, StringArgumentType.getString(ctx, "grund"))))))
                .then(Commands.literal("vorschau").then(nationArg().executes(FlagCommand::vorschau)))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> nationArg() {
        return Commands.argument("nation", StringArgumentType.string()).suggests((c, b) -> {
            CrownsData d = CrownsData.get(c.getSource().getServer());
            return SharedSuggestionProvider.suggest(FlagStore.alleWartend().values().stream()
                    .map(w -> d.nation(w.nation)).filter(n -> n != null).map(n -> n.name.contains(" ") ? "\"" + n.name + "\"" : n.name).toList(), b);
        });
    }

    /** Adresse der Upload-Seite oder leer. */
    public static String seite() {
        String u = CrownsConfig.UMHANG_SEITE.get().trim();
        if (u.isEmpty()) {
            String e = CrownsConfig.RECHTE_EDITOR_URL.get().trim();
            if (e.isEmpty()) return "";
            e = e.endsWith("/") ? e : e + "/";
            u = e.replaceFirst("editor/$", "") + "flagge/";
        }
        return u.endsWith("/") ? u : u + "/";
    }

    /** Oeffentliche Adresse eines hochgeladenen Bilds (fuer Admins zum Ansehen). */
    public static String bildUrl(String code) {
        String s = seite();
        return s.isEmpty() || code == null || code.isEmpty() ? "" : s.replaceFirst("flagge/$", "") + "data/" + code;
    }

    private static MutableComponent link(String text, String url, String hover) {
        return Component.literal(text).withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover))));
    }

    private static MutableComponent button(String text, String command, ChatFormatting color) {
        return Component.literal(text).withStyle(Style.EMPTY.withColor(color)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
    }

    private static String quote(String name) {
        return name.contains(" ") ? "\"" + name + "\"" : name;
    }

    // ---- /nation flagge -------------------------------------------------------

    private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer p = CommandUtil.player(ctx);
        CrownsData d = CommandUtil.data(ctx);
        Nation n = CommandUtil.own(src, d, p);
        if (n == null) return 0;
        String jetzt = FlagStore.eigene(n) != null ? "eigene Flagge"
                : CapeService.flagge(n) != null ? "Landesflagge" : "keine";
        Msg.info(src, "Flagge von " + n.name + ": §e" + jetzt);
        FlagStore.Pending w = FlagStore.wartend(n);
        if (w != null) Msg.line(src, " §7Eine neue Flagge wartet seit " + zeit(w.zeit) + " auf Freigabe durch einen Admin.");
        if (!n.isChef(p.getUUID())) {
            Msg.line(src, " §7Eine eigene Flagge kann nur der Staatschef hochladen.");
            return 1;
        }
        String url = seite() + ("LINKS".equalsIgnoreCase(CrownsConfig.UMHANG_DREHUNG.get().trim()) ? "?drehung=links" : "");
        if (seite().isEmpty()) {
            Msg.line(src, " §cDie Upload-Seite ist nicht eingerichtet (Config umhang.seiteUrl oder rechte.editorUrl).");
            return 1;
        }
        src.sendSuccess(() -> Component.literal(" §71. ").append(link("Upload-Seite oeffnen", url, url))
                .append(Component.literal("§7, Bild hochladen, Vorschau pruefen.")), false);
        Msg.line(src, " §72. Den angezeigten Befehl hier eingeben: §e/nation flagge anwenden <Code>");
        if (CrownsConfig.UMHANG_FREIGABE.get()) Msg.line(src, " §73. Ein Admin gibt die Flagge frei, dann tragen alle Buerger sie.");
        if (FlagStore.eigene(n) != null) Msg.line(src, " §7Zurueck zur Landesflagge: §e/nation flagge entfernen");
        return 1;
    }

    private static int anwenden(CommandContext<CommandSourceStack> ctx, String code) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer p = CommandUtil.player(ctx);
        CrownsData d = CommandUtil.data(ctx);
        Nation n = CommandUtil.own(src, d, p);
        if (n == null || !CommandUtil.chef(src, n, p)) return 0;
        if (!code.matches("[A-Za-z0-9]{3,32}")) {
            Msg.err(src, "Ungueltiger Code. Er steht auf der Upload-Seite nach dem Hochladen.");
            return 0;
        }
        if (seite().isEmpty()) {
            Msg.err(src, "Die Upload-Seite ist nicht eingerichtet.");
            return 0;
        }
        Msg.info(src, "Flagge wird abgeholt ...");
        MinecraftServer server = src.getServer();
        UUID staat = n.staat();
        String von = p.getGameProfile().getName();
        UUID vonId = p.getUUID();
        Thread t = new Thread(() -> {
            byte[] png;
            String fehler = null;
            try {
                BufferedImage img = FlagImages.limit(FlagImages.decode(download(code)), FlagImages.STORE_SIDE);
                png = FlagImages.png(img);
            } catch (Exception e) {
                png = null;
                fehler = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            byte[] fertig = png;
            String err = fehler;
            server.execute(() -> abgeholt(server, staat, von, vonId, code, fertig, err));
        }, "CrownsCore-Flagge");
        t.setDaemon(true);
        t.start();
        return 1;
    }

    private static void abgeholt(MinecraftServer server, UUID staat, String von, UUID vonId, String code, byte[] png, String fehler) {
        ServerPlayer p = server.getPlayerList().getPlayer(vonId);
        CrownsData d = CrownsData.get(server);
        Nation n = null;
        for (Nation x : d.nations.values()) if (staat.equals(x.staat())) n = x;
        if (n == null) return;
        if (png == null) {
            if (p != null) Msg.player(p, "§cFlagge nicht uebernommen: " + fehler);
            return;
        }
        boolean freigabe = CrownsConfig.UMHANG_FREIGABE.get();
        try {
            FlagStore.einreichen(n, von, vonId, code, png, freigabe);
        } catch (IOException e) {
            CrownsCore.LOGGER.error("Flagge nicht gespeichert", e);
            if (p != null) Msg.player(p, "§cFlagge konnte nicht gespeichert werden, siehe Server-Log.");
            return;
        }
        if (!freigabe) {
            Msg.nation(server, n, "§a" + n.name + " hat eine neue Flagge. Ihr tragt sie ab sofort als Umhang.");
            return;
        }
        if (p != null) Msg.player(p, "§aFlagge eingereicht. Ein Admin muss sie noch freigeben.");
        Nation nation = n;
        for (ServerPlayer admin : server.getPlayerList().getPlayers()) {
            if (Perms.isAdmin(admin)) admin.sendSystemMessage(meldung(nation, von, code));
        }
        String bild = bildUrl(code);
        DiscordWebhook.news(":triangular_flag_on_post: " + n.name + " hat eine neue Flagge eingereicht (von " + von + ")"
                + (bild.isEmpty() ? "" : ": " + bild) + ". Freigabe im Spiel mit /cccore flaggen.");
    }

    private static Component meldung(Nation n, String von, String code) {
        MutableComponent c = Component.literal(Msg.PREFIX + "§e" + n.name + " hat eine neue Flagge eingereicht (von " + von + "). ");
        String bild = bildUrl(code);
        if (!bild.isEmpty()) c.append(link("[Bild]", bild, "Hochgeladenes Bild im Browser")).append(" ");
        c.append(button("[Vorschau]", "/cccore flaggen vorschau " + quote(n.name), ChatFormatting.YELLOW)).append(" ");
        c.append(button("[Annehmen]", "/cccore flaggen annehmen " + quote(n.name), ChatFormatting.GREEN)).append(" ");
        c.append(button("[Ablehnen]", "/cccore flaggen ablehnen " + quote(n.name), ChatFormatting.RED));
        return c;
    }

    private static int entfernen(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer p = CommandUtil.player(ctx);
        CrownsData d = CommandUtil.data(ctx);
        Nation n = CommandUtil.own(src, d, p);
        if (n == null || !CommandUtil.chef(src, n, p)) return 0;
        try {
            if (!FlagStore.entfernen(n)) {
                Msg.err(src, n.name + " hat keine eigene Flagge.");
                return 0;
            }
        } catch (IOException e) {
            Msg.err(src, "Fehler beim Loeschen: " + e.getMessage());
            return 0;
        }
        Msg.nation(src.getServer(), n, "§e" + n.name + " traegt wieder die Landesflagge.");
        return 1;
    }

    public static byte[] download(String code) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(WebEditor.bytebin() + code))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", WebEditor.userAgent() + "/flagge")
                .GET().build();
        HttpResponse<InputStream> res = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = res.body()) {
            if (res.statusCode() == 404) throw new IOException("Code nicht gefunden (abgelaufen oder vertippt)");
            if (res.statusCode() / 100 != 2) throw new IOException("Upload-Server antwortet mit HTTP " + res.statusCode());
            byte[] body = in.readNBytes(FlagImages.MAX_BYTES + 1);
            if (body.length > FlagImages.MAX_BYTES) throw new IOException("Datei zu gross");
            if (body.length > 2 && (body[0] & 0xff) == 0x1f && (body[1] & 0xff) == 0x8b) {
                body = new GZIPInputStream(new ByteArrayInputStream(body)).readNBytes(FlagImages.MAX_BYTES + 1);
            }
            return body;
        }
    }

    // ---- /umhang --------------------------------------------------------------

    private static int umhangStatus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        boolean aus = FlagStore.aus(p.getUUID());
        Msg.info(ctx.getSource(), "Dein Flaggen-Umhang ist " + (aus ? "§caus" : "§aan") + "§r. Umschalten: §e/umhang " + (aus ? "an" : "aus"));
        Msg.line(ctx.getSource(), " §7Sehen kann ihn, wer Crowns Core im Client hat (ist im Modpack).");
        return 1;
    }

    private static int umhang(CommandContext<CommandSourceStack> ctx, boolean an) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        FlagStore.setAus(p.getUUID(), !an);
        Msg.ok(ctx.getSource(), an ? "Du traegst wieder die Flagge deiner Nation." : "Flaggen-Umhang aus, dein normaler Umhang ist wieder sichtbar.");
        return 1;
    }

    // ---- /cccore flaggen (Admins) ---------------------------------------------

    private static int liste(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (!CommandUtil.admin(src)) return 0;
        Map<UUID, FlagStore.Pending> all = FlagStore.alleWartend();
        Msg.info(src, "Flaggen: " + FlagStore.landesflaggen() + " Landesflaggen in der Mod, " + all.size() + " warten auf Freigabe"
                + (CrownsConfig.UMHANG_FREIGABE.get() ? "" : " (Freigabe ist in der Config aus)"));
        CrownsData d = CommandUtil.data(ctx);
        for (FlagStore.Pending w : all.values()) {
            Nation n = d.nation(w.nation);
            if (n == null) continue;
            src.sendSuccess(() -> meldung(n, w.von + ", " + zeit(w.zeit), w.code), false);
        }
        return all.size();
    }

    private static int pruefen(CommandContext<CommandSourceStack> ctx, boolean ok, String grund) {
        CommandSourceStack src = ctx.getSource();
        if (!CommandUtil.admin(src)) return 0;
        CrownsData d = CommandUtil.data(ctx);
        Nation n = CommandUtil.nation(src, d, StringArgumentType.getString(ctx, "nation"));
        if (n == null) return 0;
        try {
            if (!(ok ? FlagStore.annehmen(n) : FlagStore.ablehnen(n))) {
                Msg.err(src, "Von " + n.name + " wartet keine Flagge.");
                return 0;
            }
        } catch (IOException e) {
            Msg.err(src, "Fehler: " + e.getMessage());
            return 0;
        }
        if (ok) {
            Msg.ok(src, "Flagge von " + n.name + " freigegeben.");
            Msg.nation(src.getServer(), n, "§aDie neue Flagge von " + n.name + " ist freigegeben. Ihr tragt sie ab sofort als Umhang.");
            de.niklasmetzger.crownscore.handel.Postfach.nation(src.getServer(), n, "Flagge freigegeben", "Die neue Flagge von " + n.name + " ist freigegeben.", null);
        } else {
            Msg.ok(src, "Flagge von " + n.name + " abgelehnt.");
            Msg.nation(src.getServer(), n, "§cDie eingereichte Flagge von " + n.name + " wurde abgelehnt" + (grund.isBlank() ? "." : ": " + grund));
            de.niklasmetzger.crownscore.handel.Postfach.nation(src.getServer(), n, "Flagge abgelehnt", "Die eingereichte Flagge von " + n.name
                    + " wurde abgelehnt" + (grund.isBlank() ? "." : ": " + grund), null);
        }
        return 1;
    }

    private static int vorschau(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        if (!CommandUtil.admin(src)) return 0;
        ServerPlayer p = CommandUtil.player(ctx);
        CrownsData d = CommandUtil.data(ctx);
        Nation n = CommandUtil.nation(src, d, StringArgumentType.getString(ctx, "nation"));
        if (n == null) return 0;
        byte[] png = FlagStore.wartendPng(n);
        if (png == null) png = CapeService.flagge(n);
        if (png == null) {
            Msg.err(src, n.name + " hat keine Flagge.");
            return 0;
        }
        CapeService.preview(p, png, 60);
        Msg.ok(src, "Du traegst die Flagge von " + n.name + " fuer 60 Sekunden. Mit F5 von hinten ansehen.");
        return 1;
    }

    private static String zeit(long ms) {
        return ZEIT.format(Instant.ofEpochMilli(ms).atZone(ZoneId.of(CrownsConfig.ZEITZONE.get())));
    }

    /** Fuer den Selbsttest: Flagge ohne Download einreichen. */
    public static void einreichenTest(MinecraftServer server, Nation n, byte[] png) throws IOException {
        FlagStore.einreichen(n, "Test", null, "", png, CrownsConfig.UMHANG_FREIGABE.get());
    }
}
