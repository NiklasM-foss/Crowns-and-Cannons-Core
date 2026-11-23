package de.niklasmetzger.crownscore.war;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.niklasmetzger.crownscore.util.CommandUtil;
import de.niklasmetzger.crownscore.util.Msg;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

/** /duell: Zweikampf ohne Krieg, beide muessen zustimmen. */
public final class DuelCommand {
    private DuelCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("duell")
                .then(Commands.literal("annehmen").executes(DuelCommand::annehmen))
                .then(Commands.literal("abbrechen").executes(DuelCommand::abbrechen))
                .then(Commands.argument("spieler", EntityArgument.player()).executes(DuelCommand::fordern)));
    }

    private static int fordern(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        ServerPlayer target = EntityArgument.getPlayer(ctx, "spieler");
        if (target == p) {
            Msg.err(ctx.getSource(), "Gegen dich selbst?");
            return 0;
        }
        if (DuelManager.busy(p.getUUID()) || DuelManager.busy(target.getUUID())) {
            Msg.err(ctx.getSource(), "Einer von euch ist schon in einem Duell.");
            return 0;
        }
        if (WarService.inSpawnZone(p) || WarService.inSpawnZone(target)) {
            Msg.err(ctx.getSource(), "Am Spawn gibt es keine Duelle.");
            return 0;
        }
        DuelManager.request(p, target);
        return 1;
    }

    private static int annehmen(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return DuelManager.accept(CommandUtil.player(ctx)) ? 1 : 0;
    }

    private static int abbrechen(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        if (!DuelManager.cancel(p)) Msg.err(ctx.getSource(), "Du bist in keinem Duell und hast keine offene Herausforderung.");
        return 1;
    }
}
