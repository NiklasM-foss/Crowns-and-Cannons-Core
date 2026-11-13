package de.niklasmetzger.crownscore.visa;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.niklasmetzger.crownscore.config.CrownsConfig;
import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.Nation;
import de.niklasmetzger.crownscore.data.Visa;
import de.niklasmetzger.crownscore.util.CommandUtil;
import de.niklasmetzger.crownscore.util.Msg;
import de.niklasmetzger.crownscore.util.TimeUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.server.level.ServerPlayer;

/** /visum: Einreiseerlaubnis fuer fremde Spieler. */
public final class VisaCommand {
    private VisaCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("visum")
                .then(Commands.literal("erteilen")
                        .then(Commands.argument("spieler", GameProfileArgument.gameProfile())
                                .executes(ctx -> erteilen(ctx, CrownsConfig.VISUM_STANDARD_TAGE.get()))
                                .then(Commands.argument("tage", IntegerArgumentType.integer(1, 3650))
                                        .executes(ctx -> erteilen(ctx, IntegerArgumentType.getInteger(ctx, "tage"))))))
                .then(Commands.literal("entziehen")
                        .then(Commands.argument("spieler", GameProfileArgument.gameProfile())
                                .executes(VisaCommand::entziehen)))
                .then(Commands.literal("liste").executes(VisaCommand::liste))
                .then(Commands.literal("meine").executes(VisaCommand::meine)));
    }

    private static int erteilen(CommandContext<CommandSourceStack> ctx, int tage) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        CrownsData d = CommandUtil.data(ctx);
        Nation n = CommandUtil.own(ctx.getSource(), d, p);
        if (n == null) return 0;
        if (!CommandUtil.beamter(ctx.getSource(), n, p)) return 0;
        GameProfile gp = GameProfileArgument.getGameProfiles(ctx, "spieler").iterator().next();
        if (n.isMember(gp.getId())) {
            Msg.err(ctx.getSource(), gp.getName() + " ist Buerger von " + n.name + " und braucht kein Visum.");
            return 0;
        }
        d.visas.removeIf(v -> v.spieler.equals(gp.getId()) && v.nation.equals(n.key()));
        Visa v = new Visa();
        v.spieler = gp.getId();
        v.spielerName = gp.getName();
        v.nation = n.key();
        v.erteilt = TimeUtil.now();
        v.bis = v.erteilt + tage * 86_400_000L;
        v.erteiltVon = p.getUUID();
        d.visas.add(v);
        d.setDirty();
        Msg.ok(ctx.getSource(), "Visum fuer " + gp.getName() + " bis " + TimeUtil.fmt(v.bis) + " erteilt.");
        ServerPlayer target = ctx.getSource().getServer().getPlayerList().getPlayer(gp.getId());
        if (target != null) Msg.player(target, "§a" + n.name + " hat dir ein Visum bis " + TimeUtil.fmt(v.bis) + " erteilt.");
        return 1;
    }

    private static int entziehen(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        CrownsData d = CommandUtil.data(ctx);
        Nation n = CommandUtil.own(ctx.getSource(), d, p);
        if (n == null) return 0;
        if (!CommandUtil.beamter(ctx.getSource(), n, p)) return 0;
        GameProfile gp = GameProfileArgument.getGameProfiles(ctx, "spieler").iterator().next();
        boolean removed = d.visas.removeIf(v -> v.spieler.equals(gp.getId()) && v.nation.equals(n.key()));
        d.setDirty();
        if (!removed) {
            Msg.err(ctx.getSource(), gp.getName() + " hat kein Visum fuer " + n.name + ".");
            return 0;
        }
        Msg.ok(ctx.getSource(), "Visum von " + gp.getName() + " entzogen.");
        ServerPlayer target = ctx.getSource().getServer().getPlayerList().getPlayer(gp.getId());
        if (target != null) Msg.player(target, "§c" + n.name + " hat dein Visum entzogen.");
        return 1;
    }

    private static int liste(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        CrownsData d = CommandUtil.data(ctx);
        Nation n = CommandUtil.own(ctx.getSource(), d, p);
        if (n == null) return 0;
        long now = TimeUtil.now();
        d.visas.removeIf(v -> !v.gueltig(now));
        Msg.info(ctx.getSource(), "Gueltige Visa fuer " + n.name + ":");
        int c = 0;
        for (Visa v : d.visas) {
            if (!v.nation.equals(n.key())) continue;
            c++;
            Msg.line(ctx.getSource(), " §7" + v.spielerName + " bis " + TimeUtil.fmt(v.bis));
        }
        if (c == 0) Msg.line(ctx.getSource(), " §7keine");
        return c;
    }

    private static int meine(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        CrownsData d = CommandUtil.data(ctx);
        long now = TimeUtil.now();
        Msg.info(ctx.getSource(), "Deine Visa:");
        int c = 0;
        for (Visa v : d.visas) {
            if (!v.spieler.equals(p.getUUID()) || !v.gueltig(now)) continue;
            c++;
            Msg.line(ctx.getSource(), " §7" + d.anzeige(v.nation) + " bis " + TimeUtil.fmt(v.bis));
        }
        if (c == 0) Msg.line(ctx.getSource(), " §7keine");
        return c;
    }
}
