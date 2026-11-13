package de.niklasmetzger.crownscore.war;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.niklasmetzger.crownscore.config.CrownsConfig;
import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.Nation;
import de.niklasmetzger.crownscore.data.War;
import de.niklasmetzger.crownscore.data.WarType;
import de.niklasmetzger.crownscore.util.CommandUtil;
import de.niklasmetzger.crownscore.util.Msg;
import de.niklasmetzger.crownscore.util.TimeUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** /krieg: erklaeren, Frieden, Liste. */
public final class WarCommand {
    private WarCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("krieg")
                .then(Commands.literal("erklaeren")
                        .then(Commands.argument("gegner", StringArgumentType.string()).suggests(CommandUtil.NATIONS)
                                .then(Commands.argument("kriegsart", StringArgumentType.string()).suggests(CommandUtil.WAR_TYPES)
                                        .then(Commands.argument("grund", StringArgumentType.greedyString())
                                                .executes(WarCommand::erklaeren)))))
                .then(Commands.literal("liste").executes(WarCommand::liste))
                .then(Commands.literal("arten").executes(WarCommand::arten))
                .then(Commands.literal("frieden")
                        .then(Commands.argument("gegner", StringArgumentType.string()).suggests(CommandUtil.NATIONS)
                                .executes(WarCommand::frieden)))
                .then(Commands.literal("wechseln")
                        .then(Commands.argument("gegner", StringArgumentType.string()).suggests(CommandUtil.NATIONS)
                                .then(Commands.argument("kriegsart", StringArgumentType.string()).suggests(CommandUtil.WAR_TYPES)
                                        .executes(WarCommand::wechseln))))
                .then(Commands.literal("beenden")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .then(Commands.argument("ergebnis", StringArgumentType.greedyString())
                                        .executes(WarCommand::beenden)))));
    }

    private static int erklaeren(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        CrownsData d = CommandUtil.data(ctx);
        Nation own = CommandUtil.own(ctx.getSource(), d, p);
        if (own == null) return 0;
        if (!CommandUtil.chef(ctx.getSource(), own, p)) return 0;
        Nation gegner = CommandUtil.nation(ctx.getSource(), d, StringArgumentType.getString(ctx, "gegner"));
        if (gegner == null) return 0;
        if (gegner == own) {
            Msg.err(ctx.getSource(), "Einer Nation kann man nicht selbst den Krieg erklaeren.");
            return 0;
        }
        WarType art = WarType.parse(StringArgumentType.getString(ctx, "kriegsart"));
        if (art == null) {
            Msg.err(ctx.getSource(), "Unbekannte Kriegsart. /krieg arten zeigt alle.");
            return 0;
        }
        String grund = StringArgumentType.getString(ctx, "grund").trim();
        if (grund.length() < 10) {
            Msg.err(ctx.getSource(), "Ohne nachvollziehbaren Grund ist eine Kriegserklaerung ungueltig (Regel 4.1). Mindestens 10 Zeichen.");
            return 0;
        }
        if (d.activeWar(own.key(), gegner.key()) != null) {
            Msg.err(ctx.getSource(), "Zwischen " + own.name + " und " + gegner.name + " laeuft schon ein Konflikt. /krieg wechseln fuer eine andere Kriegsart.");
            return 0;
        }
        if (gegner.chef == null && gegner.mitglieder.isEmpty()) {
            Msg.err(ctx.getSource(), gegner.name + " hat keine Spieler. Kein Krieg gegen leere Nationen.");
            return 0;
        }
        WarService.declare(ctx.getSource().getServer(), own, gegner, art, grund, p.getGameProfile().getName());
        return 1;
    }

    private static int liste(CommandContext<CommandSourceStack> ctx) {
        CrownsData d = CommandUtil.data(ctx);
        List<War> aktiv = d.wars.stream().filter(w -> w.aktiv).toList();
        if (aktiv.isEmpty()) {
            Msg.info(ctx.getSource(), "Es herrscht Frieden. Keine laufenden Konflikte.");
        } else {
            Msg.info(ctx.getSource(), "Laufende Konflikte (" + aktiv.size() + "):");
            for (War w : aktiv) {
                String extra = "";
                if (w.art == WarType.BLITZKRIEG && w.ersterKampf == 0) {
                    long rest = w.start + CrownsConfig.BLITZKRIEG_MINUTEN.get() * 60_000L - TimeUtil.now();
                    extra = " §e(Blitzkrieg, noch " + Math.max(0, rest / 60000) + " min fuer den ersten Angriff)";
                }
                if (!w.friedensvorschlag.isEmpty()) extra += " §a(Friedensangebot von " + d.anzeige(w.friedensvorschlag.iterator().next()) + ")";
                Msg.line(ctx.getSource(), " §c#" + w.id + " " + w.art.anzeige + ": §f" + d.anzeige(w.angreifer) + " gegen " + d.anzeige(w.verteidiger)
                        + " §7seit " + TimeUtil.fmtShort(w.start) + ", Grund: " + w.grund + extra);
            }
        }
        List<War> beendet = d.wars.stream().filter(w -> !w.aktiv).toList();
        if (!beendet.isEmpty()) {
            Msg.line(ctx.getSource(), "§7Beendet: " + beendet.size() + " (zuletzt #" + beendet.get(beendet.size() - 1).id + ": "
                    + beendet.get(beendet.size() - 1).ergebnis + ")");
        }
        return aktiv.size();
    }

    private static int arten(CommandContext<CommandSourceStack> ctx) {
        Msg.info(ctx.getSource(), "Kriegsarten (Regel 4.2):");
        for (WarType t : WarType.values()) {
            Msg.line(ctx.getSource(), " §6" + t.name() + "§7 = " + t.anzeige + (t.kampfErlaubt() ? " §c(PvP erlaubt)" : " §a(kein offener Kampf)"));
        }
        return 1;
    }

    private static int frieden(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        CrownsData d = CommandUtil.data(ctx);
        MinecraftServer server = ctx.getSource().getServer();
        Nation own = CommandUtil.own(ctx.getSource(), d, p);
        if (own == null) return 0;
        if (!CommandUtil.chef(ctx.getSource(), own, p)) return 0;
        Nation gegner = CommandUtil.nation(ctx.getSource(), d, StringArgumentType.getString(ctx, "gegner"));
        if (gegner == null) return 0;
        War w = d.activeWar(own.key(), gegner.key());
        if (w == null) {
            Msg.err(ctx.getSource(), "Kein laufender Konflikt mit " + gegner.name + ".");
            return 0;
        }
        w.friedensvorschlag.add(own.key());
        d.setDirty();
        if (w.friedensvorschlag.contains(gegner.key())) {
            WarService.end(server, w, "Frieden, von beiden Seiten bestaetigt", true);
        } else {
            Msg.ok(ctx.getSource(), "Friedensangebot an " + gegner.name + " gesendet. Der Gegner muss mit /krieg frieden " + own.kuerzel + " bestaetigen.");
            Msg.nation(server, gegner, "§a" + own.name + " bietet Frieden im " + w.art.anzeige + " an. Annehmen: /krieg frieden " + own.kuerzel);
        }
        return 1;
    }

    private static int wechseln(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        CrownsData d = CommandUtil.data(ctx);
        MinecraftServer server = ctx.getSource().getServer();
        Nation own = CommandUtil.own(ctx.getSource(), d, p);
        if (own == null) return 0;
        if (!CommandUtil.chef(ctx.getSource(), own, p)) return 0;
        Nation gegner = CommandUtil.nation(ctx.getSource(), d, StringArgumentType.getString(ctx, "gegner"));
        if (gegner == null) return 0;
        WarType art = WarType.parse(StringArgumentType.getString(ctx, "kriegsart"));
        if (art == null) {
            Msg.err(ctx.getSource(), "Unbekannte Kriegsart. /krieg arten zeigt alle.");
            return 0;
        }
        War w = d.activeWar(own.key(), gegner.key());
        if (w == null) {
            Msg.err(ctx.getSource(), "Kein laufender Konflikt mit " + gegner.name + ".");
            return 0;
        }
        // Wechsel braucht beide Seiten: wir merken uns den Wunsch im Friedensvorschlag-Set mit Praefix.
        String wunsch = "wechsel:" + art.name() + ":" + own.key();
        String gegenwunsch = "wechsel:" + art.name() + ":" + gegner.key();
        if (w.friedensvorschlag.contains(gegenwunsch) || ctx.getSource().hasPermission(2)) {
            w.friedensvorschlag.removeIf(s -> s.startsWith("wechsel:"));
            WarType alt = w.art;
            w.art = art;
            w.ersterKampf = 0;
            w.start = TimeUtil.now();
            d.setDirty();
            Msg.broadcast(server, "Der Konflikt zwischen " + own.name + " und " + gegner.name + " ist jetzt ein " + art.anzeige + " (vorher " + alt.anzeige + ").");
            de.niklasmetzger.crownscore.discord.DiscordWebhook.kriegsnews(":arrows_counterclockwise: Konflikt Nr. " + w.id + " zwischen **"
                    + own.name + "** und **" + gegner.name + "**: " + alt.anzeige + " wird zum **" + art.anzeige + "**.");
        } else {
            w.friedensvorschlag.removeIf(s -> s.startsWith("wechsel:") && s.endsWith(":" + own.key()));
            w.friedensvorschlag.add(wunsch);
            d.setDirty();
            Msg.ok(ctx.getSource(), "Wechsel zu " + art.anzeige + " vorgeschlagen. " + gegner.name + " muss mit /krieg wechseln " + own.kuerzel + " " + art.name() + " zustimmen.");
            Msg.nation(server, gegner, "§e" + own.name + " schlaegt vor, den Konflikt in einen " + art.anzeige + " zu wandeln. Zustimmen: /krieg wechseln " + own.kuerzel + " " + art.name());
        }
        return 1;
    }

    private static int beenden(CommandContext<CommandSourceStack> ctx) {
        if (!CommandUtil.admin(ctx.getSource())) return 0;
        CrownsData d = CommandUtil.data(ctx);
        War w = d.warById(IntegerArgumentType.getInteger(ctx, "id"));
        if (w == null || !w.aktiv) {
            Msg.err(ctx.getSource(), "Kein laufender Konflikt mit dieser Nummer.");
            return 0;
        }
        WarService.end(ctx.getSource().getServer(), w, StringArgumentType.getString(ctx, "ergebnis") + " (Entscheidung der Spielleitung)", true);
        return 1;
    }
}
