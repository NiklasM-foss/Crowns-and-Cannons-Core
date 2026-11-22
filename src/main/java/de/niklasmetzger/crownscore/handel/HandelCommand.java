package de.niklasmetzger.crownscore.handel;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.Nation;
import de.niklasmetzger.crownscore.data.Stadt;
import de.niklasmetzger.crownscore.util.CommandUtil;
import de.niklasmetzger.crownscore.util.Msg;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;
import java.util.Map;

/** /stadt, /haendler, /postfach und die Admin-Befehle /cccore staedte und /cccore haendler. */
public final class HandelCommand {
    /** Markierung des Villager-Haendlers. */
    public static final String TAG = "crownscore_haendler";

    private HandelCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("stadt")
                .executes(ctx -> liste(ctx, null))
                .then(Commands.literal("gruenden").then(Commands.argument("name", StringArgumentType.greedyString()).executes(HandelCommand::gruenden)))
                .then(Commands.literal("pruefen").executes(HandelCommand::pruefen))
                .then(Commands.literal("liste").executes(ctx -> liste(ctx, null))
                        .then(Commands.argument("nation", StringArgumentType.greedyString()).suggests(CommandUtil.NATIONS)
                                .executes(ctx -> liste(ctx, StringArgumentType.getString(ctx, "nation"))))));
        d.register(Commands.literal("haendler")
                .executes(HandelCommand::preise)
                .then(Commands.literal("kaufen").then(Commands.argument("angebot", StringArgumentType.string())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(VillagerHandel.angebote().stream()
                                .map(a -> a.id().substring(a.id().indexOf(':') + 1)).toList(), b))
                        .executes(ctx -> kaufen(ctx, false))
                        .then(Commands.literal("geld").executes(ctx -> kaufen(ctx, false)))
                        .then(Commands.literal("rohstoffe").executes(ctx -> kaufen(ctx, true))))));
        d.register(Commands.literal("postfach").executes(ctx -> {
            PostfachMenu.oeffnen(CommandUtil.player(ctx));
            return 1;
        }));
        d.register(Commands.literal("cccore")
                .then(Commands.literal("staedte")
                        .executes(HandelCommand::offen)
                        .then(Commands.literal("annehmen").then(Commands.argument("stadt", StringArgumentType.string()).suggests(HandelCommand::staedte)
                                .executes(HandelCommand::annehmen)))
                        .then(Commands.literal("uebertragen").then(Commands.argument("stadt", StringArgumentType.string()).suggests(HandelCommand::staedte)
                                .then(Commands.argument("nation", StringArgumentType.greedyString()).suggests(CommandUtil.NATIONS)
                                        .executes(HandelCommand::uebertragen))))
                        .then(Commands.literal("ablehnen").then(Commands.argument("stadt", StringArgumentType.string()).suggests(HandelCommand::staedte)
                                .executes(ctx -> ablehnen(ctx, ""))
                                .then(Commands.argument("grund", StringArgumentType.greedyString())
                                        .executes(ctx -> ablehnen(ctx, StringArgumentType.getString(ctx, "grund")))))))
                .then(Commands.literal("haendler")
                        .then(Commands.literal("setzen").executes(HandelCommand::haendlerSetzen))
                        .then(Commands.literal("entfernen").executes(HandelCommand::haendlerEntfernen))));
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> staedte(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
        return SharedSuggestionProvider.suggest(CrownsData.get(ctx.getSource().getServer()).staedte.stream().map(s -> "\"" + s.name + "\"").toList(), b);
    }

    // ---- Staedte --------------------------------------------------------

    private static int gruenden(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer p = CommandUtil.player(ctx);
        CrownsData d = CommandUtil.data(ctx);
        Nation n = CommandUtil.own(src, d, p);
        if (n == null || !CommandUtil.beamter(src, n, p)) return 0;
        String err = StadtService.gruenden(p, n, StringArgumentType.getString(ctx, "name"));
        if (err != null) {
            Msg.err(src, err);
            return 0;
        }
        Msg.ok(src, "Antrag gestellt. Ein Admin prueft die Stadt, die Entscheidung kommt ins /postfach.");
        return 1;
    }

    private static int pruefen(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer p = CommandUtil.player(ctx);
        CrownsData d = CommandUtil.data(ctx);
        Nation n = d.nationOf(p.getUUID());
        StadtService.Pruefung pr = StadtService.pruefen(p, n);
        Msg.info(src, "Pruefung fuer eine Stadt hier:");
        Msg.line(src, " §7Betten: §f" + pr.betten() + " §7von " + de.niklasmetzger.crownscore.config.CrownsConfig.STADT_BETTEN.get());
        Msg.line(src, " §7Glocke: §f" + (pr.glocke() ? "ja" : "nein"));
        if (pr.naechste() != null) Msg.line(src, " §7Naechste Stadt: §f" + pr.naechste().name + " (" + (int) pr.abstand() + " Bloecke)");
        Msg.line(src, " §7Eigenes Gebiet: §f" + (pr.eigenesGebiet() ? "ja" : "nein"));
        String err = pr.fehler();
        Msg.line(src, err == null ? " §aPasst. /stadt gruenden <Name> stellt den Antrag." : " §c" + err);
        return err == null ? 1 : 0;
    }

    private static int liste(CommandContext<CommandSourceStack> ctx, String arg) {
        CommandSourceStack src = ctx.getSource();
        CrownsData d = CommandUtil.data(ctx);
        Nation n;
        if (arg != null) n = CommandUtil.nation(src, d, arg);
        else n = src.getEntity() instanceof ServerPlayer p ? d.nationOf(p.getUUID()) : null;
        if (n == null) {
            if (arg == null) Msg.err(src, "Du gehoerst keiner Nation an. /stadt liste <Nation>");
            return 0;
        }
        List<Stadt> l = StadtService.von(d, n.key());
        ServerPlayer wer = src.getEntity() instanceof ServerPlayer p ? p : null;
        boolean eigene = wer == null || n.isMember(wer.getUUID()) || n.isChef(wer.getUUID()) || de.niklasmetzger.crownscore.util.Perms.isAdmin(src);
        Msg.info(src, "§fStaedte von " + n.name + ": " + StadtService.anzahl(d, n.key()) + " anerkannt"
                + (eigene ? ", Villager " + VillagerHandel.grenzeText(d, n.key()) : ""));
        int versteckt = 0;
        for (Stadt s : l) {
            // Fremde Staedte nur mit Ort, wenn man schon dort war; wartende Antraege gar nicht
            if (eigene || (s.bestaetigt && s.entdeckt.contains(wer.getUUID()))) Msg.line(src, " §7" + StadtService.zeile(s));
            else if (s.bestaetigt) versteckt++;
        }
        if (versteckt > 0) Msg.line(src, " §8" + versteckt + " weitere, die du noch nicht entdeckt hast.");
        return l.size();
    }

    private static int offen(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!CommandUtil.admin(ctx.getSource())) return 0;
        PostfachMenu.oeffnen(CommandUtil.player(ctx));
        return 1;
    }

    private static String adminName(CommandSourceStack src) {
        return src.getEntity() instanceof ServerPlayer p ? p.getGameProfile().getName() : "Konsole";
    }

    private static int annehmen(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (!CommandUtil.admin(src)) return 0;
        String err = StadtService.annehmen(src.getServer(), StringArgumentType.getString(ctx, "stadt"), adminName(src), true);
        if (err != null) {
            Msg.err(src, err);
            return 0;
        }
        return 1;
    }

    private static int uebertragen(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (!CommandUtil.admin(src)) return 0;
        CrownsData d = CommandUtil.data(ctx);
        Stadt s = StadtService.byName(d, StringArgumentType.getString(ctx, "stadt"));
        if (s == null) {
            Msg.err(src, "Diese Stadt gibt es nicht.");
            return 0;
        }
        Nation neu = CommandUtil.nation(src, d, StringArgumentType.getString(ctx, "nation"));
        if (neu == null) return 0;
        Nation alt = d.nations.get(s.nation);
        if (neu == alt) {
            Msg.err(src, s.name + " gehoert schon " + neu.name + ".");
            return 0;
        }
        if (!s.bestaetigt) {
            s.bestaetigt = true;
            s.bestaetigtVon = adminName(src);
        }
        d.besatzungen.removeIf(b -> b.stadt.equalsIgnoreCase(s.name));
        de.niklasmetzger.crownscore.world.EroberungService.stadtUebergeben(src.getServer(), s, neu, alt == null ? neu : alt,
                "per Admin (" + adminName(src) + ") uebertragen bekommen");
        Msg.ok(src, s.name + " gehoert jetzt " + neu.name + ".");
        return 1;
    }

    private static int ablehnen(CommandContext<CommandSourceStack> ctx, String grund) {
        CommandSourceStack src = ctx.getSource();
        if (!CommandUtil.admin(src)) return 0;
        String err = StadtService.ablehnen(src.getServer(), StringArgumentType.getString(ctx, "stadt"), adminName(src), grund, true);
        if (err != null) {
            Msg.err(src, err);
            return 0;
        }
        Msg.ok(src, "Erledigt.");
        return 1;
    }

    // ---- Haendler -------------------------------------------------------

    private static int preise(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        CrownsData d = CommandUtil.data(ctx);
        Nation n = src.getEntity() instanceof ServerPlayer p ? d.nationOf(p.getUUID()) : null;
        Msg.info(src, "§fVillager-Haendler" + (n == null ? "" : " §7(" + n.name + ": " + StadtService.anzahl(d, n.key()) + " Staedte, "
                + VillagerHandel.grenzeText(d, n.key()) + ")"));
        for (VillagerHandel.Angebot a : VillagerHandel.angebote()) {
            String key = n == null ? "" : n.key();
            Map<net.minecraft.world.item.Item, Integer> roh = VillagerHandel.rohstoffpreis(d, key, a);
            Msg.line(src, " §6" + a.name() + " §7(" + a.id().substring(a.id().indexOf(':') + 1) + "): §f" + Msg.money(VillagerHandel.geldpreis(d, key, a))
                    + (roh.isEmpty() ? "" : " §7oder §f" + VillagerHandel.rohstoffText(roh)));
        }
        Msg.line(src, " §7Kaufen beim Haendler (Rechtsklick) oder mit /haendler kaufen <Angebot> [geld|rohstoffe]");
        return 1;
    }

    private static int kaufen(CommandContext<CommandSourceStack> ctx, boolean roh) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer p = CommandUtil.player(ctx);
        VillagerHandel.Angebot a = VillagerHandel.angebot(StringArgumentType.getString(ctx, "angebot"));
        if (a == null) {
            Msg.err(src, "Dieses Angebot gibt es nicht. /haendler zeigt alle.");
            return 0;
        }
        String err = VillagerHandel.kaufen(p, a, roh);
        if (err != null) {
            Msg.err(src, err);
            return 0;
        }
        Msg.ok(src, a.name() + " gekauft. Er steht neben dir.");
        return 1;
    }

    private static int haendlerSetzen(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        if (!CommandUtil.admin(src)) return 0;
        ServerPlayer p = CommandUtil.player(ctx);
        Villager v = EntityType.VILLAGER.create(p.serverLevel());
        if (v == null) return 0;
        v.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0);
        v.setYHeadRot(p.getYRot());
        v.setNoAi(true);
        v.setInvulnerable(true);
        v.setPersistenceRequired();
        v.setSilent(true);
        v.setCustomName(Component.literal("§6Villager-Haendler"));
        v.setCustomNameVisible(true);
        v.addTag(TAG);
        p.serverLevel().addFreshEntity(v);
        Msg.ok(src, "Villager-Haendler steht hier. Rechtsklick oeffnet das Kaufmenue. Entfernen: davor stellen, /cccore haendler entfernen");
        return 1;
    }

    private static int haendlerEntfernen(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        if (!CommandUtil.admin(src)) return 0;
        ServerPlayer p = CommandUtil.player(ctx);
        List<Entity> l = p.serverLevel().getEntities(p, new AABB(p.blockPosition()).inflate(5), e -> e.getTags().contains(TAG));
        l.forEach(Entity::discard);
        if (l.isEmpty()) Msg.err(src, "Kein Villager-Haendler im Umkreis von 5 Bloecken.");
        else Msg.ok(src, l.size() + " Haendler entfernt.");
        return l.size();
    }

    public static class Events {
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onInteract(PlayerInteractEvent.EntityInteract e) {
            if (!e.getTarget().getTags().contains(TAG)) return;
            e.setCanceled(true);
            e.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            if (e.getEntity() instanceof ServerPlayer p && e.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND) HaendlerMenu.oeffnen(p);
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onInteractAt(PlayerInteractEvent.EntityInteractSpecific e) {
            if (e.getTarget().getTags().contains(TAG)) {
                e.setCanceled(true);
                e.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            }
        }
    }
}
