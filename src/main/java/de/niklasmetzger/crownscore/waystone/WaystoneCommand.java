package de.niklasmetzger.crownscore.waystone;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.niklasmetzger.crownscore.config.CrownsConfig;
import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.Nation;
import de.niklasmetzger.crownscore.util.CommandUtil;
import de.niklasmetzger.crownscore.util.Msg;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/** /wegstein: Freigaben fuer fremde Nationen verwalten. */
public final class WaystoneCommand {
    private WaystoneCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("wegstein")
                .then(Commands.literal("liste").executes(WaystoneCommand::liste))
                .then(Commands.literal("freigeben")
                        .then(Commands.argument("nation", StringArgumentType.string()).suggests(CommandUtil.NATIONS)
                                .executes(ctx -> setzen(ctx, true))))
                .then(Commands.literal("entziehen")
                        .then(Commands.argument("nation", StringArgumentType.string()).suggests(CommandUtil.NATIONS)
                                .executes(ctx -> setzen(ctx, false)))));
    }

    private static int liste(CommandContext<CommandSourceStack> ctx) {
        CrownsData d = CommandUtil.data(ctx);
        Msg.info(ctx.getSource(), "Wegstein-Freigaben" + (CrownsConfig.WEGSTEIN_ZOLL.get() > 0 ? " (Zoll " + Msg.money(CrownsConfig.WEGSTEIN_ZOLL.get()) + " je Nutzung)" : "") + ":");
        for (Nation n : d.nations.values()) {
            List<String> frei = new ArrayList<>();
            for (String k : n.wegsteinFreigaben) frei.add(d.anzeige(k));
            Msg.line(ctx.getSource(), " §6" + n.name + "§7 oeffnet fuer: §f" + (frei.isEmpty() ? "niemanden" : String.join(", ", frei)));
        }
        return 1;
    }

    private static int setzen(CommandContext<CommandSourceStack> ctx, boolean frei) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        CrownsData d = CommandUtil.data(ctx);
        Nation n = CommandUtil.own(ctx.getSource(), d, p);
        if (n == null) return 0;
        if (!CommandUtil.beamter(ctx.getSource(), n, p)) return 0;
        Nation other = CommandUtil.nation(ctx.getSource(), d, StringArgumentType.getString(ctx, "nation"));
        if (other == null) return 0;
        if (frei) {
            n.wegsteinFreigaben.add(other.key());
            Msg.ok(ctx.getSource(), other.name + " darf jetzt die Wegsteine von " + n.name + " benutzen.");
            Msg.nation(ctx.getSource().getServer(), other, "§a" + n.name + " hat euch seine Wegsteine freigegeben.");
        } else {
            n.wegsteinFreigaben.remove(other.key());
            Msg.ok(ctx.getSource(), other.name + " darf die Wegsteine von " + n.name + " nicht mehr benutzen.");
            Msg.nation(ctx.getSource().getServer(), other, "§c" + n.name + " hat euch die Wegstein-Freigabe entzogen.");
        }
        d.setDirty();
        return 1;
    }
}
