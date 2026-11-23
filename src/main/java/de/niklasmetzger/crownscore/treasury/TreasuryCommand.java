package de.niklasmetzger.crownscore.treasury;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.Nation;
import de.niklasmetzger.crownscore.data.Treasury;
import de.niklasmetzger.crownscore.util.Perms;
import de.niklasmetzger.crownscore.economy.Economy;
import de.niklasmetzger.crownscore.util.CommandUtil;
import de.niklasmetzger.crownscore.util.Msg;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

/** /staatskasse: Einzahlen, Auszahlen, Ueberweisen. */
public final class TreasuryCommand {
    private TreasuryCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("staatskasse")
                .executes(ctx -> stand(ctx, null))
                .then(Commands.literal("stand")
                        .executes(ctx -> stand(ctx, null))
                        .then(Commands.argument("nation", StringArgumentType.string()).suggests(CommandUtil.NATIONS)
                                .executes(ctx -> stand(ctx, StringArgumentType.getString(ctx, "nation")))))
                .then(Commands.literal("einzahlen")
                        .then(Commands.argument("betrag", DoubleArgumentType.doubleArg(0.01))
                                .executes(TreasuryCommand::einzahlen)))
                .then(Commands.literal("auszahlen")
                        .then(Commands.argument("betrag", DoubleArgumentType.doubleArg(0.01))
                                .executes(TreasuryCommand::auszahlen)))
                .then(Commands.literal("ueberweisen")
                        .then(Commands.argument("nation", StringArgumentType.string()).suggests(CommandUtil.NATIONS)
                                .then(Commands.argument("betrag", DoubleArgumentType.doubleArg(0.01))
                                        .executes(TreasuryCommand::ueberweisen))))
                .then(Commands.literal("setzen")
                        .then(Commands.argument("nation", StringArgumentType.string()).suggests(CommandUtil.NATIONS)
                                .then(Commands.argument("betrag", DoubleArgumentType.doubleArg(0))
                                        .executes(TreasuryCommand::setzen))))
                .then(Commands.literal("geldgeben")
                        .then(Commands.argument("spieler", EntityArgument.player())
                                .then(Commands.argument("betrag", DoubleArgumentType.doubleArg(0.01))
                                        .executes(TreasuryCommand::geldgeben))))
                .then(Commands.literal("konto").executes(TreasuryCommand::konto)));
    }

    private static int stand(CommandContext<CommandSourceStack> ctx, String arg) throws CommandSyntaxException {
        CrownsData d = CommandUtil.data(ctx);
        if (arg == null && !Perms.isAdmin(ctx.getSource())) {
            Nation eigene = ctx.getSource().getEntity() instanceof ServerPlayer sp ? d.nationOf(sp.getUUID()) : null;
            if (eigene == null) {
                Msg.err(ctx.getSource(), "Du gehoerst keiner Nation an. /staatskasse stand <Nation>");
                return 0;
            }
            arg = eigene.key();
        }
        if (arg == null) {
            Msg.info(ctx.getSource(), "Staatskassen:");
            for (Nation n : d.nations.values()) {
                Treasury t = d.treasury(n.key());
                Msg.line(ctx.getSource(), " §6" + n.anzeige() + "§f " + Msg.money(t.stand)
                        + (t.schulden() > 0 ? " §c(Schulden " + Msg.money(t.schulden()) + (t.imVerzug() ? ", IM VERZUG" : "") + ")" : ""));
            }
            return 1;
        }
        Nation n = CommandUtil.nation(ctx.getSource(), d, arg);
        if (n == null) return 0;
        Treasury t = d.treasury(n.key());
        Msg.info(ctx.getSource(), "Staatskasse " + n.anzeige() + ": " + Msg.money(t.stand)
                + (t.schulden() > 0 ? " (Schulden " + Msg.money(t.schulden()) + ")" : ""));
        return 1;
    }

    private static int einzahlen(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        CrownsData d = CommandUtil.data(ctx);
        Nation n = CommandUtil.own(ctx.getSource(), d, p);
        if (n == null) return 0;
        double betrag = Math.round(DoubleArgumentType.getDouble(ctx, "betrag") * 100) / 100.0;
        if (!Economy.get().withdraw(p, betrag)) {
            Msg.err(ctx.getSource(), "Nicht genug Geld. Dein Konto: " + Msg.money(Economy.get().balance(p)));
            return 0;
        }
        TreasuryService.credit(d, n.key(), betrag);
        Msg.ok(ctx.getSource(), Msg.money(betrag) + " in die Staatskasse von " + n.name + " eingezahlt. Stand: " + Msg.money(d.treasury(n.key()).stand));
        Msg.nation(ctx.getSource().getServer(), n, "§7" + p.getGameProfile().getName() + " hat " + Msg.money(betrag) + " in die Staatskasse eingezahlt.");
        return 1;
    }

    private static int auszahlen(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        CrownsData d = CommandUtil.data(ctx);
        Nation n = CommandUtil.own(ctx.getSource(), d, p);
        if (n == null) return 0;
        if (!CommandUtil.chef(ctx.getSource(), n, p)) return 0;
        double betrag = Math.round(DoubleArgumentType.getDouble(ctx, "betrag") * 100) / 100.0;
        Treasury kasse = d.treasury(n.key());
        if (kasse.imVerzug()) {
            Msg.err(ctx.getSource(), "Im Zahlungsverzug ist die Staatskasse fuer Auszahlungen gesperrt. Erst die faellige Rate tilgen.");
            return 0;
        }
        double frei = Math.max(0, kasse.stand - kasse.schulden());
        if (betrag > frei + 1e-9) {
            Msg.err(ctx.getSource(), "Geliehenes Geld bleibt in der Staatskasse. Auszahlbar sind nur " + Msg.money(frei)
                    + " (Stand " + Msg.money(kasse.stand) + " minus Schulden " + Msg.money(kasse.schulden()) + ").");
            return 0;
        }
        if (!TreasuryService.charge(d, n.key(), betrag)) {
            Msg.err(ctx.getSource(), "Staatskasse nicht gedeckt. Stand: " + Msg.money(d.treasury(n.key()).stand));
            return 0;
        }
        Economy.get().deposit(p, betrag);
        Msg.ok(ctx.getSource(), Msg.money(betrag) + " aus der Staatskasse entnommen. Stand: " + Msg.money(d.treasury(n.key()).stand));
        Msg.nation(ctx.getSource().getServer(), n, "§7Staatschef " + p.getGameProfile().getName() + " hat " + Msg.money(betrag) + " aus der Staatskasse entnommen.");
        return 1;
    }

    private static int ueberweisen(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        CrownsData d = CommandUtil.data(ctx);
        Nation n = CommandUtil.own(ctx.getSource(), d, p);
        if (n == null) return 0;
        if (!CommandUtil.chef(ctx.getSource(), n, p)) return 0;
        Nation ziel = CommandUtil.nation(ctx.getSource(), d, StringArgumentType.getString(ctx, "nation"));
        if (ziel == null) return 0;
        double betrag = Math.round(DoubleArgumentType.getDouble(ctx, "betrag") * 100) / 100.0;
        if (!TreasuryService.charge(d, n.key(), betrag)) {
            Msg.err(ctx.getSource(), "Staatskasse nicht gedeckt. Stand: " + Msg.money(d.treasury(n.key()).stand));
            return 0;
        }
        TreasuryService.credit(d, ziel.key(), betrag);
        Msg.broadcast(ctx.getSource().getServer(), n.name + " ueberweist " + Msg.money(betrag) + " an " + ziel.name + ".");
        return 1;
    }

    private static int setzen(CommandContext<CommandSourceStack> ctx) {
        if (!CommandUtil.admin(ctx.getSource())) return 0;
        CrownsData d = CommandUtil.data(ctx);
        Nation n = CommandUtil.nation(ctx.getSource(), d, StringArgumentType.getString(ctx, "nation"));
        if (n == null) return 0;
        d.treasury(n.key()).stand = DoubleArgumentType.getDouble(ctx, "betrag");
        d.setDirty();
        Msg.ok(ctx.getSource(), "Staatskasse " + n.name + " auf " + Msg.money(d.treasury(n.key()).stand) + " gesetzt.");
        return 1;
    }

    private static int geldgeben(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!CommandUtil.admin(ctx.getSource())) return 0;
        ServerPlayer target = EntityArgument.getPlayer(ctx, "spieler");
        double betrag = DoubleArgumentType.getDouble(ctx, "betrag");
        Economy.get().deposit(target, betrag);
        Msg.ok(ctx.getSource(), Msg.money(betrag) + " an " + target.getGameProfile().getName() + " (" + Economy.get().name() + "). Konto: " + Msg.money(Economy.get().balance(target)));
        return 1;
    }

    private static int konto(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        Msg.info(ctx.getSource(), "Dein Konto (" + Economy.get().name() + "): " + Msg.money(Economy.get().balance(p)));
        return 1;
    }
}
