package de.niklasmetzger.crownscore.mode;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.niklasmetzger.crownscore.config.CrownsConfig;
import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.EvidenceEntry;
import de.niklasmetzger.crownscore.util.CommandUtil;
import de.niklasmetzger.crownscore.util.Durations;
import de.niklasmetzger.crownscore.util.Msg;
import de.niklasmetzger.crownscore.util.TimeUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** /modus: RP, Farm oder AFK mit Parkscheibe, dazu /modus grund fuer Angriffe auf Geschuetzte. */
public final class ModeCommand {
    private ModeCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("modus")
                .executes(ModeCommand::zeigen)
                .then(Commands.literal("rp").executes(ctx -> setzen(ctx, PlayerMode.RP, null)))
                .then(Commands.literal("farm").executes(ctx -> setzen(ctx, PlayerMode.FARM, null)))
                .then(Commands.literal("afk")
                        .then(Commands.argument("bis", StringArgumentType.greedyString())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("30m", "1h", "2h", "20:00"), b))
                                .executes(ctx -> setzen(ctx, PlayerMode.AFK, StringArgumentType.getString(ctx, "bis")))))
                .then(Commands.literal("liste").executes(ModeCommand::liste))
                .then(Commands.literal("grund")
                        .then(Commands.argument("spieler", EntityArgument.player())
                                .then(Commands.argument("grund", StringArgumentType.greedyString())
                                        .executes(ModeCommand::grund)))));
    }

    private static int zeigen(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        ModeService.State s = ModeService.state(p.getUUID());
        Msg.info(ctx.getSource(), "Dein Modus: " + ModeService.anzeige(s) + " §7(seit " + ModeService.uhrzeit(s.seit) + ")");
        for (PlayerMode m : PlayerMode.values()) {
            Msg.line(ctx.getSource(), " " + m.color + m.label + "§7: " + m.info);
        }
        Msg.line(ctx.getSource(), " §e/modus rp§7, §e/modus farm§7, §e/modus afk <20:00|90m>§7, §e/modus liste");
        return 1;
    }

    /** Liest "20:00" (heute oder morgen) oder eine Dauer wie 90m, 2h, 1h30m oder 45. */
    static long parseBis(String s) {
        long now = TimeUtil.now();
        if (s.matches("\\d{1,2}[:.]\\d{2}")) {
            String[] p = s.split("[:.]");
            int h = Integer.parseInt(p[0]);
            int m = Integer.parseInt(p[1]);
            if (h > 23 || m > 59) return -1;
            ZonedDateTime z = TimeUtil.zoned(now).with(LocalTime.of(h, m));
            if (!z.toInstant().isAfter(java.time.Instant.ofEpochMilli(now))) z = z.plusDays(1);
            return z.toInstant().toEpochMilli();
        }
        long d = Durations.parse(s);
        return d <= 0 ? -1 : now + d;
    }

    private static int setzen(CommandContext<CommandSourceStack> ctx, PlayerMode mode, String bisText) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        long sperre = ModeService.kampfSperre(p.getUUID());
        if (mode.geschuetzt() && sperre > 0) {
            Msg.err(ctx.getSource(), "Du warst gerade im Kampf. Moduswechsel erst in " + sperre + " Sekunden.");
            return 0;
        }
        long bis = 0;
        if (mode == PlayerMode.AFK) {
            bis = parseBis(bisText);
            if (bis < 0) {
                Msg.err(ctx.getSource(), "Gib an, bis wann du AFK bist: eine Uhrzeit wie 20:00 oder eine Dauer wie 90m oder 2h.");
                return 0;
            }
            long max = CrownsConfig.MODUS_AFK_MAX_STUNDEN.get() * 3_600_000L;
            if (bis - TimeUtil.now() > max) {
                Msg.err(ctx.getSource(), "Hoechstens " + CrownsConfig.MODUS_AFK_MAX_STUNDEN.get() + " Stunden AFK am Stueck.");
                return 0;
            }
        }
        ModeService.set(p, mode, bis);
        String txt = switch (mode) {
            case RP -> "Du bist im §aRP-Modus§r. Du kannst nach den normalen Kriegsregeln angegriffen werden.";
            case FARM -> "Du bist im §eFarm-Modus§r. Angreifen darf dich nur, wer einen triftigen Grund angibt.";
            case AFK -> "Du bist §7AFK bis " + ModeService.uhrzeit(bis) + "§r (" + Durations.human(bis - TimeUtil.now())
                    + "). Danach geht es automatisch zurueck in den RP-Modus.";
        };
        Msg.info(ctx.getSource(), txt);
        return 1;
    }

    private static int liste(CommandContext<CommandSourceStack> ctx) {
        List<ServerPlayer> players = new ArrayList<>(ctx.getSource().getServer().getPlayerList().getPlayers());
        players.sort(Comparator.comparing((ServerPlayer p) -> ModeService.mode(p.getUUID())).thenComparing(p -> p.getGameProfile().getName()));
        Msg.info(ctx.getSource(), "Modus der Spieler online (" + players.size() + "):");
        for (ServerPlayer p : players) {
            Msg.line(ctx.getSource(), " §f" + p.getGameProfile().getName() + " §7" + ModeService.anzeige(ModeService.state(p.getUUID())));
        }
        return players.size();
    }

    private static int grund(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        ServerPlayer ziel = EntityArgument.getPlayer(ctx, "spieler");
        String grund = StringArgumentType.getString(ctx, "grund").trim();
        if (ziel == p) {
            Msg.err(ctx.getSource(), "Gegen dich selbst brauchst du keinen Grund.");
            return 0;
        }
        if (grund.length() < 10) {
            Msg.err(ctx.getSource(), "Der Grund muss nachvollziehbar sein, mindestens 10 Zeichen. Er landet in der Akte.");
            return 0;
        }
        ModeService.grund(p.getUUID(), ziel.getUUID());
        CrownsData d = CommandUtil.data(ctx);
        EvidenceEntry e = new EvidenceEntry();
        e.zeit = TimeUtil.now();
        e.typ = "ANGRIFFSGRUND";
        e.taeter = p.getUUID();
        e.taeterName = p.getGameProfile().getName();
        String kn = d.nationKeyOf(p.getUUID());
        e.taeterNation = kn == null ? "" : d.anzeige(kn);
        e.dim = p.level().dimension().location().toString();
        e.x = p.blockPosition().getX();
        e.y = p.blockPosition().getY();
        e.z = p.blockPosition().getZ();
        e.detail = "gegen " + ziel.getGameProfile().getName() + " (" + ModeService.mode(ziel.getUUID()).label + "): " + grund;
        d.addEvidence(e);
        Msg.ok(ctx.getSource(), "Grund gegen " + ziel.getGameProfile().getName() + " eingetragen. Er gilt "
                + CrownsConfig.MODUS_GRUND_MINUTEN.get() + " Minuten und steht in der Akte.");
        return 1;
    }
}
