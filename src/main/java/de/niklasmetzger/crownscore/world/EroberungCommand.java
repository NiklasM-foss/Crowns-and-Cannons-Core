package de.niklasmetzger.crownscore.world;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.niklasmetzger.crownscore.config.CrownsConfig;
import de.niklasmetzger.crownscore.data.Besatzung;
import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.Nation;
import de.niklasmetzger.crownscore.util.CommandUtil;
import de.niklasmetzger.crownscore.util.Msg;
import de.niklasmetzger.crownscore.util.Perms;
import de.niklasmetzger.crownscore.util.TimeUtil;
import de.niklasmetzger.crownscore.world.WorldMap.Country;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** /nation land preis|kaufen, /nation besetzen und /nation besetzungen. */
public final class EroberungCommand {
    private EroberungCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("nation")
                .then(Commands.literal("land")
                        .then(Commands.literal("preis").then(Commands.argument("land", StringArgumentType.string()).suggests(EroberungCommand::laender)
                                .executes(EroberungCommand::preis)))
                        .then(Commands.literal("kaufen").then(Commands.argument("land", StringArgumentType.string()).suggests(EroberungCommand::laender)
                                .executes(ctx -> kaufen(ctx, false))
                                .then(Commands.literal("bestaetigen").executes(ctx -> kaufen(ctx, true))))))
                .then(Commands.literal("besetzen")
                        .executes(EroberungCommand::besetzen)
                        .then(Commands.literal("abbrechen")
                                .executes(ctx -> abbrechen(ctx, -1))
                                .then(Commands.argument("nr", IntegerArgumentType.integer(1))
                                        .executes(ctx -> abbrechen(ctx, IntegerArgumentType.getInteger(ctx, "nr"))))))
                .then(Commands.literal("besetzungen").executes(EroberungCommand::liste))
                .then(Commands.literal("aufgeben")
                        .executes(ctx -> aufgeben(ctx, -1))
                        .then(Commands.argument("nr", IntegerArgumentType.integer(1)).executes(ctx -> aufgeben(ctx, IntegerArgumentType.getInteger(ctx, "nr"))))));
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> laender(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
        return SharedSuggestionProvider.suggest(WorldMap.countries().stream().map(Country::code).toList(), b);
    }

    private static Country land(CommandSourceStack src, CrownsData d, String arg) {
        List<String> codes = WeltkarteCommand.landCodes(d, arg);
        Country c = codes.size() == 1 ? WorldMap.byCode(codes.get(0)) : WorldMap.byCode(arg);
        if (c == null) Msg.err(src, "Kein Land \"" + arg + "\" auf der Weltkarte. Kuerzel wie BEL oder den Laendernamen angeben.");
        return c;
    }

    private static int preis(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        CrownsData d = CommandUtil.data(ctx);
        Country c = land(src, d, StringArgumentType.getString(ctx, "land"));
        if (c == null) return 0;
        Nation besitzer = EroberungService.besitzer(d, c);
        boolean frei = besitzer == null || !besitzer.gespielt();
        Msg.info(src, "§f" + c.name() + " §7(" + c.code() + ", " + c.fields() + " Chunks)");
        Msg.line(src, " §7Gehoert: §f" + (besitzer == null ? "niemandem" : besitzer.name + (besitzer.gespielt() ? "" : " (ohne Spieler)")));
        if (frei) Msg.line(src, " §7Kaufpreis: §f" + Msg.money(EroberungService.kaufpreis(c)));
        else Msg.line(src, " §7Besatzungsgebuehr: §f" + Msg.money(EroberungService.besatzungsgebuehr(c)) + "§7, Haltezeit "
                + TimeUtil.duration(CrownsConfig.EROBERUNG_HALTEZEIT_MINUTEN.get() * 60_000L));
        Long sperre = d.landSperre.get(c.code());
        if (sperre != null && sperre > TimeUtil.now()) Msg.line(src, " §eGesperrt bis " + TimeUtil.fmt(sperre));
        if (src.getEntity() instanceof ServerPlayer p) {
            Nation own = d.nationOf(p.getUUID());
            if (own != null) {
                String err = EroberungService.pruefen(d, own, c, frei);
                Msg.line(src, err == null ? " §a" + own.name + " kann das Land " + (frei ? "kaufen: /nation land kaufen " + c.code() : "besetzen: dort /nation besetzen")
                        : " §c" + err);
            }
        }
        return 1;
    }

    private static int kaufen(CommandContext<CommandSourceStack> ctx, boolean ok) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer p = CommandUtil.player(ctx);
        CrownsData d = CommandUtil.data(ctx);
        Nation own = CommandUtil.own(src, d, p);
        if (own == null || !CommandUtil.chef(src, own, p)) return 0;
        Country c = land(src, d, StringArgumentType.getString(ctx, "land"));
        if (c == null) return 0;
        String err = EroberungService.pruefen(d, own, c, true);
        if (err != null) {
            Msg.err(src, err);
            return 0;
        }
        if (!ok) {
            String cmd = "/nation land kaufen " + c.code() + " bestaetigen";
            Msg.info(src, c.name() + " kostet " + Msg.money(EroberungService.kaufpreis(c)) + " aus der Staatskasse (Stand "
                    + Msg.money(de.niklasmetzger.crownscore.treasury.TreasuryService.stand(d, own.key())) + ").");
            src.sendSystemMessage(Component.literal(" [Kaufen]").withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(cmd)))));
            return 1;
        }
        err = EroberungService.kaufen(src.getServer(), own, c);
        if (err != null) {
            Msg.err(src, err);
            return 0;
        }
        Msg.ok(src, c.name() + " gehoert jetzt " + own.name + ".");
        return 1;
    }

    private static int besetzen(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer p = CommandUtil.player(ctx);
        CrownsData d = CommandUtil.data(ctx);
        Nation own = CommandUtil.own(src, d, p);
        if (own == null || !CommandUtil.beamter(src, own, p)) return 0;
        String err = EroberungService.besetzen(p, own);
        if (err != null) {
            Msg.err(src, err);
            return 0;
        }
        Msg.ok(src, "Besatzungsblock gesetzt. Haltet ihn, bis die Zeit um ist. /nation besetzungen zeigt den Stand.");
        return 1;
    }

    private static int abbrechen(CommandContext<CommandSourceStack> ctx, int nr) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        CrownsData d = CommandUtil.data(ctx);
        boolean admin = Perms.isAdmin(src);
        Nation own = src.getEntity() instanceof ServerPlayer p ? d.nationOf(p.getUUID()) : null;
        Besatzung ziel = null;
        for (Besatzung b : d.besatzungen) {
            if (nr > 0 ? b.id == nr : own != null && b.angreifer.equals(own.key())) {
                ziel = b;
                break;
            }
        }
        if (ziel == null) {
            Msg.err(src, nr > 0 ? "Keine laufende Besetzung Nr. " + nr + "." : "Deine Nation hat keine laufende Besetzung.");
            return 0;
        }
        boolean darf = admin || (own != null && ziel.angreifer.equals(own.key()) && src.getEntity() instanceof ServerPlayer p && own.isBeamter(p.getUUID()));
        if (!darf) {
            Msg.err(src, "Abbrechen duerfen nur Staatschef und Beamte der besetzenden Nation.");
            return 0;
        }
        EroberungService.abbrechen(src.getServer(), ziel, admin && (own == null || !ziel.angreifer.equals(own.key()))
                ? "von einem Admin beendet." : "abgebrochen.");
        return 1;
    }

    private static int aufgeben(CommandContext<CommandSourceStack> ctx, int nr) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        String err = EroberungService.aufgeben(src.getServer(), CommandUtil.player(ctx), nr);
        if (err != null) {
            Msg.err(src, err);
            return 0;
        }
        return 1;
    }

    private static int liste(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        CrownsData d = CommandUtil.data(ctx);
        if (d.besatzungen.isEmpty()) {
            Msg.info(src, "Gerade laeuft keine Besetzung.");
            return 0;
        }
        Msg.info(src, "§fLaufende Besetzungen");
        for (Besatzung b : d.besatzungen) Msg.line(src, " §7" + EroberungService.zeile(d, b));
        return d.besatzungen.size();
    }
}
