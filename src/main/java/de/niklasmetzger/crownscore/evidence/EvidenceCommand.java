package de.niklasmetzger.crownscore.evidence;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.EvidenceEntry;
import de.niklasmetzger.crownscore.data.Nation;
import de.niklasmetzger.crownscore.discord.DiscordWebhook;
import de.niklasmetzger.crownscore.util.CommandUtil;
import de.niklasmetzger.crownscore.util.Msg;
import de.niklasmetzger.crownscore.util.Perms;
import de.niklasmetzger.crownscore.util.TimeUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** /akte: Beweisakte lesen, filtern, exportieren. */
public final class EvidenceCommand {
    private static final int PAGE = 12;

    private EvidenceCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("akte")
                .then(Commands.literal("spieler")
                        .then(Commands.argument("spieler", GameProfileArgument.gameProfile())
                                .executes(ctx -> spieler(ctx, 1))
                                .then(Commands.argument("seite", IntegerArgumentType.integer(1))
                                        .executes(ctx -> spieler(ctx, IntegerArgumentType.getInteger(ctx, "seite"))))))
                .then(Commands.literal("gebiet")
                        .then(Commands.argument("nation", StringArgumentType.string()).suggests(CommandUtil.NATIONS)
                                .executes(ctx -> gebiet(ctx, 1))
                                .then(Commands.argument("seite", IntegerArgumentType.integer(1))
                                        .executes(ctx -> gebiet(ctx, IntegerArgumentType.getInteger(ctx, "seite"))))))
                .then(Commands.literal("typ")
                        .then(Commands.argument("typ", StringArgumentType.word())
                                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                        List.of("BLOCK_ABBAU", "BLOCK_SETZEN", "KISTE", "FEUER", "CAMPING", "EINREISE", "TOETUNG", "KRIEG"), b))
                                .executes(ctx -> typ(ctx, 1))
                                .then(Commands.argument("seite", IntegerArgumentType.integer(1))
                                        .executes(ctx -> typ(ctx, IntegerArgumentType.getInteger(ctx, "seite"))))))
                .then(Commands.literal("neueste")
                        .executes(ctx -> neueste(ctx, 1))
                        .then(Commands.argument("seite", IntegerArgumentType.integer(1))
                                .executes(ctx -> neueste(ctx, IntegerArgumentType.getInteger(ctx, "seite")))))
                .then(Commands.literal("export")
                        .then(Commands.argument("spieler", GameProfileArgument.gameProfile())
                                .executes(ctx -> export(ctx, 50))
                                .then(Commands.argument("anzahl", IntegerArgumentType.integer(1, 200))
                                        .executes(ctx -> export(ctx, IntegerArgumentType.getInteger(ctx, "anzahl"))))))
                .then(Commands.literal("loeschen")
                        .then(Commands.argument("spieler", GameProfileArgument.gameProfile())
                                .executes(EvidenceCommand::loeschen))));
    }

    private static boolean allowed(CommandContext<CommandSourceStack> ctx, CrownsData d, String gebietNation) {
        if (Perms.isAdmin(ctx.getSource())) return true;
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer p)) return false;
        Nation own = d.nationOf(p.getUUID());
        if (own == null) return false;
        // Beamte sehen die Akte fuer das eigene Gebiet
        return gebietNation == null || own.name.equals(gebietNation) && own.isBeamter(p.getUUID());
    }

    private static List<EvidenceEntry> filter(CrownsData d, Predicate<EvidenceEntry> f) {
        List<EvidenceEntry> out = new ArrayList<>();
        for (EvidenceEntry e : d.evidence) if (f.test(e)) out.add(e);
        java.util.Collections.reverse(out);
        return out;
    }

    private static int show(CommandContext<CommandSourceStack> ctx, String titel, List<EvidenceEntry> list, int seite) {
        if (list.isEmpty()) {
            Msg.info(ctx.getSource(), titel + ": keine Eintraege.");
            return 0;
        }
        int pages = CommandUtil.pages(list.size(), PAGE);
        Msg.info(ctx.getSource(), titel + " (" + list.size() + " Eintraege, Seite " + seite + "/" + pages + "):");
        for (EvidenceEntry e : CommandUtil.page(list, seite, PAGE)) {
            Msg.line(ctx.getSource(), " §7" + e.zeile());
        }
        return list.size();
    }

    private static int spieler(CommandContext<CommandSourceStack> ctx, int seite) throws CommandSyntaxException {
        CrownsData d = CommandUtil.data(ctx);
        GameProfile gp = GameProfileArgument.getGameProfiles(ctx, "spieler").iterator().next();
        if (!Perms.isAdmin(ctx.getSource())) {
            // Spieler sehen nur Eintraege, die das eigene Gebiet betreffen
            if (!(ctx.getSource().getEntity() instanceof ServerPlayer p) || d.nationOf(p.getUUID()) == null) {
                Msg.err(ctx.getSource(), "Nur Admins und Beamte duerfen Akten lesen.");
                return 0;
            }
            Nation own = d.nationOf(p.getUUID());
            if (!own.isBeamter(p.getUUID())) {
                Msg.err(ctx.getSource(), "Nur Admins und Beamte duerfen Akten lesen.");
                return 0;
            }
            return show(ctx, "Akte " + gp.getName() + " in Gebiet " + own.name,
                    filter(d, e -> gp.getId().equals(e.taeter) && own.name.equals(e.gebietNation)), seite);
        }
        return show(ctx, "Akte " + gp.getName(), filter(d, e -> gp.getId().equals(e.taeter)), seite);
    }

    private static int gebiet(CommandContext<CommandSourceStack> ctx, int seite) {
        CrownsData d = CommandUtil.data(ctx);
        Nation n = CommandUtil.nation(ctx.getSource(), d, StringArgumentType.getString(ctx, "nation"));
        if (n == null) return 0;
        if (!allowed(ctx, d, n.name)) {
            Msg.err(ctx.getSource(), "Nur Admins und Beamte von " + n.name + " duerfen diese Akte lesen.");
            return 0;
        }
        return show(ctx, "Vorfaelle im Gebiet " + n.name, filter(d, e -> n.name.equals(e.gebietNation)), seite);
    }

    private static int typ(CommandContext<CommandSourceStack> ctx, int seite) {
        if (!CommandUtil.admin(ctx.getSource())) return 0;
        CrownsData d = CommandUtil.data(ctx);
        String typ = StringArgumentType.getString(ctx, "typ").toUpperCase();
        return show(ctx, "Akte Typ " + typ, filter(d, e -> typ.equals(e.typ)), seite);
    }

    private static int neueste(CommandContext<CommandSourceStack> ctx, int seite) {
        if (!CommandUtil.admin(ctx.getSource())) return 0;
        CrownsData d = CommandUtil.data(ctx);
        return show(ctx, "Neueste Eintraege", filter(d, e -> true), seite);
    }

    private static int export(CommandContext<CommandSourceStack> ctx, int anzahl) throws CommandSyntaxException {
        if (!CommandUtil.admin(ctx.getSource())) return 0;
        CrownsData d = CommandUtil.data(ctx);
        GameProfile gp = GameProfileArgument.getGameProfiles(ctx, "spieler").iterator().next();
        List<EvidenceEntry> list = filter(d, e -> gp.getId().equals(e.taeter));
        if (list.isEmpty()) {
            Msg.err(ctx.getSource(), "Keine Eintraege fuer " + gp.getName() + ".");
            return 0;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(":scales: **Beweisakte ").append(gp.getName()).append("** (").append(Math.min(anzahl, list.size()))
                .append(" von ").append(list.size()).append(" Eintraegen, Stand ").append(TimeUtil.fmt(TimeUtil.now())).append(")\n```\n");
        for (EvidenceEntry e : list.subList(0, Math.min(anzahl, list.size()))) {
            sb.append(e.zeile()).append('\n');
        }
        sb.append("```");
        DiscordWebhook.gericht(sb.toString());
        Msg.ok(ctx.getSource(), "Akte von " + gp.getName() + " an den Gerichtskanal geschickt.");
        return 1;
    }

    private static int loeschen(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!CommandUtil.admin(ctx.getSource())) return 0;
        CrownsData d = CommandUtil.data(ctx);
        GameProfile gp = GameProfileArgument.getGameProfiles(ctx, "spieler").iterator().next();
        int before = d.evidence.size();
        d.evidence.removeIf(e -> gp.getId().equals(e.taeter));
        d.setDirty();
        Msg.ok(ctx.getSource(), (before - d.evidence.size()) + " Eintraege von " + gp.getName() + " geloescht.");
        return 1;
    }
}
