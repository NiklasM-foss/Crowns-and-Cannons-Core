package de.niklasmetzger.crownscore.treasury;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.niklasmetzger.crownscore.config.CrownsConfig;
import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.Nation;
import de.niklasmetzger.crownscore.data.Treasury;
import de.niklasmetzger.crownscore.discord.DiscordWebhook;
import de.niklasmetzger.crownscore.util.CommandUtil;
import de.niklasmetzger.crownscore.util.Msg;
import de.niklasmetzger.crownscore.util.TimeUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/** /darlehen: Kredite der Zentralbank fuer Nationen. */
public final class LoanCommand {
    private LoanCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("darlehen")
                .executes(ctx -> stand(ctx, null))
                .then(Commands.literal("stand")
                        .executes(ctx -> stand(ctx, null))
                        .then(Commands.argument("nation", StringArgumentType.string()).suggests(CommandUtil.NATIONS)
                                .executes(ctx -> stand(ctx, StringArgumentType.getString(ctx, "nation")))))
                .then(Commands.literal("aufnehmen")
                        .then(Commands.argument("betrag", DoubleArgumentType.doubleArg(1))
                                .executes(LoanCommand::aufnehmen)))
                .then(Commands.literal("tilgen")
                        .then(Commands.argument("betrag", DoubleArgumentType.doubleArg(0.01))
                                .executes(LoanCommand::tilgen)))
                .then(Commands.literal("bedingungen").executes(LoanCommand::bedingungen)));
    }

    private static int bedingungen(CommandContext<CommandSourceStack> ctx) {
        Msg.info(ctx.getSource(), "Zentralbank: Zins " + CrownsConfig.DARLEHEN_ZINS_PROZENT.get() + " % auf die Summe, Laufzeit "
                + CrownsConfig.DARLEHEN_WOCHEN.get() + " Wochen, eine Rate pro Woche aus der Staatskasse. Hoechstschuld je Nation: "
                + Msg.money(CrownsConfig.DARLEHEN_MAX.get()) + ". Bei Verzug wird es oeffentlich.");
        return 1;
    }

    private static int stand(CommandContext<CommandSourceStack> ctx, String arg) throws CommandSyntaxException {
        CrownsData d = CommandUtil.data(ctx);
        Nation n;
        if (arg == null) {
            ServerPlayer p = CommandUtil.player(ctx);
            n = CommandUtil.own(ctx.getSource(), d, p);
        } else {
            n = CommandUtil.nation(ctx.getSource(), d, arg);
        }
        if (n == null) return 0;
        Treasury t = d.treasury(n.key());
        if (t.darlehen.isEmpty()) {
            Msg.info(ctx.getSource(), n.name + " hat keine Darlehen.");
            return 1;
        }
        Msg.info(ctx.getSource(), "Darlehen von " + n.name + " (Gesamtschuld " + Msg.money(t.schulden()) + "):");
        for (Treasury.Loan l : t.darlehen) {
            Msg.line(ctx.getSource(), " §6#" + l.id + "§f " + Msg.money(l.betrag) + " aufgenommen, Rest " + Msg.money(l.rest)
                    + ", Rate " + Msg.money(l.rate) + " faellig " + TimeUtil.fmtShort(l.naechsteRate) + (l.verzug ? " §cIM VERZUG" : ""));
        }
        return 1;
    }

    private static int aufnehmen(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        CrownsData d = CommandUtil.data(ctx);
        Nation n = CommandUtil.own(ctx.getSource(), d, p);
        if (n == null) return 0;
        if (!CommandUtil.chef(ctx.getSource(), n, p)) return 0;
        Treasury t = d.treasury(n.key());
        double betrag = Math.round(DoubleArgumentType.getDouble(ctx, "betrag") * 100) / 100.0;
        if (t.imVerzug()) {
            Msg.err(ctx.getSource(), "Solange eine Rate offen ist, gibt die Zentralbank nichts Neues.");
            return 0;
        }
        double max = CrownsConfig.DARLEHEN_MAX.get();
        if (t.schulden() + betrag > max) {
            Msg.err(ctx.getSource(), "Hoechstschuld " + Msg.money(max) + " wuerde ueberschritten (aktuell " + Msg.money(t.schulden()) + ").");
            return 0;
        }
        Treasury.Loan l = LoanService.take(d, n, betrag);
        Msg.broadcast(ctx.getSource().getServer(), n.name + " nimmt bei der Zentralbank ein Darlehen ueber " + Msg.money(betrag) + " auf.");
        Msg.ok(ctx.getSource(), "Darlehen #" + l.id + ": " + Msg.money(betrag) + " gutgeschrieben, zurueckzuzahlen " + Msg.money(l.gesamt)
                + " in " + l.wochen + " Raten zu " + Msg.money(l.rate) + ", erste Rate am " + TimeUtil.fmt(l.naechsteRate) + ".");
        DiscordWebhook.news(":bank: **" + n.name + "** nimmt ein Darlehen ueber " + Msg.money(betrag) + " auf (Rueckzahlung "
                + Msg.money(l.gesamt) + " in " + l.wochen + " Wochen).");
        return 1;
    }

    private static int tilgen(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        CrownsData d = CommandUtil.data(ctx);
        Nation n = CommandUtil.own(ctx.getSource(), d, p);
        if (n == null) return 0;
        if (!CommandUtil.chef(ctx.getSource(), n, p)) return 0;
        Treasury t = d.treasury(n.key());
        if (t.darlehen.isEmpty()) {
            Msg.err(ctx.getSource(), "Keine Darlehen offen.");
            return 0;
        }
        double betrag = Math.min(Math.round(DoubleArgumentType.getDouble(ctx, "betrag") * 100) / 100.0, t.schulden());
        if (!TreasuryService.charge(d, n.key(), betrag)) {
            Msg.err(ctx.getSource(), "Staatskasse nicht gedeckt. Stand: " + Msg.money(t.stand));
            return 0;
        }
        double rest = betrag;
        var it = t.darlehen.iterator();
        while (it.hasNext() && rest > 0) {
            Treasury.Loan l = it.next();
            double z = Math.min(rest, l.rest);
            l.rest = Math.round((l.rest - z) * 100) / 100.0;
            rest -= z;
            if (l.rest <= 0.004) {
                it.remove();
                Msg.broadcast(ctx.getSource().getServer(), n.name + " hat Darlehen #" + l.id + " vollstaendig getilgt.");
            } else if (l.verzug) {
                l.verzug = false;
                l.verzugRaten = 0;
            }
        }
        d.setDirty();
        Msg.ok(ctx.getSource(), Msg.money(betrag) + " getilgt. Restschuld " + Msg.money(t.schulden()) + ".");
        return 1;
    }
}
