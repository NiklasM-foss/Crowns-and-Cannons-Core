package de.niklasmetzger.crownscore.colony;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.niklasmetzger.crownscore.claims.Claims;
import de.niklasmetzger.crownscore.config.CrownsConfig;
import de.niklasmetzger.crownscore.data.Colony;
import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.Nation;
import de.niklasmetzger.crownscore.discord.DiscordWebhook;
import de.niklasmetzger.crownscore.treasury.TreasuryService;
import de.niklasmetzger.crownscore.util.CommandUtil;
import de.niklasmetzger.crownscore.util.Msg;
import de.niklasmetzger.crownscore.util.TimeUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /kolonie: Verfahren der Kolonialbehoerde (besetztes und unbesetztes Land). */
public final class ColonyCommand {
    private ColonyCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("kolonie")
                .then(Commands.literal("liste").executes(ColonyCommand::liste))
                .then(Commands.literal("info")
                        .then(Commands.argument("name", StringArgumentType.string()).suggests(ColonyCommand::suggest)
                                .executes(ColonyCommand::info)))
                .then(Commands.literal("kosten").executes(ColonyCommand::kosten))
                .then(Commands.literal("expedition")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .then(Commands.argument("typ", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("herrscher", "frei"), b))
                                        .executes(ColonyCommand::expedition))))
                .then(Commands.literal("weiter")
                        .then(Commands.argument("name", StringArgumentType.string()).suggests(ColonyCommand::suggest)
                                .executes(ColonyCommand::weiter)))
                .then(Commands.literal("aufgeben")
                        .then(Commands.argument("name", StringArgumentType.string()).suggests(ColonyCommand::suggest)
                                .executes(ColonyCommand::aufgeben)))
                // Kolonialbehoerde (Admins)
                .then(Commands.literal("genehmigen")
                        .then(Commands.argument("name", StringArgumentType.string()).suggests(ColonyCommand::suggest)
                                .executes(ColonyCommand::genehmigen)))
                .then(Commands.literal("ablehnen")
                        .then(Commands.argument("name", StringArgumentType.string()).suggests(ColonyCommand::suggest)
                                .then(Commands.argument("grund", StringArgumentType.greedyString())
                                        .executes(ColonyCommand::ablehnen))))
                .then(Commands.literal("radius")
                        .then(Commands.argument("name", StringArgumentType.string()).suggests(ColonyCommand::suggest)
                                .then(Commands.argument("radius", IntegerArgumentType.integer(16, 10000))
                                        .executes(ColonyCommand::radius))))
                .then(Commands.literal("gebuehr")
                        .then(Commands.argument("name", StringArgumentType.string()).suggests(ColonyCommand::suggest)
                                .then(Commands.argument("betrag", DoubleArgumentType.doubleArg(0))
                                        .executes(ColonyCommand::gebuehr)))));
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggest(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
        CrownsData d = CommandUtil.data(ctx);
        List<String> names = new ArrayList<>();
        for (Colony c : d.colonies.values()) names.add(c.name.contains(" ") ? "\"" + c.name + "\"" : c.name);
        return SharedSuggestionProvider.suggest(names, b);
    }

    private static Colony colony(CommandContext<CommandSourceStack> ctx) {
        CrownsData d = CommandUtil.data(ctx);
        Colony c = d.colonies.get(StringArgumentType.getString(ctx, "name").toLowerCase(Locale.ROOT));
        if (c == null) Msg.err(ctx.getSource(), "Kein Kolonialgebiet mit diesem Namen. /kolonie liste");
        return c;
    }

    private static double cost(Colony c, int stufe) {
        List<? extends Integer> list = "HERRSCHER".equals(c.typ) ? CrownsConfig.KOLONIE_KOSTEN_HERRSCHER.get() : CrownsConfig.KOLONIE_KOSTEN_FREI.get();
        return stufe >= 0 && stufe < list.size() ? list.get(stufe) : 0;
    }

    private static int kosten(CommandContext<CommandSourceStack> ctx) {
        Msg.info(ctx.getSource(), "Verfahren bei lokalem Herrscher:");
        List<? extends Integer> h = CrownsConfig.KOLONIE_KOSTEN_HERRSCHER.get();
        for (int i = 0; i < Colony.STUFEN_HERRSCHER.size(); i++) {
            Msg.line(ctx.getSource(), " §7" + (i + 1) + ". " + Colony.STUFEN_HERRSCHER.get(i) + ": " + (i < h.size() ? Msg.money(h.get(i)) : "0"));
        }
        Msg.info(ctx.getSource(), "Verfahren ohne Herrscher:");
        List<? extends Integer> f = CrownsConfig.KOLONIE_KOSTEN_FREI.get();
        for (int i = 0; i < Colony.STUFEN_FREI.size(); i++) {
            Msg.line(ctx.getSource(), " §7" + (i + 1) + ". " + Colony.STUFEN_FREI.get(i) + ": " + (i < f.size() ? Msg.money(f.get(i)) : "0"));
        }
        Msg.line(ctx.getSource(), " §7Die letzte Stufe prueft die Kolonialbehoerde. Kosten gehen aus der Staatskasse. Radius je Gebiet: " + CrownsConfig.KOLONIE_RADIUS.get() + " Bloecke.");
        return 1;
    }

    private static int liste(CommandContext<CommandSourceStack> ctx) {
        CrownsData d = CommandUtil.data(ctx);
        if (d.colonies.isEmpty()) {
            Msg.info(ctx.getSource(), "Noch keine Kolonialgebiete. /kolonie expedition <Name> <herrscher|frei> am Zielort.");
            return 0;
        }
        Msg.info(ctx.getSource(), "Kolonialgebiete (" + d.colonies.size() + "):");
        for (Colony c : d.colonies.values()) {
            Msg.line(ctx.getSource(), " §6" + c.name + "§f (" + d.anzeige(c.nation) + ") bei " + c.x + "/" + c.z + ": §7" + c.status());
        }
        return d.colonies.size();
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        CrownsData d = CommandUtil.data(ctx);
        Colony c = colony(ctx);
        if (c == null) return 0;
        Msg.info(ctx.getSource(), "§6" + c.name + "§f von " + d.anzeige(c.nation) + ", Typ " + ("HERRSCHER".equals(c.typ) ? "lokaler Herrscher" : "unbesetztes Land"));
        Msg.line(ctx.getSource(), " §7Mittelpunkt " + c.x + "/" + c.z + " (" + c.dim + "), Radius " + c.radius + ", angelegt " + TimeUtil.fmt(c.angelegt));
        for (int i = 0; i < c.stufen().size(); i++) {
            String mark = i < c.stufe ? "§a[x]" : (i == c.stufe && c.antragOffen ? "§e[?]" : "§7[ ]");
            Msg.line(ctx.getSource(), " " + mark + " §f" + (i + 1) + ". " + c.stufen().get(i) + " §7(" + Msg.money(cost(c, i)) + ")");
        }
        Msg.line(ctx.getSource(), " §7Status: " + c.status() + ", bisher gezahlt " + Msg.money(c.gezahlt) + (c.notiz.isEmpty() ? "" : ", Notiz: " + c.notiz));
        return 1;
    }

    private static int expedition(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        CrownsData d = CommandUtil.data(ctx);
        Nation n = CommandUtil.own(ctx.getSource(), d, p);
        if (n == null) return 0;
        if (!CommandUtil.beamter(ctx.getSource(), n, p)) return 0;
        String name = StringArgumentType.getString(ctx, "name").trim();
        String typ = StringArgumentType.getString(ctx, "typ").toLowerCase(Locale.ROOT).startsWith("h") ? "HERRSCHER" : "FREI";
        if (name.length() < 2 || d.colonies.containsKey(name.toLowerCase(Locale.ROOT))) {
            Msg.err(ctx.getSource(), "Name ungueltig oder schon vergeben.");
            return 0;
        }
        if (d.treasury(n.key()).imVerzug()) {
            Msg.err(ctx.getSource(), "Im Zahlungsverzug genehmigt die Kolonialbehoerde keine Expeditionen.");
            return 0;
        }
        String owner = Claims.nationAt(p.serverLevel(), p.blockPosition());
        if (owner != null) {
            Msg.err(ctx.getSource(), "Hier liegt schon Gebiet von " + d.anzeige(owner) + ". Kolonien gibt es nur in unbeanspruchtem Land.");
            return 0;
        }
        int radius = CrownsConfig.KOLONIE_RADIUS.get();
        for (Colony other : d.colonies.values()) {
            if (!other.dim.equals(p.serverLevel().dimension().location().toString())) continue;
            double dist = Math.hypot(other.x - p.getBlockX(), other.z - p.getBlockZ());
            if (dist < other.radius + radius) {
                Msg.err(ctx.getSource(), "Ueberschneidung mit \"" + other.name + "\" von " + d.anzeige(other.nation) + " (" + (int) dist + " Bloecke entfernt). "
                        + (other.nation.equals(n.key()) ? "Das ist euer eigenes Gebiet." : "Wer zuerst da war, hat den Anspruch."));
                if (!other.nation.equals(n.key())) {
                    Msg.nation(ctx.getSource().getServer(), d.nations.get(other.nation), "§e" + n.name + " wollte eine Expedition bei eurem Kolonialgebiet \"" + other.name + "\" starten.");
                }
                return 0;
            }
        }
        Colony c = new Colony();
        c.name = name;
        c.nation = n.key();
        c.typ = typ;
        c.dim = p.serverLevel().dimension().location().toString();
        c.x = p.getBlockX();
        c.z = p.getBlockZ();
        c.radius = radius;
        c.angelegt = TimeUtil.now();
        double kosten = cost(c, 0);
        if (!TreasuryService.charge(d, n.key(), kosten)) {
            Msg.err(ctx.getSource(), "Die Expedition kostet " + Msg.money(kosten) + ", Staatskasse hat nur " + Msg.money(d.treasury(n.key()).stand) + ".");
            return 0;
        }
        c.gezahlt += kosten;
        c.stufe = 1;
        d.colonies.put(name.toLowerCase(Locale.ROOT), c);
        d.setDirty();
        Msg.broadcast(ctx.getSource().getServer(), n.name + " entsendet eine Expedition nach \"" + name + "\" (" + c.x + "/" + c.z + ").");
        DiscordWebhook.news(":compass: **" + n.name + "** startet die Expedition **" + name + "** bei " + c.x + "/" + c.z
                + " (" + ("HERRSCHER".equals(typ) ? "Gebiet mit lokalem Herrscher" : "unbesetztes Land") + "). Naechste Stufe: " + c.stufenName(1));
        Msg.ok(ctx.getSource(), "Expedition gestartet. Naechste Stufe: " + c.stufenName(1) + " fuer " + Msg.money(cost(c, 1)) + " mit /kolonie weiter " + name);
        return 1;
    }

    private static int weiter(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        CrownsData d = CommandUtil.data(ctx);
        MinecraftServer server = ctx.getSource().getServer();
        Colony c = colony(ctx);
        if (c == null) return 0;
        Nation n = d.nations.get(c.nation);
        if (n == null || !n.isBeamter(p.getUUID()) && !ctx.getSource().hasPermission(2)) {
            Msg.err(ctx.getSource(), "Das Gebiet gehoert nicht deiner Nation.");
            return 0;
        }
        if (c.anerkannt) {
            Msg.err(ctx.getSource(), "\"" + c.name + "\" ist schon anerkannte Kolonie.");
            return 0;
        }
        if (c.antragOffen) {
            Msg.err(ctx.getSource(), "Der Antrag liegt bei der Kolonialbehoerde. Geduld.");
            return 0;
        }
        int next = c.stufe;
        boolean letzte = next == c.stufen().size() - 1;
        double kosten = cost(c, next);
        if (!TreasuryService.charge(d, c.nation, kosten)) {
            Msg.err(ctx.getSource(), c.stufenName(next) + " kostet " + Msg.money(kosten) + ", Staatskasse hat nur " + Msg.money(d.treasury(c.nation).stand) + ".");
            return 0;
        }
        c.gezahlt += kosten;
        if (letzte) {
            c.antragOffen = true;
            d.setDirty();
            Msg.broadcast(server, n.name + " beantragt bei der Kolonialbehoerde die Anerkennung von \"" + c.name + "\".");
            DiscordWebhook.news(":scroll: **" + n.name + "** beantragt die Anerkennung von **" + c.name + "** als Kolonie. Spielleitung: /kolonie genehmigen " + c.name);
            Msg.ok(ctx.getSource(), "Antrag gestellt. Die Behoerde prueft Vertrag, Praesenz, Infrastruktur und fremde Ansprueche.");
        } else {
            c.stufe++;
            d.setDirty();
            Msg.broadcast(server, n.name + " schliesst in \"" + c.name + "\" die Stufe " + c.stufenName(next) + " ab.");
            DiscordWebhook.news(":flag_white: **" + n.name + "** / **" + c.name + "**: " + c.stufenName(next) + " abgeschlossen. Naechste Stufe: " + c.stufenName(c.stufe));
            Msg.ok(ctx.getSource(), "Naechste Stufe: " + c.stufenName(c.stufe) + " fuer " + Msg.money(cost(c, c.stufe)) + ".");
        }
        return 1;
    }

    private static int aufgeben(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        CrownsData d = CommandUtil.data(ctx);
        Colony c = colony(ctx);
        if (c == null) return 0;
        Nation n = d.nations.get(c.nation);
        if (n == null || !n.isChef(p.getUUID()) && !ctx.getSource().hasPermission(2)) {
            Msg.err(ctx.getSource(), "Nur der Staatschef der Besitzernation oder ein Admin.");
            return 0;
        }
        d.colonies.remove(c.name.toLowerCase(Locale.ROOT));
        d.setDirty();
        Msg.broadcast(ctx.getSource().getServer(), d.anzeige(c.nation) + " gibt das Kolonialgebiet \"" + c.name + "\" auf.");
        return 1;
    }

    private static int genehmigen(CommandContext<CommandSourceStack> ctx) {
        if (!CommandUtil.admin(ctx.getSource())) return 0;
        CrownsData d = CommandUtil.data(ctx);
        Colony c = colony(ctx);
        if (c == null) return 0;
        c.antragOffen = false;
        c.anerkannt = true;
        c.stufe = c.stufen().size();
        d.setDirty();
        Msg.broadcast(ctx.getSource().getServer(), "§aDie Kolonialbehoerde erkennt \"" + c.name + "\" offiziell als Kolonie von " + d.anzeige(c.nation) + " an.");
        DiscordWebhook.news(":tada: Die Kolonialbehoerde erkennt **" + c.name + "** als Kolonie von **" + d.anzeige(c.nation) + "** an.");
        return 1;
    }

    private static int ablehnen(CommandContext<CommandSourceStack> ctx) {
        if (!CommandUtil.admin(ctx.getSource())) return 0;
        CrownsData d = CommandUtil.data(ctx);
        Colony c = colony(ctx);
        if (c == null) return 0;
        String grund = StringArgumentType.getString(ctx, "grund");
        c.antragOffen = false;
        c.notiz = "Abgelehnt am " + TimeUtil.fmtShort(TimeUtil.now()) + ": " + grund;
        d.setDirty();
        Msg.broadcast(ctx.getSource().getServer(), "Die Kolonialbehoerde lehnt den Antrag fuer \"" + c.name + "\" ab: " + grund);
        DiscordWebhook.news(":no_entry: Antrag **" + c.name + "** von **" + d.anzeige(c.nation) + "** abgelehnt: " + grund);
        return 1;
    }

    private static int radius(CommandContext<CommandSourceStack> ctx) {
        if (!CommandUtil.admin(ctx.getSource())) return 0;
        CrownsData d = CommandUtil.data(ctx);
        Colony c = colony(ctx);
        if (c == null) return 0;
        c.radius = IntegerArgumentType.getInteger(ctx, "radius");
        d.setDirty();
        Msg.ok(ctx.getSource(), "Radius von \"" + c.name + "\" auf " + c.radius + " gesetzt.");
        return 1;
    }

    private static int gebuehr(CommandContext<CommandSourceStack> ctx) {
        if (!CommandUtil.admin(ctx.getSource())) return 0;
        CrownsData d = CommandUtil.data(ctx);
        Colony c = colony(ctx);
        if (c == null) return 0;
        double betrag = DoubleArgumentType.getDouble(ctx, "betrag");
        if (!TreasuryService.charge(d, c.nation, betrag)) {
            Msg.err(ctx.getSource(), "Staatskasse von " + d.anzeige(c.nation) + " reicht nicht (" + Msg.money(d.treasury(c.nation).stand) + ").");
            return 0;
        }
        c.gezahlt += betrag;
        d.setDirty();
        Msg.broadcast(ctx.getSource().getServer(), "Die Kolonialbehoerde erhebt fuer \"" + c.name + "\" eine Gebuehr von " + Msg.money(betrag) + ".");
        return 1;
    }
}
