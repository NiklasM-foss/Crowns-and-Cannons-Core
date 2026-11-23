package de.niklasmetzger.crownscore.mail;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.niklasmetzger.crownscore.claims.Claims;
import de.niklasmetzger.crownscore.config.CrownsConfig;
import de.niklasmetzger.crownscore.util.Perms;
import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.Mail;
import de.niklasmetzger.crownscore.data.Nation;
import de.niklasmetzger.crownscore.data.War;
import de.niklasmetzger.crownscore.util.CommandUtil;
import de.niklasmetzger.crownscore.util.Msg;
import de.niklasmetzger.crownscore.util.TimeUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * /post: Briefe zwischen Nationen. Wer in fremdem Gebiet schreibt, wird am Grenzposten gelesen.
 * Nationen in Spannungen oder Kaltem Krieg fangen mit etwas Glueck Briefe ab.
 */
public final class MailCommand {
    private static final int PAGE = 6;

    private MailCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("post")
                .then(Commands.literal("senden")
                        .then(Commands.argument("nation", StringArgumentType.string()).suggests(CommandUtil.NATIONS)
                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                        .executes(MailCommand::senden))))
                .then(Commands.literal("lesen")
                        .executes(ctx -> lesen(ctx, 1))
                        .then(Commands.argument("seite", IntegerArgumentType.integer(1))
                                .executes(ctx -> lesen(ctx, IntegerArgumentType.getInteger(ctx, "seite")))))
                .then(Commands.literal("abgefangen")
                        .executes(ctx -> abgefangen(ctx, 1))
                        .then(Commands.argument("seite", IntegerArgumentType.integer(1))
                                .executes(ctx -> abgefangen(ctx, IntegerArgumentType.getInteger(ctx, "seite")))))
                .then(Commands.literal("gesendet")
                        .executes(ctx -> gesendet(ctx, 1))
                        .then(Commands.argument("seite", IntegerArgumentType.integer(1))
                                .executes(ctx -> gesendet(ctx, IntegerArgumentType.getInteger(ctx, "seite"))))));
    }

    private static final java.util.Map<java.util.UUID, Long> LETZTER_BRIEF = new java.util.concurrent.ConcurrentHashMap<>();

    private static int senden(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        CrownsData d = CommandUtil.data(ctx);
        MinecraftServer server = ctx.getSource().getServer();
        Nation own = CommandUtil.own(ctx.getSource(), d, p);
        if (own == null) return 0;
        Nation ziel = CommandUtil.nation(ctx.getSource(), d, StringArgumentType.getString(ctx, "nation"));
        if (ziel == null) return 0;
        String text = StringArgumentType.getString(ctx, "text").trim();
        Long zuletzt = LETZTER_BRIEF.get(p.getUUID());
        if (zuletzt != null && System.currentTimeMillis() - zuletzt < 20_000 && !Perms.isAdmin(ctx.getSource())) {
            Msg.err(ctx.getSource(), "Die Post braucht etwas Zeit. Warte ein paar Sekunden bis zum naechsten Brief.");
            return 0;
        }
        if (text.length() > 800) {
            Msg.err(ctx.getSource(), "Ein Brief hat hoechstens 800 Zeichen. Fuer mehr nimm ein Buch.");
            return 0;
        }
        Mail m = new Mail();
        m.id = d.nextMailId++;
        m.von = p.getUUID();
        m.vonName = p.getGameProfile().getName();
        m.vonNation = own.key();
        m.anNation = ziel.key();
        m.text = text;
        m.zeit = TimeUtil.now();

        // Grenzposten: Brief aus fremdem Gebiet heraus geschrieben
        String hier = Claims.nationAt(p.serverLevel(), p.blockPosition());
        if (hier != null && !hier.equals(own.key()) && !hier.equals(ziel.key())) {
            m.abgefangenVon.add(hier);
        }
        // Spionage in Spannungen und Kaltem Krieg
        int chance = CrownsConfig.POST_ABFANG_PROZENT.get();
        for (War w : d.wars) {
            if (!w.aktiv || !w.art.spionage()) continue;
            String spion = null;
            if (w.beteiligt(own.key())) spion = w.gegner(own.key());
            else if (w.beteiligt(ziel.key())) spion = w.gegner(ziel.key());
            if (spion == null || spion.equals(own.key()) || spion.equals(ziel.key())) continue;
            if (ThreadLocalRandom.current().nextInt(100) < chance) m.abgefangenVon.add(spion);
        }
        d.mails.add(m);
        LETZTER_BRIEF.put(p.getUUID(), System.currentTimeMillis());
        // Aufbewahrung: hoechstens 500 Briefe pro Empfaenger-Nation, aelteste zuerst weg
        long anzahl = d.mails.stream().filter(x -> x.anNation.equals(m.anNation)).count();
        for (java.util.Iterator<Mail> it = d.mails.iterator(); anzahl > 500 && it.hasNext(); ) {
            if (it.next().anNation.equals(m.anNation)) {
                it.remove();
                anzahl--;
            }
        }
        d.setDirty();
        Msg.ok(ctx.getSource(), "Brief #" + m.id + " an " + ziel.name + " geschickt.");
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (ziel.isBeamter(sp.getUUID())) Msg.player(sp, "§ePost aus " + own.name + " von " + m.vonName + ". Lesen: /post lesen");
        }
        for (String s : m.abgefangenVon) {
            Msg.nation(server, d.nations.get(s), "§dEure Agenten haben einen Brief von " + own.name + " an " + ziel.name + " abgefangen. /post abgefangen");
        }
        return 1;
    }

    private static int show(CommandContext<CommandSourceStack> ctx, String titel, List<Mail> list, int seite, CrownsData d) {
        if (list.isEmpty()) {
            Msg.info(ctx.getSource(), titel + ": nichts da.");
            return 0;
        }
        Msg.info(ctx.getSource(), titel + " (" + list.size() + ", Seite " + seite + "/" + CommandUtil.pages(list.size(), PAGE) + "):");
        for (Mail m : CommandUtil.page(list, seite, PAGE)) {
            Msg.line(ctx.getSource(), " §6#" + m.id + " §7" + TimeUtil.fmtShort(m.zeit) + " von " + m.vonName + " (" + d.anzeige(m.vonNation)
                    + ") an " + d.anzeige(m.anNation) + ":");
            Msg.line(ctx.getSource(), "   §f" + m.text);
        }
        return list.size();
    }

    private static int lesen(CommandContext<CommandSourceStack> ctx, int seite) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        CrownsData d = CommandUtil.data(ctx);
        Nation own = CommandUtil.own(ctx.getSource(), d, p);
        if (own == null) return 0;
        if (!own.isBeamter(p.getUUID())) {
            Msg.err(ctx.getSource(), "Briefe an " + own.name + " lesen nur Staatschef und Beamte.");
            return 0;
        }
        List<Mail> list = new ArrayList<>();
        for (int i = d.mails.size() - 1; i >= 0; i--) {
            Mail m = d.mails.get(i);
            if (m.anNation.equals(own.key())) {
                list.add(m);
                m.gelesen = true;
            }
        }
        return show(ctx, "Posteingang " + own.name, list, seite, d);
    }

    private static int abgefangen(CommandContext<CommandSourceStack> ctx, int seite) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        CrownsData d = CommandUtil.data(ctx);
        Nation own = CommandUtil.own(ctx.getSource(), d, p);
        if (own == null) return 0;
        List<Mail> list = new ArrayList<>();
        for (int i = d.mails.size() - 1; i >= 0; i--) {
            Mail m = d.mails.get(i);
            if (m.abgefangenVon.contains(own.key())) list.add(m);
        }
        return show(ctx, "Abgefangene Post " + own.name, list, seite, d);
    }

    private static int gesendet(CommandContext<CommandSourceStack> ctx, int seite) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        CrownsData d = CommandUtil.data(ctx);
        Nation own = CommandUtil.own(ctx.getSource(), d, p);
        if (own == null) return 0;
        List<Mail> list = new ArrayList<>();
        for (int i = d.mails.size() - 1; i >= 0; i--) {
            Mail m = d.mails.get(i);
            if (m.vonNation.equals(own.key())) list.add(m);
        }
        return show(ctx, "Gesendete Post " + own.name, list, seite, d);
    }
}
