package de.niklasmetzger.crownscore.election;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.Election;
import de.niklasmetzger.crownscore.data.Nation;
import de.niklasmetzger.crownscore.discord.DiscordWebhook;
import de.niklasmetzger.crownscore.util.CommandUtil;
import de.niklasmetzger.crownscore.util.Msg;
import de.niklasmetzger.crownscore.util.Perms;
import de.niklasmetzger.crownscore.config.CrownsConfig;
import de.niklasmetzger.crownscore.util.TimeUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** /wahl (innerhalb einer Nation) und /abstimmung (Parlament, eine Stimme je Nation). */
public final class ElectionCommand {
    private ElectionCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("wahl")
                .then(Commands.literal("starten")
                        .then(Commands.argument("dauerMinuten", IntegerArgumentType.integer(1, 20160))
                                .then(Commands.argument("amt", BoolArgumentType.bool())
                                        .then(Commands.argument("kandidaten", StringArgumentType.greedyString())
                                                .executes(ElectionCommand::wahlStarten)))))
                .then(Commands.literal("stimme")
                        .then(Commands.argument("kandidat", StringArgumentType.greedyString()).suggests(ElectionCommand::suggestOptions)
                                .executes(ctx -> stimme(ctx, false))))
                .then(Commands.literal("status").executes(ctx -> status(ctx, false)))
                .then(Commands.literal("abbrechen").executes(ctx -> abbrechen(ctx, false, null))
                        .then(Commands.argument("nation", StringArgumentType.string()).suggests(CommandUtil.NATIONS)
                                .executes(ctx -> abbrechen(ctx, false, StringArgumentType.getString(ctx, "nation"))))));
        d.register(Commands.literal("abstimmung")
                .then(Commands.literal("starten")
                        .then(Commands.argument("dauerMinuten", IntegerArgumentType.integer(1, 20160))
                                .then(Commands.argument("titel", StringArgumentType.greedyString())
                                        .executes(ElectionCommand::abstimmungStarten))))
                .then(Commands.literal("ja").executes(ctx -> parlamentStimme(ctx, "Ja")))
                .then(Commands.literal("nein").executes(ctx -> parlamentStimme(ctx, "Nein")))
                .then(Commands.literal("enthaltung").executes(ctx -> parlamentStimme(ctx, "Enthaltung")))
                .then(Commands.literal("status").executes(ctx -> status(ctx, true)))
                .then(Commands.literal("abbrechen").executes(ctx -> abbrechen(ctx, true, null))));
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestOptions(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
        CrownsData d = CommandUtil.data(ctx);
        List<String> out = new ArrayList<>();
        if (ctx.getSource().getEntity() instanceof ServerPlayer p) {
            Nation n = d.nationOf(p.getUUID());
            Election e = n == null ? null : active(d, n.key(), false);
            if (e != null) out.addAll(e.optionen);
        }
        return SharedSuggestionProvider.suggest(out, b);
    }

    private static Election active(CrownsData d, String nationKey, boolean parlament) {
        for (Election e : d.elections) {
            if (e.beendet) continue;
            if (parlament && e.parlament) return e;
            if (!parlament && !e.parlament && e.nation.equals(nationKey)) return e;
        }
        return null;
    }

    private static int wahlStarten(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        CrownsData d = CommandUtil.data(ctx);
        Nation n = CommandUtil.own(ctx.getSource(), d, p);
        if (n == null) return 0;
        boolean admin = Perms.isAdmin(ctx.getSource());
        if (!n.isChef(p.getUUID()) && !admin && !"DIREKT".equals(n.wahlmodus)) {
            Msg.err(ctx.getSource(), "Nur der Staatschef darf Wahlen ansetzen.");
            return 0;
        }
        if ("KEINE".equals(n.wahlmodus) && !admin) {
            Msg.err(ctx.getSource(), n.name + " haelt keine Wahlen ab (Wahlmodus KEINE). Der Staatschef kann das mit /nation wahlmodus aendern.");
            return 0;
        }
        if (active(d, n.key(), false) != null) {
            Msg.err(ctx.getSource(), "In " + n.name + " laeuft schon eine Wahl. /wahl status");
            return 0;
        }
        List<String> kandidaten = new ArrayList<>(new LinkedHashSet<>(Arrays.stream(StringArgumentType.getString(ctx, "kandidaten").split("[,;]| +"))
                .map(String::trim).filter(s -> !s.isEmpty()).toList()));
        if (kandidaten.size() < 2) {
            Msg.err(ctx.getSource(), "Mindestens zwei Kandidaten oder Optionen, getrennt durch Leerzeichen oder Kommas.");
            return 0;
        }
        boolean amt = BoolArgumentType.getBool(ctx, "amt");
        int dauer = IntegerArgumentType.getInteger(ctx, "dauerMinuten");
        if (amt && !admin && !n.isBeamter(p.getUUID())) {
            Msg.err(ctx.getSource(), "Eine Wahl des Staatschefs setzen nur Staatschef oder Beamte an.");
            return 0;
        }
        if (!amt && !admin && !n.isBeamter(p.getUUID()) && dauer > 1440) {
            Msg.err(ctx.getSource(), "Abstimmungen von Buergern dauern hoechstens 1440 Minuten (ein Tag).");
            return 0;
        }
        if (amt && !admin && dauer < CrownsConfig.WAHL_AMT_MINDEST_MINUTEN.get()) {
            Msg.err(ctx.getSource(), "Eine Wahl des Staatschefs dauert mindestens " + CrownsConfig.WAHL_AMT_MINDEST_MINUTEN.get() + " Minuten, damit alle Buerger abstimmen koennen.");
            return 0;
        }
        if (amt) {
            for (String k : kandidaten) {
                if (n.mitglieder.values().stream().noneMatch(m -> m.equalsIgnoreCase(k))) {
                    Msg.err(ctx.getSource(), "\"" + k + "\" ist kein Mitglied von " + n.name + ". Bei Amtswahlen muessen alle Kandidaten Buerger sein.");
                    return 0;
                }
            }
        }
        Election e = new Election();
        e.id = d.nextElectionId++;
        e.nation = n.key();
        e.titel = amt ? "Wahl des Staatschefs" : "Abstimmung";
        e.optionen.addAll(kandidaten);
        e.start = TimeUtil.now();
        e.ende = e.start + dauer * 60_000L;
        e.amt = amt;
        d.elections.add(e);
        d.setDirty();
        Msg.broadcast(ctx.getSource().getServer(), "In " + n.name + " beginnt eine " + e.titel + ": " + String.join(", ", kandidaten)
                + ". Ende " + TimeUtil.fmt(e.ende) + ". Buerger stimmen mit /wahl stimme <Name>.");
        DiscordWebhook.news(":ballot_box: **" + n.name + "**: " + e.titel + " gestartet. Kandidaten: " + String.join(", ", kandidaten) + ". Ende " + TimeUtil.fmt(e.ende) + ".");
        return 1;
    }

    private static int stimme(CommandContext<CommandSourceStack> ctx, boolean parlament) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        CrownsData d = CommandUtil.data(ctx);
        Nation n = CommandUtil.own(ctx.getSource(), d, p);
        if (n == null) return 0;
        Election e = active(d, n.key(), false);
        if (e == null) {
            Msg.err(ctx.getSource(), "In " + n.name + " laeuft gerade keine Wahl.");
            return 0;
        }
        if ("PARLAMENT".equals(n.wahlmodus) && !n.isBeamter(p.getUUID())) {
            Msg.err(ctx.getSource(), n.name + " waehlt ueber das Parlament. Nur Beamte und der Staatschef stimmen ab.");
            return 0;
        }
        String wahl = StringArgumentType.getString(ctx, "kandidat").trim();
        String option = e.optionen.stream().filter(o -> o.equalsIgnoreCase(wahl)).findFirst().orElse(null);
        if (option == null) {
            Msg.err(ctx.getSource(), "Keine Option \"" + wahl + "\". Zur Wahl stehen: " + String.join(", ", e.optionen));
            return 0;
        }
        boolean changed = e.stimmen.containsKey(p.getUUID().toString());
        e.stimmen.put(p.getUUID().toString(), option);
        d.setDirty();
        Msg.ok(ctx.getSource(), (changed ? "Stimme geaendert: " : "Stimme abgegeben: ") + option);
        return 1;
    }

    private static int abstimmungStarten(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        CrownsData d = CommandUtil.data(ctx);
        Nation n = d.nationOf(p.getUUID());
        if (!Perms.isAdmin(ctx.getSource()) && (n == null || !n.isChef(p.getUUID()))) {
            Msg.err(ctx.getSource(), "Resolutionen bringen Staatschefs oder die Spielleitung ein.");
            return 0;
        }
        if (active(d, null, true) != null) {
            Msg.err(ctx.getSource(), "Im Parlament laeuft schon eine Abstimmung. /abstimmung status");
            return 0;
        }
        Election e = new Election();
        e.id = d.nextElectionId++;
        e.parlament = true;
        e.titel = StringArgumentType.getString(ctx, "titel").trim();
        e.optionen.addAll(List.of("Ja", "Nein", "Enthaltung"));
        e.start = TimeUtil.now();
        e.ende = e.start + IntegerArgumentType.getInteger(ctx, "dauerMinuten") * 60_000L;
        d.elections.add(e);
        d.setDirty();
        Msg.broadcast(ctx.getSource().getServer(), "Parlament: Abstimmung ueber \"" + e.titel + "\" (eingebracht von "
                + (n == null ? p.getGameProfile().getName() : n.name) + "). Ende " + TimeUtil.fmt(e.ende) + ". Jede Nation stimmt mit /abstimmung ja|nein|enthaltung.");
        DiscordWebhook.news(":classical_building: **Parlament**: Abstimmung ueber \"" + e.titel + "\" bis " + TimeUtil.fmt(e.ende) + ".");
        return 1;
    }

    private static int parlamentStimme(CommandContext<CommandSourceStack> ctx, String option) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        CrownsData d = CommandUtil.data(ctx);
        Nation n = CommandUtil.own(ctx.getSource(), d, p);
        if (n == null) return 0;
        if (!CommandUtil.beamter(ctx.getSource(), n, p)) return 0;
        Election e = active(d, null, true);
        if (e == null) {
            Msg.err(ctx.getSource(), "Im Parlament laeuft gerade keine Abstimmung.");
            return 0;
        }
        e.stimmen.put(n.key(), option);
        d.setDirty();
        Msg.broadcast(ctx.getSource().getServer(), n.name + " stimmt im Parlament mit " + option + ".");
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx, boolean parlament) throws CommandSyntaxException {
        CrownsData d = CommandUtil.data(ctx);
        Election e;
        if (parlament) {
            e = active(d, null, true);
        } else {
            ServerPlayer p = CommandUtil.player(ctx);
            Nation n = CommandUtil.own(ctx.getSource(), d, p);
            if (n == null) return 0;
            e = active(d, n.key(), false);
        }
        if (e == null) {
            Msg.info(ctx.getSource(), "Keine laufende " + (parlament ? "Abstimmung." : "Wahl."));
            List<Election> past = d.elections.stream().filter(x -> x.beendet && x.parlament == parlament).toList();
            if (!past.isEmpty()) {
                Election last = past.get(past.size() - 1);
                Msg.line(ctx.getSource(), " §7Zuletzt: " + last.titel + " -> " + last.ergebnis);
            }
            return 0;
        }
        Msg.info(ctx.getSource(), e.titel + ", Ende " + TimeUtil.fmt(e.ende) + " (noch " + TimeUtil.duration(e.ende - TimeUtil.now()) + ")");
        for (Map.Entry<String, Integer> c : e.auszaehlung().entrySet()) {
            Msg.line(ctx.getSource(), " §7" + c.getKey() + ": §f" + c.getValue());
        }
        if (parlament) {
            List<String> offen = new ArrayList<>();
            for (Nation n : d.nations.values()) if (!e.stimmen.containsKey(n.key())) offen.add(n.kuerzel);
            if (!offen.isEmpty()) Msg.line(ctx.getSource(), " §7Noch ohne Stimme: " + String.join(", ", offen));
        }
        return 1;
    }

    private static int abbrechen(CommandContext<CommandSourceStack> ctx, boolean parlament, String nationArg) throws CommandSyntaxException {
        if (!Perms.isAdmin(ctx.getSource())) {
            // Chef und Beamte: nur Abstimmungen (keine Wahl des Staatschefs) der eigenen Nation
            CrownsData dd = CommandUtil.data(ctx);
            Nation eigen = ctx.getSource().getEntity() instanceof ServerPlayer sp ? dd.nationOf(sp.getUUID()) : null;
            if (parlament || nationArg != null || eigen == null || !(ctx.getSource().getEntity() instanceof ServerPlayer sp2) || !eigen.isBeamter(sp2.getUUID())) {
                Msg.err(ctx.getSource(), "Abbrechen duerfen Admins, und Staatschef oder Beamte bei Abstimmungen der eigenen Nation.");
                return 0;
            }
            Election ab = active(dd, eigen.key(), false);
            if (ab == null || ab.amt) {
                Msg.err(ctx.getSource(), ab == null ? "Nichts zum Abbrechen." : "Eine Wahl des Staatschefs bricht nur die Spielleitung ab.");
                return 0;
            }
            ab.beendet = true;
            ab.ergebnis = "abgebrochen durch " + sp2.getGameProfile().getName();
            dd.setDirty();
            Msg.broadcast(ctx.getSource().getServer(), "Abstimmung \"" + ab.titel + "\" in " + eigen.name + " wurde von " + sp2.getGameProfile().getName() + " abgebrochen.");
            return 1;
        }
        CrownsData d = CommandUtil.data(ctx);
        Election e;
        if (parlament) {
            e = active(d, null, true);
        } else {
            Nation n;
            if (nationArg != null) {
                n = CommandUtil.nation(ctx.getSource(), d, nationArg);
            } else if (ctx.getSource().getEntity() instanceof ServerPlayer p) {
                n = d.nationOf(p.getUUID());
                if (n == null) Msg.err(ctx.getSource(), "Du gehoerst keiner Nation an. /wahl abbrechen <Nation>");
            } else {
                Msg.err(ctx.getSource(), "Von der Konsole: /wahl abbrechen <Nation>");
                n = null;
            }
            if (n == null) return 0;
            e = active(d, n.key(), false);
        }
        if (e == null) {
            Msg.err(ctx.getSource(), "Nichts zum Abbrechen.");
            return 0;
        }
        e.beendet = true;
        e.ergebnis = "abgebrochen durch die Spielleitung";
        d.setDirty();
        Msg.broadcast(ctx.getSource().getServer(), (parlament ? "Abstimmung" : "Wahl") + " \"" + e.titel + "\" wurde abgebrochen.");
        return 1;
    }
}
