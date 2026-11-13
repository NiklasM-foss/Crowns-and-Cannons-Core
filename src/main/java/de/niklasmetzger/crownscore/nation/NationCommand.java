package de.niklasmetzger.crownscore.nation;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.Nation;
import de.niklasmetzger.crownscore.data.War;
import de.niklasmetzger.crownscore.discord.DiscordWebhook;
import de.niklasmetzger.crownscore.util.CommandUtil;
import de.niklasmetzger.crownscore.util.Msg;
import de.niklasmetzger.crownscore.util.Names;
import de.niklasmetzger.crownscore.util.Perms;
import de.niklasmetzger.crownscore.util.TimeUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.mojang.authlib.GameProfile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** /nation: Nationen anlegen, beitreten, verwalten. */
public final class NationCommand {
    private NationCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("nation")
                .then(Commands.literal("liste").executes(NationCommand::liste))
                .then(Commands.literal("info")
                        .executes(ctx -> info(ctx, null))
                        .then(Commands.argument("nation", StringArgumentType.string()).suggests(CommandUtil.NATIONS)
                                .executes(ctx -> info(ctx, StringArgumentType.getString(ctx, "nation")))))
                .then(Commands.literal("beitreten")
                        .then(Commands.argument("nation", StringArgumentType.string()).suggests(CommandUtil.NATIONS)
                                .executes(NationCommand::beitreten)))
                .then(Commands.literal("bestaetigen")
                        .then(Commands.argument("spieler", GameProfileArgument.gameProfile())
                                .executes(ctx -> entscheiden(ctx, true))))
                .then(Commands.literal("ablehnen")
                        .then(Commands.argument("spieler", GameProfileArgument.gameProfile())
                                .executes(ctx -> entscheiden(ctx, false))))
                .then(Commands.literal("verlassen").executes(NationCommand::verlassen))
                .then(Commands.literal("rauswerfen")
                        .then(Commands.argument("spieler", GameProfileArgument.gameProfile())
                                .executes(NationCommand::rauswerfen)))
                .then(Commands.literal("beamter")
                        .then(Commands.argument("spieler", GameProfileArgument.gameProfile())
                                .executes(NationCommand::beamter)))
                .then(Commands.literal("wahlmodus")
                        .then(Commands.argument("modus", StringArgumentType.word())
                                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(List.of("KEINE", "DIREKT", "PARLAMENT"), b))
                                .executes(ctx -> wahlmodus(ctx, 0))
                                .then(Commands.argument("amtszeitTage", IntegerArgumentType.integer(0, 3650))
                                        .executes(ctx -> wahlmodus(ctx, IntegerArgumentType.getInteger(ctx, "amtszeitTage"))))))
                // Admin
                .then(Commands.literal("anlegen")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .then(Commands.argument("kuerzel", StringArgumentType.word())
                                        .executes(NationCommand::anlegen))))
                .then(Commands.literal("loeschen")
                        .then(Commands.argument("nation", StringArgumentType.string()).suggests(CommandUtil.NATIONS)
                                .executes(NationCommand::loeschen)))
                .then(Commands.literal("staatschef")
                        .then(Commands.argument("nation", StringArgumentType.string()).suggests(CommandUtil.NATIONS)
                                .then(Commands.argument("spieler", GameProfileArgument.gameProfile())
                                        .executes(NationCommand::staatschef))))
                .then(Commands.literal("setzen")
                        .then(Commands.argument("spieler", GameProfileArgument.gameProfile())
                                .then(Commands.argument("nation", StringArgumentType.string()).suggests(CommandUtil.NATIONS)
                                        .executes(NationCommand::setzen))))
                .then(Commands.literal("umbenennen")
                        .then(Commands.argument("nation", StringArgumentType.string()).suggests(CommandUtil.NATIONS)
                                .then(Commands.argument("neuerName", StringArgumentType.string())
                                        .executes(NationCommand::umbenennen)))));
    }

    private static GameProfile profile(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<GameProfile> c = GameProfileArgument.getGameProfiles(ctx, "spieler");
        return c.iterator().next();
    }

    private static int liste(CommandContext<CommandSourceStack> ctx) {
        CrownsData d = CommandUtil.data(ctx);
        if (d.nations.isEmpty()) {
            Msg.info(ctx.getSource(), "Noch keine Nationen. Admin: /nation anlegen <Name> <Kuerzel>");
            return 0;
        }
        Msg.info(ctx.getSource(), "Nationen (" + d.nations.size() + "):");
        MinecraftServer server = ctx.getSource().getServer();
        for (Nation n : d.nations.values()) {
            String chef = n.chef == null ? "§ckein Staatschef" : Names.of(server, n.chef);
            int kriege = d.activeWarsOf(n.key()).size();
            Msg.line(ctx.getSource(), " §6" + n.anzeige() + "§7 Staatschef: " + chef
                    + ", " + n.mitglieder.size() + " Mitglieder" + (kriege > 0 ? ", §c" + kriege + " Konflikte" : ""));
        }
        return d.nations.size();
    }

    private static int info(CommandContext<CommandSourceStack> ctx, String arg) throws CommandSyntaxException {
        CrownsData d = CommandUtil.data(ctx);
        MinecraftServer server = ctx.getSource().getServer();
        Nation n;
        if (arg == null) {
            ServerPlayer p = CommandUtil.player(ctx);
            n = CommandUtil.own(ctx.getSource(), d, p);
        } else {
            n = CommandUtil.nation(ctx.getSource(), d, arg);
        }
        if (n == null) return 0;
        Msg.info(ctx.getSource(), "§6" + n.anzeige());
        Msg.line(ctx.getSource(), " §7Staatschef: §f" + (n.chef == null ? "keiner" : Names.of(server, n.chef)));
        List<String> namen = new ArrayList<>();
        for (Map.Entry<UUID, String> e : n.mitglieder.entrySet()) {
            String s = e.getValue();
            if (n.beamte.contains(e.getKey())) s += "*";
            if (server.getPlayerList().getPlayer(e.getKey()) != null) s = "§a" + s + "§f";
            namen.add(s);
        }
        Msg.line(ctx.getSource(), " §7Mitglieder (" + n.mitglieder.size() + ", * = Beamter): §f" + String.join(", ", namen));
        Msg.line(ctx.getSource(), " §7Wahlmodus: §f" + n.wahlmodus + (n.amtszeitTage > 0 ? ", Amtszeit " + n.amtszeitTage + " Tage" : ""));
        Msg.line(ctx.getSource(), " §7Staatskasse: §f" + Msg.money(d.treasury(n.key()).stand)
                + (d.treasury(n.key()).schulden() > 0 ? " §c(Schulden " + Msg.money(d.treasury(n.key()).schulden()) + ")" : ""));
        List<War> wars = d.activeWarsOf(n.key());
        if (!wars.isEmpty()) {
            for (War w : wars) {
                Msg.line(ctx.getSource(), " §c" + w.art.anzeige + " gegen " + d.anzeige(w.gegner(n.key())) + " seit " + TimeUtil.fmtShort(w.start));
            }
        }
        if (!n.anfragen.isEmpty() && (n.isBeamter(uuidOf(ctx)) || Perms.isAdmin(ctx.getSource()))) {
            Msg.line(ctx.getSource(), " §eOffene Anfragen: " + String.join(", ", n.anfragen.values()) + " (/nation bestaetigen <Spieler>)");
        }
        Msg.line(ctx.getSource(), " §7Gegruendet: " + TimeUtil.fmt(n.gegruendet));
        return 1;
    }

    private static UUID uuidOf(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getEntity() instanceof ServerPlayer p ? p.getUUID() : null;
    }

    private static int beitreten(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        CrownsData d = CommandUtil.data(ctx);
        Nation n = CommandUtil.nation(ctx.getSource(), d, StringArgumentType.getString(ctx, "nation"));
        if (n == null) return 0;
        Nation own = d.nationOf(p.getUUID());
        if (own != null) {
            Msg.err(ctx.getSource(), "Du bist schon Mitglied von " + own.name + ". Erst /nation verlassen.");
            return 0;
        }
        if (n.anfragen.containsKey(p.getUUID())) {
            Msg.err(ctx.getSource(), "Deine Anfrage an " + n.name + " liegt schon vor.");
            return 0;
        }
        n.anfragen.put(p.getUUID(), p.getGameProfile().getName());
        d.setDirty();
        Msg.ok(ctx.getSource(), "Anfrage an " + n.name + " gestellt. Der Staatschef muss bestaetigen.");
        Msg.nation(ctx.getSource().getServer(), n, "§e" + p.getGameProfile().getName()
                + " moechte " + n.name + " beitreten. /nation bestaetigen " + p.getGameProfile().getName());
        return 1;
    }

    private static int entscheiden(CommandContext<CommandSourceStack> ctx, boolean ja) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        CrownsData d = CommandUtil.data(ctx);
        GameProfile gp = profile(ctx);
        Nation n = null;
        if (Perms.isAdmin(ctx.getSource())) {
            for (Nation x : d.nations.values()) if (x.anfragen.containsKey(gp.getId())) n = x;
        }
        if (n == null) n = CommandUtil.own(ctx.getSource(), d, p);
        if (n == null) return 0;
        if (!CommandUtil.beamter(ctx.getSource(), n, p)) return 0;
        if (!n.anfragen.containsKey(gp.getId())) {
            Msg.err(ctx.getSource(), gp.getName() + " hat keine offene Anfrage an " + n.name + ".");
            return 0;
        }
        n.anfragen.remove(gp.getId());
        if (ja) {
            if (d.nationOf(gp.getId()) != null) {
                Msg.err(ctx.getSource(), gp.getName() + " ist inzwischen woanders Mitglied.");
                d.setDirty();
                return 0;
            }
            n.mitglieder.put(gp.getId(), gp.getName());
            Msg.broadcast(ctx.getSource().getServer(), gp.getName() + " ist jetzt Buerger von " + n.name + ".");
        } else {
            Msg.ok(ctx.getSource(), "Anfrage von " + gp.getName() + " abgelehnt.");
            ServerPlayer target = ctx.getSource().getServer().getPlayerList().getPlayer(gp.getId());
            if (target != null) Msg.player(target, "§cDeine Anfrage an " + n.name + " wurde abgelehnt.");
        }
        d.setDirty();
        return 1;
    }

    private static int verlassen(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        CrownsData d = CommandUtil.data(ctx);
        Nation n = CommandUtil.own(ctx.getSource(), d, p);
        if (n == null) return 0;
        if (!d.activeWarsOf(n.key()).isEmpty() && !Perms.isAdmin(ctx.getSource())) {
            Msg.err(ctx.getSource(), n.name + " befindet sich im Krieg. Austritt waehrend eines Krieges ist nicht moeglich.");
            return 0;
        }
        n.mitglieder.remove(p.getUUID());
        n.beamte.remove(p.getUUID());
        if (p.getUUID().equals(n.chef)) n.chef = null;
        d.setDirty();
        Msg.broadcast(ctx.getSource().getServer(), p.getGameProfile().getName() + " hat " + n.name + " verlassen.");
        return 1;
    }

    private static int rauswerfen(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        CrownsData d = CommandUtil.data(ctx);
        GameProfile gp = profile(ctx);
        Nation n = d.nationOf(gp.getId());
        if (n == null) {
            Msg.err(ctx.getSource(), gp.getName() + " gehoert keiner Nation an.");
            return 0;
        }
        if (!CommandUtil.chef(ctx.getSource(), n, p)) return 0;
        if (gp.getId().equals(n.chef)) {
            Msg.err(ctx.getSource(), "Der Staatschef kann sich nicht selbst rauswerfen.");
            return 0;
        }
        n.mitglieder.remove(gp.getId());
        n.beamte.remove(gp.getId());
        d.setDirty();
        Msg.broadcast(ctx.getSource().getServer(), gp.getName() + " wurde aus " + n.name + " ausgewiesen.");
        return 1;
    }

    private static int beamter(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        CrownsData d = CommandUtil.data(ctx);
        GameProfile gp = profile(ctx);
        Nation n = d.nationOf(gp.getId());
        if (n == null || (!n.isMember(p.getUUID()) && !Perms.isAdmin(ctx.getSource()))) {
            Msg.err(ctx.getSource(), gp.getName() + " ist kein Mitglied deiner Nation.");
            return 0;
        }
        if (!CommandUtil.chef(ctx.getSource(), n, p)) return 0;
        if (n.beamte.remove(gp.getId())) {
            Msg.ok(ctx.getSource(), gp.getName() + " ist kein Beamter mehr.");
        } else {
            n.beamte.add(gp.getId());
            Msg.ok(ctx.getSource(), gp.getName() + " ist jetzt Beamter von " + n.name + " (Visa, Gebiete, Parlament).");
        }
        d.setDirty();
        return 1;
    }

    private static int wahlmodus(CommandContext<CommandSourceStack> ctx, int amtszeit) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        CrownsData d = CommandUtil.data(ctx);
        Nation n = CommandUtil.own(ctx.getSource(), d, p);
        if (n == null) return 0;
        if (!CommandUtil.chef(ctx.getSource(), n, p)) return 0;
        String modus = StringArgumentType.getString(ctx, "modus").toUpperCase(Locale.ROOT);
        if (!List.of("KEINE", "DIREKT", "PARLAMENT").contains(modus)) {
            Msg.err(ctx.getSource(), "Modus muss KEINE, DIREKT oder PARLAMENT sein.");
            return 0;
        }
        n.wahlmodus = modus;
        n.amtszeitTage = amtszeit;
        d.setDirty();
        Msg.ok(ctx.getSource(), n.name + ": Wahlmodus " + modus + (amtszeit > 0 ? ", Amtszeit " + amtszeit + " Tage" : ""));
        return 1;
    }

    // ---- Admin ---------------------------------------------------------

    private static int anlegen(CommandContext<CommandSourceStack> ctx) {
        if (!CommandUtil.admin(ctx.getSource())) return 0;
        CrownsData d = CommandUtil.data(ctx);
        String name = StringArgumentType.getString(ctx, "name").trim();
        String kuerzel = StringArgumentType.getString(ctx, "kuerzel").trim().toUpperCase(Locale.ROOT);
        if (name.length() < 2 || name.length() > 40) {
            Msg.err(ctx.getSource(), "Name muss 2 bis 40 Zeichen haben.");
            return 0;
        }
        if (!kuerzel.matches("[A-Z0-9]{2,5}")) {
            Msg.err(ctx.getSource(), "Kuerzel: 2 bis 5 Buchstaben oder Ziffern, z.B. FRA.");
            return 0;
        }
        if (d.nation(name) != null || d.nation(kuerzel) != null) {
            Msg.err(ctx.getSource(), "Name oder Kuerzel ist schon vergeben.");
            return 0;
        }
        Nation n = new Nation(name, kuerzel);
        d.nations.put(n.key(), n);
        d.setDirty();
        Msg.broadcast(ctx.getSource().getServer(), "Die Nation " + n.anzeige() + " wurde gegruendet.");
        return 1;
    }

    private static int loeschen(CommandContext<CommandSourceStack> ctx) {
        if (!CommandUtil.admin(ctx.getSource())) return 0;
        CrownsData d = CommandUtil.data(ctx);
        Nation n = CommandUtil.nation(ctx.getSource(), d, StringArgumentType.getString(ctx, "nation"));
        if (n == null) return 0;
        for (War w : d.activeWarsOf(n.key())) {
            w.aktiv = false;
            w.ende = TimeUtil.now();
            w.ergebnis = "Nation aufgeloest";
        }
        d.nations.remove(n.key());
        d.treasuries.remove(n.key());
        d.claims.values().forEach(m -> m.values().removeIf(v -> v.equals(n.key())));
        d.setDirty();
        Msg.broadcast(ctx.getSource().getServer(), "Die Nation " + n.name + " wurde aufgeloest.");
        return 1;
    }

    private static int staatschef(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!CommandUtil.admin(ctx.getSource())) return 0;
        CrownsData d = CommandUtil.data(ctx);
        Nation n = CommandUtil.nation(ctx.getSource(), d, StringArgumentType.getString(ctx, "nation"));
        if (n == null) return 0;
        GameProfile gp = profile(ctx);
        Nation other = d.nationOf(gp.getId());
        if (other != null && other != n) {
            other.mitglieder.remove(gp.getId());
            other.beamte.remove(gp.getId());
            if (gp.getId().equals(other.chef)) other.chef = null;
        }
        n.mitglieder.put(gp.getId(), gp.getName());
        n.anfragen.remove(gp.getId());
        n.chef = gp.getId();
        d.setDirty();
        Msg.broadcast(ctx.getSource().getServer(), gp.getName() + " ist jetzt Staatschef von " + n.name + ".");
        DiscordWebhook.news("**" + n.name + "**: " + gp.getName() + " ist neues Staatsoberhaupt.");
        return 1;
    }

    private static int setzen(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!CommandUtil.admin(ctx.getSource())) return 0;
        CrownsData d = CommandUtil.data(ctx);
        Nation n = CommandUtil.nation(ctx.getSource(), d, StringArgumentType.getString(ctx, "nation"));
        if (n == null) return 0;
        GameProfile gp = profile(ctx);
        Nation other = d.nationOf(gp.getId());
        if (other != null) {
            other.mitglieder.remove(gp.getId());
            other.beamte.remove(gp.getId());
            if (gp.getId().equals(other.chef)) other.chef = null;
        }
        for (Nation x : d.nations.values()) x.anfragen.remove(gp.getId());
        n.mitglieder.put(gp.getId(), gp.getName());
        d.setDirty();
        Msg.broadcast(ctx.getSource().getServer(), gp.getName() + " gehoert jetzt zu " + n.name + ".");
        return 1;
    }

    private static int umbenennen(CommandContext<CommandSourceStack> ctx) {
        if (!CommandUtil.admin(ctx.getSource())) return 0;
        CrownsData d = CommandUtil.data(ctx);
        Nation n = CommandUtil.nation(ctx.getSource(), d, StringArgumentType.getString(ctx, "nation"));
        if (n == null) return 0;
        String neu = StringArgumentType.getString(ctx, "neuerName").trim();
        if (d.nation(neu) != null) {
            Msg.err(ctx.getSource(), "Name schon vergeben.");
            return 0;
        }
        String alt = n.key();
        String neuKey = Nation.key(neu);
        d.nations.remove(alt);
        n.name = neu;
        d.nations.put(neuKey, n);
        // Verweise mitziehen
        for (War w : d.wars) {
            if (alt.equals(w.angreifer)) w.angreifer = neuKey;
            if (alt.equals(w.verteidiger)) w.verteidiger = neuKey;
        }
        if (d.treasuries.containsKey(alt)) d.treasuries.put(neuKey, d.treasuries.remove(alt));
        d.claims.values().forEach(m -> m.replaceAll((k, v) -> v.equals(alt) ? neuKey : v));
        d.visas.forEach(v -> { if (v.nation.equals(alt)) v.nation = neuKey; });
        d.colonies.values().forEach(c -> { if (c.nation.equals(alt)) c.nation = neuKey; });
        d.mails.forEach(m -> { if (m.anNation.equals(alt)) m.anNation = neuKey; if (m.vonNation.equals(alt)) m.vonNation = neuKey; });
        d.nations.values().forEach(x -> { if (x.wegsteinFreigaben.remove(alt)) x.wegsteinFreigaben.add(neuKey); });
        d.setDirty();
        Msg.broadcast(ctx.getSource().getServer(), "Die Nation " + d.anzeige(neuKey) + " hiess frueher " + alt + ".");
        return 1;
    }
}
