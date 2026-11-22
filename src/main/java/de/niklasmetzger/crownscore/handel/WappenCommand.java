package de.niklasmetzger.crownscore.handel;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.niklasmetzger.crownscore.CrownsCore;
import de.niklasmetzger.crownscore.config.CrownsConfig;
import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.Nation;
import de.niklasmetzger.crownscore.data.Stadt;
import de.niklasmetzger.crownscore.flag.FlagCommand;
import de.niklasmetzger.crownscore.flag.FlagImages;
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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Wappen einer Stadt: Upload im Browser (Flaggen-Seite mit ?wappen=), Freigabe durch Admins, sonst Flagge der Nation. */
public final class WappenCommand {
    private WappenCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("stadt").then(Commands.literal("wappen")
                .then(Commands.argument("stadt", StringArgumentType.string()).suggests(WappenCommand::eigeneStaedte)
                        .executes(WappenCommand::link)
                        .then(Commands.literal("entfernen").executes(WappenCommand::entfernen))
                        .then(Commands.argument("code", StringArgumentType.word()).executes(WappenCommand::anwenden)))));
        d.register(Commands.literal("cccore").then(Commands.literal("wappen")
                .then(Commands.literal("annehmen").then(Commands.argument("stadt", StringArgumentType.string()).suggests(WappenCommand::wartende)
                        .executes(ctx -> entscheiden(ctx, true, ""))))
                .then(Commands.literal("ablehnen").then(Commands.argument("stadt", StringArgumentType.string()).suggests(WappenCommand::wartende)
                        .executes(ctx -> entscheiden(ctx, false, ""))
                        .then(Commands.argument("grund", StringArgumentType.greedyString())
                                .executes(ctx -> entscheiden(ctx, false, StringArgumentType.getString(ctx, "grund"))))))));
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> eigeneStaedte(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
        CrownsData d = CrownsData.get(ctx.getSource().getServer());
        String own = ctx.getSource().getEntity() instanceof ServerPlayer p ? d.nationKeyOf(p.getUUID()) : null;
        return SharedSuggestionProvider.suggest(d.staedte.stream().filter(s -> s.nation.equals(own)).map(s -> "\"" + s.name + "\"").toList(), b);
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> wartende(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
        CrownsData d = CrownsData.get(ctx.getSource().getServer());
        return SharedSuggestionProvider.suggest(d.staedte.stream().filter(s -> WappenStore.wartend(s) != null).map(s -> "\"" + s.name + "\"").toList(), b);
    }

    /** Stadt der eigenen Nation, sonst Fehlermeldung. */
    private static Stadt eigene(CommandContext<CommandSourceStack> ctx, ServerPlayer p) {
        CrownsData d = CommandUtil.data(ctx);
        Stadt s = StadtService.byName(d, StringArgumentType.getString(ctx, "stadt"));
        if (s == null) {
            Msg.err(ctx.getSource(), "Diese Stadt gibt es nicht. /stadt zeigt eure Staedte.");
            return null;
        }
        Nation n = d.nations.get(s.nation);
        if (!Perms.isAdmin(ctx.getSource()) && (n == null || !n.isBeamter(p.getUUID()))) {
            Msg.err(ctx.getSource(), "Das Wappen von " + s.name + " aendern nur Staatschef und Beamte von " + (n == null ? s.nation : n.name) + ".");
            return null;
        }
        return s;
    }

    private static int link(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        Stadt s = eigene(ctx, p);
        if (s == null) return 0;
        String seite = FlagCommand.seite();
        if (seite.isEmpty()) {
            Msg.err(ctx.getSource(), "Die Upload-Seite ist nicht eingerichtet (Config rechte.editorUrl oder umhang.seiteUrl).");
            return 0;
        }
        String url = seite + "?wappen=" + URLEncoder.encode(s.name, StandardCharsets.UTF_8);
        Msg.info(ctx.getSource(), "Wappen fuer " + s.name + (WappenStore.wappen(s) != null ? " (hat schon eins)" : " (bisher Flagge der Nation)") + ":");
        ctx.getSource().sendSystemMessage(Component.literal(" [Upload-Seite oeffnen]").withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url)).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(url)))));
        Msg.line(ctx.getSource(), " §7Danach den angezeigten Befehl hier eingeben: /stadt wappen \"" + s.name + "\" <Code>");
        return 1;
    }

    private static int anwenden(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        Stadt s = eigene(ctx, p);
        if (s == null) return 0;
        String code = StringArgumentType.getString(ctx, "code");
        if (!code.matches("[A-Za-z0-9]{4,32}")) {
            Msg.err(ctx.getSource(), "Der Code sieht falsch aus. Er steht auf der Upload-Seite nach dem Hochladen.");
            return 0;
        }
        MinecraftServer server = ctx.getSource().getServer();
        UUID von = p.getUUID();
        String vonName = p.getGameProfile().getName();
        Msg.info(ctx.getSource(), "Wappen wird geholt ...");
        CompletableFuture.supplyAsync(() -> {
            try {
                byte[] raw = FlagCommand.download(code);
                return FlagImages.png(FlagImages.limit(FlagImages.decode(raw), 512));
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }).whenComplete((png, err) -> server.execute(() -> {
            ServerPlayer pl = server.getPlayerList().getPlayer(von);
            if (err != null) {
                if (pl != null) Msg.player(pl, "§cWappen nicht geladen: " + (err.getCause() != null ? err.getCause().getMessage() : err.getMessage()));
                return;
            }
            boolean freigabe = CrownsConfig.UMHANG_FREIGABE.get();
            try {
                WappenStore.einreichen(s, png, freigabe);
            } catch (Exception e) {
                CrownsCore.LOGGER.error("Wappen nicht gespeichert", e);
                if (pl != null) Msg.player(pl, "§cWappen konnte nicht gespeichert werden.");
                return;
            }
            if (!freigabe) {
                if (pl != null) Msg.player(pl, "§aDas Wappen von " + s.name + " ist gesetzt.");
                return;
            }
            if (pl != null) Msg.player(pl, "§aWappen eingereicht. Ein Admin muss es noch freigeben.");
            String bild = FlagCommand.bildUrl(code);
            for (ServerPlayer admin : server.getPlayerList().getPlayers()) {
                if (Perms.isAdmin(admin)) admin.sendSystemMessage(meldung(s, vonName, bild));
            }
            de.niklasmetzger.crownscore.discord.DiscordWebhook.antraege(":shield: Neues Wappen fuer die Stadt **" + s.name + "** (von " + vonName + ")"
                    + (bild.isEmpty() ? "" : ": <" + bild + ">") + "\nFreigabe im Spiel: /cccore wappen annehmen \"" + s.name + "\" oder im /postfach");
        }));
        return 1;
    }

    static Component meldung(Stadt s, String von, String bild) {
        MutableComponent c = Component.literal(Msg.PREFIX + "§eNeues Wappen fuer " + s.name + (von == null ? "" : " (von " + von + ")") + ". ");
        if (bild != null && !bild.isEmpty()) c.append(knopf("[Bild]", bild, ChatFormatting.AQUA, ClickEvent.Action.OPEN_URL)).append(" ");
        c.append(knopf("[Annehmen]", "/cccore wappen annehmen \"" + s.name + "\"", ChatFormatting.GREEN, ClickEvent.Action.RUN_COMMAND)).append(" ");
        c.append(knopf("[Ablehnen]", "/cccore wappen ablehnen \"" + s.name + "\" ", ChatFormatting.RED, ClickEvent.Action.SUGGEST_COMMAND));
        return c;
    }

    private static Component knopf(String text, String cmd, ChatFormatting farbe, ClickEvent.Action a) {
        return Component.literal(text).withStyle(Style.EMPTY.withColor(farbe).withClickEvent(new ClickEvent(a, cmd))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(cmd))));
    }

    private static int entfernen(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        Stadt s = eigene(ctx, p);
        if (s == null) return 0;
        try {
            if (!WappenStore.entfernen(s)) {
                Msg.err(ctx.getSource(), s.name + " hat kein eigenes Wappen.");
                return 0;
            }
        } catch (Exception e) {
            Msg.err(ctx.getSource(), "Fehler: " + e.getMessage());
            return 0;
        }
        Msg.ok(ctx.getSource(), s.name + " zeigt wieder die Flagge der Nation.");
        return 1;
    }

    private static int entscheiden(CommandContext<CommandSourceStack> ctx, boolean ja, String grund) {
        CommandSourceStack src = ctx.getSource();
        if (!CommandUtil.admin(src)) return 0;
        CrownsData d = CommandUtil.data(ctx);
        Stadt s = StadtService.byName(d, StringArgumentType.getString(ctx, "stadt"));
        if (s == null) {
            Msg.err(src, "Diese Stadt gibt es nicht.");
            return 0;
        }
        try {
            if (!(ja ? WappenStore.annehmen(s) : WappenStore.ablehnen(s))) {
                Msg.err(src, s.name + " hat kein wartendes Wappen.");
                return 0;
            }
        } catch (Exception e) {
            Msg.err(src, "Fehler: " + e.getMessage());
            return 0;
        }
        Nation n = d.nations.get(s.nation);
        String text = ja ? "Das Wappen von " + s.name + " ist freigegeben und steht jetzt auf der Karte."
                : "Das Wappen von " + s.name + " wurde abgelehnt" + (grund.isBlank() ? "." : ": " + grund);
        if (n != null) Postfach.nation(src.getServer(), n, ja ? "Wappen freigegeben" : "Wappen abgelehnt", text, null);
        Msg.ok(src, text);
        return 1;
    }
}
