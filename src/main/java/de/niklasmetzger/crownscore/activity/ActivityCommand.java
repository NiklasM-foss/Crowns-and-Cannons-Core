package de.niklasmetzger.crownscore.activity;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.niklasmetzger.crownscore.config.CrownsConfig;
import de.niklasmetzger.crownscore.data.ActivityRecord;
import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.discord.DiscordWebhook;
import de.niklasmetzger.crownscore.util.CommandUtil;
import de.niklasmetzger.crownscore.util.Msg;
import de.niklasmetzger.crownscore.util.TimeUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** /aktivitaet und /pause. */
public final class ActivityCommand {
    private ActivityCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("aktivitaet")
                .executes(ctx -> zeigen(ctx, null))
                .then(Commands.literal("bericht").executes(ActivityCommand::bericht))
                .then(Commands.argument("spieler", GameProfileArgument.gameProfile())
                        .executes(ctx -> zeigen(ctx, GameProfileArgument.getGameProfiles(ctx, "spieler").iterator().next()))));
        d.register(Commands.literal("pause")
                .then(Commands.argument("spieler", GameProfileArgument.gameProfile())
                        .then(Commands.argument("tage", IntegerArgumentType.integer(0, 365))
                                .executes(ActivityCommand::pause))));
    }

    private static int zeigen(CommandContext<CommandSourceStack> ctx, GameProfile gp) throws CommandSyntaxException {
        CrownsData d = CommandUtil.data(ctx);
        UUID id;
        String name;
        if (gp == null) {
            ServerPlayer p = CommandUtil.player(ctx);
            id = p.getUUID();
            name = p.getGameProfile().getName();
        } else {
            if (!CommandUtil.admin(ctx.getSource())) return 0;
            id = gp.getId();
            name = gp.getName();
            if (!bekannt(ctx, d, gp)) return 0;
        }
        ActivityRecord r = d.activity.get(id);
        long now = TimeUtil.now();
        String week = TimeUtil.weekKey(now);
        String prev = TimeUtil.previousWeekKey(now);
        double minWoche = CrownsConfig.AKTIV_WOCHE_STUNDEN.get();
        double minWe = CrownsConfig.AKTIV_WOCHENENDE_STUNDEN.get();
        Msg.info(ctx.getSource(), "Aktivitaet " + name + " (Ziel " + minWoche + " h/Woche, " + minWe + " h am Wochenende):");
        for (String w : new String[]{week, prev}) {
            long ges = r == null ? 0 : r.gesamt(w);
            long we = r == null ? 0 : r.wochenende(w);
            String status = (ges >= minWoche * 3600 && we >= minWe * 3600) ? "§aerfuellt" : "§coffen";
            Msg.line(ctx.getSource(), " §7" + w + ": §f" + Msg.hours(ges) + " gesamt, " + Msg.hours(we) + " Wochenende " + status);
        }
        if (r != null && r.pauseBis > now) Msg.line(ctx.getSource(), " §ePause angemeldet bis " + TimeUtil.fmt(r.pauseBis));
        return 1;
    }

    /** Spieler war schon einmal auf dem Server (Profil-Cache oder Aktivitaet), sonst Fehlermeldung. */
    private static boolean bekannt(CommandContext<CommandSourceStack> ctx, CrownsData d, GameProfile gp) {
        if (d.activity.containsKey(gp.getId())) return true;
        if (ctx.getSource().getServer().getPlayerList().getPlayer(gp.getId()) != null) return true;
        // Der Profil-Cache kennt auch erfundene Namen (Offline-Modus), deshalb die Spielerdaten der Welt pruefen
        java.nio.file.Path dat = ctx.getSource().getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR)
                .resolve(gp.getId() + ".dat");
        if (java.nio.file.Files.exists(dat)) return true;
        Msg.err(ctx.getSource(), gp.getName() + " war noch nie auf diesem Server.");
        return false;
    }

    private static int bericht(CommandContext<CommandSourceStack> ctx) {
        if (!CommandUtil.admin(ctx.getSource())) return 0;
        long now = TimeUtil.now();
        String report = ActivityService.report(ctx.getSource().getServer(), TimeUtil.weekKey(now), now);
        for (String line : report.split("\n")) {
            Msg.line(ctx.getSource(), line.replace("**", "").replaceAll(":[a-z_]+:", "").trim());
        }
        DiscordWebhook.aktivitaet(report);
        Msg.ok(ctx.getSource(), "Bericht an Discord geschickt (falls Webhook gesetzt).");
        return 1;
    }

    private static int pause(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!CommandUtil.admin(ctx.getSource())) return 0;
        CrownsData d = CommandUtil.data(ctx);
        GameProfile gp = GameProfileArgument.getGameProfiles(ctx, "spieler").iterator().next();
        int tage = IntegerArgumentType.getInteger(ctx, "tage");
        if (!bekannt(ctx, d, gp)) return 0;
        ActivityRecord r = d.activity.computeIfAbsent(gp.getId(), k -> new ActivityRecord());
        if (r.name.isEmpty()) r.name = gp.getName();
        r.pauseBis = tage == 0 ? 0 : TimeUtil.now() + tage * 86_400_000L;
        d.setDirty();
        Msg.ok(ctx.getSource(), tage == 0 ? "Pause von " + gp.getName() + " beendet."
                : gp.getName() + " ist bis " + TimeUtil.fmt(r.pauseBis) + " von der Aktivitaetsregel befreit.");
        return 1;
    }
}
