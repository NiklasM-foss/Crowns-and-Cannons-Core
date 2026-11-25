package de.niklasmetzger.crownscore.discord;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.util.CommandUtil;
import de.niklasmetzger.crownscore.util.Msg;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/** /discord: Konto verknuepfen oder trennen, Status und Neustart des Bots fuer Admins. */
public final class DiscordCommand {
    private DiscordCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("discord")
                .executes(DiscordCommand::status)
                .then(Commands.literal("verknuepfen").then(Commands.argument("code", StringArgumentType.word()).executes(DiscordCommand::verknuepfen)))
                .then(Commands.literal("trennen").executes(DiscordCommand::trennen))
                .then(Commands.literal("neustart").executes(DiscordCommand::neustart)));
    }

    private static int verknuepfen(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        String err = DiscordBot.verknuepfen(ctx.getSource().getServer(), p, StringArgumentType.getString(ctx, "code"));
        if (err != null && !err.startsWith("!")) {
            Msg.err(ctx.getSource(), err);
            return 0;
        }
        Msg.ok(ctx.getSource(), "Verknuepft" + (err == null ? "" : " mit dem Discord-Konto " + err.substring(1))
                + ". Rolle und Name werden in den naechsten Minuten angepasst. Falsches Konto? /discord trennen");
        return 1;
    }

    private static int trennen(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        boolean weg = DiscordBot.trennen(ctx.getSource().getServer(), p.getUUID());
        if (weg) Msg.ok(ctx.getSource(), "Verknuepfung mit Discord geloest.");
        else Msg.err(ctx.getSource(), "Dein Konto war nicht mit Discord verknuepft.");
        return weg ? 1 : 0;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        CrownsData d = CommandUtil.data(ctx);
        boolean ich = ctx.getSource().getEntity() instanceof ServerPlayer p && d.discord.containsValue(p.getUUID());
        Msg.info(ctx.getSource(), "Discord-Bot: " + (DiscordBot.aktiv() ? "verbunden" : "nicht verbunden") + ", " + d.discord.size() + " verknuepfte Konten.");
        Msg.line(ctx.getSource(), ich ? " §7Dein Konto ist verknuepft (/discord trennen loest es)."
                : " §7Verknuepfen: im Discord /verknuepfen, dann hier /discord verknuepfen <Code>.");
        return 1;
    }

    private static int neustart(CommandContext<CommandSourceStack> ctx) {
        if (!CommandUtil.admin(ctx.getSource())) return 0;
        DiscordBot.start(ctx.getSource().getServer());
        Msg.ok(ctx.getSource(), DiscordBot.gestartet() ? "Discord-Bot startet neu (Config neu gelesen). /discord zeigt in ein paar Sekunden, ob er verbunden ist."
                : "Discord-Bot ist aus (discordbot.aktiv, token und server pruefen).");
        return 1;
    }
}
