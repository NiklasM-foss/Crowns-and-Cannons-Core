package de.niklasmetzger.crownscore.selftest;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.tree.CommandNode;
import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.Nation;
import de.niklasmetzger.crownscore.data.War;
import de.niklasmetzger.crownscore.data.WarType;
import de.niklasmetzger.crownscore.log.Inspector;
import de.niklasmetzger.crownscore.log.LogDatabase;
import de.niklasmetzger.crownscore.mode.ModeService;
import de.niklasmetzger.crownscore.mode.PlayerMode;
import de.niklasmetzger.crownscore.perms.PermData;
import de.niklasmetzger.crownscore.perms.PermissionManager;
import de.niklasmetzger.crownscore.war.WarService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.permission.PermissionAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * Selbsttest fuer Crowns Core, nur fuer den Testserver: /cctest spielt mit zwei Fake-Spielern Kampf,
 * Modi, Blockprotokoll, Rollback, Rechte und Befehlssichtbarkeit durch und schreibt PASS/FAIL ins Log.
 */
@Mod("crownscoretest")
public class SelfTest {
    private static final Logger LOG = LoggerFactory.getLogger("CCTEST");
    private static final UUID A_ID = UUID.fromString("00000000-0000-0000-0000-00000000a001");
    private static final UUID B_ID = UUID.fromString("00000000-0000-0000-0000-00000000b002");

    private record Step(int waitAfter, String name, Runnable run) {}

    private final Deque<Step> steps = new ArrayDeque<>();
    private int wait;
    private int pass, fail;

    private MinecraftServer server;
    private ServerLevel lvl;
    private FakePlayer a, b;
    private BlockPos base;
    /** Kuehe nach dem Toeten, Reste frueherer Laeufe zaehlen mit. */
    private int cowsAfterKill;
    private Nation na, nb;

    public SelfTest() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Spieler.befehle(Commands.literal("cctest").requires(s -> s.hasPermission(4))));
        e.getDispatcher().register(Commands.literal("cctest").requires(s -> s.hasPermission(4)).executes(ctx -> {
            start(ctx.getSource().getServer());
            return 1;
        }).then(Commands.literal("welt").executes(ctx -> {
            // Testwelt: einige Laender bekommen einen Testbuerger, damit ihr Gebiet als gespielt gilt
            CrownsData d = CrownsData.get(ctx.getSource().getServer());
            int n = 0;
            for (String code : new String[]{"DEU", "FRA", "SAU", "USA"}) {
                for (Nation x : d.nations.values()) {
                    if (code.equals(x.land)) {
                        x.mitglieder.put(UUID.nameUUIDFromBytes(("testbuerger-" + code).getBytes()), "Testbuerger_" + code);
                        n++;
                    }
                }
            }
            d.setDirty();
            LOG.info("[CCTEST] Testwelt: {} Laender mit Testbuerger", n);
            return n;
        })).then(Commands.literal("karte").executes(ctx -> {
            karte(ctx.getSource().getServer());
            return 1;
        })).then(Commands.literal("flagge").executes(ctx -> {
            flagge(ctx.getSource().getServer());
            return 1;
        })).then(Commands.literal("eroberung").executes(ctx -> {
            eroberung(ctx.getSource().getServer());
            return 1;
        })).then(Commands.literal("staedte").executes(ctx -> {
            staedte(ctx.getSource().getServer());
            return 1;
        })));
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || steps.isEmpty()) return;
        if (wait > 0) {
            wait--;
            return;
        }
        Step s = steps.poll();
        try {
            s.run().run();
        } catch (Throwable t) {
            check(s.name() + " (Ausnahme)", false, t.toString());
            LOG.error("Ausnahme in {}", s.name(), t);
        }
        wait = s.waitAfter();
        if (steps.isEmpty()) LOG.info("[CCTEST] FERTIG: {} bestanden, {} fehlgeschlagen", pass, fail);
    }

    private void check(String name, boolean ok, String detail) {
        if (ok) pass++;
        else fail++;
        LOG.info("[CCTEST] {} {}{}", ok ? "PASS" : "FAIL", name, detail == null || detail.isEmpty() ? "" : " -> " + detail);
    }

    private void step(String name, Runnable r) {
        steps.add(new Step(0, name, r));
    }

    /** Mit LuckPerms haben Fake-Spieler keine Rechte-Daten (Capability fehlt): Schritt ueberspringen statt FAIL. */
    private void stepOhneLp(String name, Runnable r) {
        if (net.minecraftforge.fml.ModList.get().isLoaded("luckperms")) {
            step(name, () -> LOG.info("[CCTEST] SKIP {} (LuckPerms geladen, Fake-Spieler haben dort keine Daten; ohne LuckPerms testen)", name));
        } else {
            step(name, r);
        }
    }

    private void stepWait(int ticks, String name, Runnable r) {
        steps.add(new Step(ticks, name, r));
    }

    private boolean blocked(FakePlayer att, FakePlayer vic) {
        LivingAttackEvent ev = new LivingAttackEvent(vic, att.damageSources().playerAttack(att), 1f);
        return MinecraftForge.EVENT_BUS.post(ev);
    }

    private long startMs;

    /** Zeitfenster seit Teststart fuer t:, damit Reste frueherer Laeufe nicht mitzaehlen. */
    private String since() {
        return "t:" + ((System.currentTimeMillis() - startMs) / 1000 + 2) + "s";
    }

    private void cmd(String c) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), c);
    }

    private BlockPos at(int x, int y, int z) {
        return base.offset(x, y, z);
    }

    private void place(FakePlayer p, BlockPos target, ItemStack stack) {
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos below = target.below();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false);
        p.gameMode.useItemOn(p, lvl, p.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    }

    private void countLog(String name, String where, List<Object> args, long min) {
        LogDatabase.async(server, c -> LogDatabase.count(c, where, args),
                n -> check(name, n >= min, n + " Eintraege, erwartet mindestens " + min), ex -> check(name, false, ex.toString()));
    }

    private boolean canUse(CommandSourceStack src, String... path) {
        CommandNode<CommandSourceStack> n = server.getCommands().getDispatcher().getRoot();
        for (String p : path) {
            CommandNode<CommandSourceStack> c = p.equals("<arg>") ? n.getChildren().stream()
                    .filter(x -> x instanceof com.mojang.brigadier.tree.ArgumentCommandNode).findFirst().orElse(null) : n.getChild(p);
            if (c == null) throw new IllegalStateException("Knoten fehlt: " + String.join(" ", path));
            if (!c.canUse(src)) return false;
            n = c;
        }
        return true;
    }

    private int diamonds(BlockPos pos) {
        if (!(lvl.getBlockEntity(pos) instanceof ChestBlockEntity ch)) return -1;
        int n = 0;
        for (int i = 0; i < ch.getContainerSize(); i++) if (ch.getItem(i).is(Items.DIAMOND)) n += ch.getItem(i).getCount();
        return n;
    }

    private int cows() {
        return lvl.getEntitiesOfClass(Cow.class, new AABB(at(-8, -2, -8), at(8, 6, 8))).size();
    }

    /** Schritt, der wartet, bis die Bedingung erfuellt ist (hoechstens timeout Ticks). */
    private void stepUntil(String name, java.util.function.BooleanSupplier cond, int timeout) {
        final int[] left = {timeout};
        steps.add(new Step(0, name, new Runnable() {
            @Override
            public void run() {
                if (!cond.getAsBoolean() && left[0]-- > 0) {
                    steps.addFirst(new Step(0, name, this));
                    wait = 1;
                } else if (!cond.getAsBoolean()) {
                    check(name, false, "Zeitueberschreitung");
                }
            }
        }));
    }

    private UUID ownerAt(double lon, double lat) {
        double bpg = de.niklasmetzger.crownscore.world.WorldMap.blocksPerDegree();
        var c = xaero.pac.common.server.api.OpenPACServerAPI.get(server).getServerClaimsManager()
                .get(net.minecraft.world.level.Level.OVERWORLD.location(), (int) Math.floor(lon * bpg / 16), (int) Math.floor(-lat * bpg / 16));
        return c == null ? null : c.getPlayerId();
    }

    private Nation nat(String code) {
        for (Nation x : CrownsData.get(server).nations.values()) if (code.equals(x.land)) return x;
        return null;
    }

    /** Weltkarte: Staedte, Laender als Staatskonten und Parties, Annexion, Teileroberung. */
    private void karte(MinecraftServer srv) {
        server = srv;
        lvl = srv.overworld();
        pass = fail = 0;
        steps.clear();
        LOG.info("[CCTEST] START Karte");
        double bpg = de.niklasmetzger.crownscore.world.WorldMap.blocksPerDegree();
        step("Staedte", () -> {
            Object[][] staedte = {
                    {"Berlin", 13.405, 52.52, "Deutschland"}, {"Riad", 46.72, 24.69, "Saudi-Arabien"},
                    {"New York", -74.0, 40.71, "Vereinigte Staaten"}, {"Paris", 2.35, 48.86, "Frankreich"},
                    {"Sydney", 151.21, -33.87, "Australien"}, {"Kairo", 31.24, 30.04, "Ägypten"}, {"Tokio", 139.69, 35.69, "Japan"}};
            for (Object[] st : staedte) {
                int x = (int) Math.floor((double) st[1] * bpg);
                int z = (int) Math.floor(-(double) st[2] * bpg);
                var land = de.niklasmetzger.crownscore.world.WorldMap.atChunk(x >> 4, z >> 4);
                String name = land == null ? "Meer" : land.name();
                check("Karte: " + st[0] + " liegt in " + st[3], st[3].equals(name), name);
            }
            check("Karte: mitten im Atlantik niemand", de.niklasmetzger.crownscore.claims.Claims.nationAt(lvl, new BlockPos((int) (-30 * bpg), 64, (int) (-30 * bpg))) == null, "");
        });
        step("Gebiet nur fuer gespielte Nationen", () -> {
            CrownsData d = CrownsData.get(server);
            BlockPos berlin = new BlockPos((int) (13.405 * bpg), 64, (int) (-52.52 * bpg));
            Nation deu = nat("DEU");
            check("Karte: Deutschland ist angelegt und haelt DEU", deu != null && deu.laender.contains("DEU"), deu == null ? "fehlt" : String.valueOf(deu.laender));
            if (deu == null) return;
            UUID dummy = UUID.fromString("00000000-0000-0000-0000-0000000000de");
            boolean hatte = deu.mitglieder.containsKey(dummy);
            deu.mitglieder.remove(dummy);
            boolean leer = !deu.gespielt();
            check("Karte: unbespieltes Deutschland ist freies Land", !leer || de.niklasmetzger.crownscore.claims.Claims.nationAt(lvl, berlin) == null, "");
            deu.mitglieder.put(dummy, "Testbuerger");
            check("Karte: mit Buerger gehoert Berlin zu Deutschland", deu.key().equals(de.niklasmetzger.crownscore.claims.Claims.nationAt(lvl, berlin)), "");
            if (!hatte) deu.mitglieder.remove(dummy);
        });
        step("Flaeche aufbauen", () -> cmd("nation weltkarte minimap flaeche"));
        stepUntil("Flaeche fertig", () -> !de.niklasmetzger.crownscore.claims.OpacTerritory.running(), 4000);
        step("Laender als Staatskonten", () -> {
            Nation deu = nat("DEU"), fra = nat("FRA");
            UUID b = ownerAt(13.405, 52.52), pa = ownerAt(2.35, 48.86);
            check("Minimap: Berlin gehoert dem Staatskonto von Deutschland", deu != null && deu.staat().equals(b), String.valueOf(b));
            check("Minimap: Paris gehoert dem Staatskonto von Frankreich", fra != null && fra.staat().equals(pa), String.valueOf(pa));
            var info = xaero.pac.common.server.api.OpenPACServerAPI.get(server).getServerClaimsManager().getPlayerInfo(deu.staat());
            check("Minimap: Besitzername ist Deutschland", info != null && "Deutschland".equals(info.getPlayerUsername()), info == null ? "null" : info.getPlayerUsername());
            var party = xaero.pac.common.server.api.OpenPACServerAPI.get(server).getPartyManager().getPartyById(deu.partei);
            check("Party: Deutschland hat eine eigene Party", party != null, "");
        });
        step("Staatschef wird Party-Besitzer", () -> {
            Nation deu = nat("DEU");
            UUID chef = UUID.fromString("00000000-0000-0000-0000-0000000c4ef0");
            deu.mitglieder.put(chef, "Kanzlerin");
            deu.chef = chef;
            de.niklasmetzger.crownscore.claims.OpacTerritory.syncNation(server, deu);
            var party = xaero.pac.common.server.api.OpenPACServerAPI.get(server).getPartyManager().getPartyById(deu.partei);
            check("Party: Staatschef ist Besitzer", party != null && chef.equals(party.getOwner().getUUID()), party == null ? "keine Party" : String.valueOf(party.getOwner().getUUID()));
            check("Party: Buerger ist Mitglied", party != null && party.getMemberInfo(UUID.fromString("00000000-0000-0000-0000-0000000000de")) != null || !deu.mitglieder.containsKey(UUID.fromString("00000000-0000-0000-0000-0000000000de")), "");
            deu.chef = null;
            deu.mitglieder.remove(chef);
            de.niklasmetzger.crownscore.claims.OpacTerritory.syncNation(server, deu);
            check("Party: ohne Staatschef wieder das Staatskonto", party != null && deu.staat().equals(party.getOwner().getUUID()), party == null ? "" : String.valueOf(party.getOwner().getUUID()));
        });
        step("Frankreich annektieren", () -> cmd("nation annektieren FRA Deutschland"));
        stepUntil("Annexion fertig", () -> !de.niklasmetzger.crownscore.claims.OpacTerritory.running(), 4000);
        step("Grenze weg", () -> {
            Nation deu = nat("DEU"), fra = nat("FRA");
            check("Annexion: Deutschland haelt FRA", deu.laender.contains("FRA") && !fra.laender.contains("FRA"), deu.laender + " / " + fra.laender);
            check("Annexion: Paris gehoert jetzt dem Staatskonto von Deutschland", deu.staat().equals(ownerAt(2.35, 48.86)), String.valueOf(ownerAt(2.35, 48.86)));
            UUID west = ownerAt(7.2, 48.58), ost = ownerAt(8.3, 48.58);
            check("Annexion: bei Strassburg beide Seiten gleicher Besitzer, keine Grenze", deu.staat().equals(west) && deu.staat().equals(ost), west + " / " + ost);
        });
        step("Frankreich zurueckgeben", () -> cmd("nation annektieren FRA Frankreich"));
        stepUntil("Rueckgabe fertig", () -> !de.niklasmetzger.crownscore.claims.OpacTerritory.running(), 4000);
        step("Grenze wieder da", () -> {
            Nation fra = nat("FRA");
            check("Rueckgabe: Paris gehoert wieder Frankreich", fra.staat().equals(ownerAt(2.35, 48.86)), String.valueOf(ownerAt(2.35, 48.86)));
        });
        step("Teileroberung", () -> {
            CrownsData d = CrownsData.get(server);
            Nation deu = nat("DEU"), fra = nat("FRA");
            int cx = (int) Math.floor(2.35 * bpg / 16), cz = (int) Math.floor(-48.86 * bpg / 16);
            var map = d.claims.computeIfAbsent(net.minecraft.world.level.Level.OVERWORLD.location().toString(), k -> new java.util.HashMap<>());
            map.put(net.minecraft.world.level.ChunkPos.asLong(cx, cz), deu.key());
            de.niklasmetzger.crownscore.claims.OpacTerritory.syncChunks(server, List.of(new net.minecraft.world.level.ChunkPos(cx, cz)));
            check("Teileroberung: ein Chunk in Paris gehoert Deutschland", deu.staat().equals(ownerAt(2.35, 48.86)), "");
            check("Teileroberung: Nachbarchunk bleibt Frankreich", fra.staat().equals(ownerAt(2.35 + 0.2, 48.86)), "");
            map.remove(net.minecraft.world.level.ChunkPos.asLong(cx, cz));
            de.niklasmetzger.crownscore.claims.OpacTerritory.syncChunks(server, List.of(new net.minecraft.world.level.ChunkPos(cx, cz)));
            check("Teileroberung: zurueckgesetzt", fra.staat().equals(ownerAt(2.35, 48.86)), "");
        });
    }

    /** Laender einnehmen: Kauf freier Laender, Besetzung im Krieg mit Haltezeit, zerstoerter Block, Kartenflaggen. */
    private void eroberung(MinecraftServer srv) {
        server = srv;
        lvl = srv.overworld();
        pass = fail = 0;
        steps.clear();
        LOG.info("[CCTEST] START Eroberung");
        if (a == null) a = FakePlayerFactory.get(lvl, new GameProfile(A_ID, "TestAlpha"));
        double bpg = de.niklasmetzger.crownscore.world.WorldMap.blocksPerDegree();
        UUID jorBuerger = UUID.fromString("00000000-0000-0000-0000-0000000004a0");
        final boolean[] online = {de.niklasmetzger.crownscore.config.CrownsConfig.EROBERUNG_VERTEIDIGER_ONLINE.get()};
        final long[] gegruendet = {0};
        final de.niklasmetzger.crownscore.data.War[] krieg = {null};
        step("Vorbereitung", () -> {
            CrownsData d = CrownsData.get(server);
            Nation sau = nat("SAU"), yem = nat("YEM"), jor = nat("JOR");
            check("Eroberung: SAU, YEM und JOR sind angelegt", sau != null && yem != null && jor != null, "");
            sau.mitglieder.put(A_ID, "TestAlpha");
            sau.chef = A_ID;
            de.niklasmetzger.crownscore.treasury.TreasuryService.credit(d, sau.key(), 1.0E7);
            d.landSperre.clear();
            d.besatzungen.clear();
            for (Nation n : d.nations.values()) {
                if (n != yem) n.laender.remove("YEM");
                if (n != jor) n.laender.remove("JOR");
            }
            yem.laender.add("YEM");
            jor.laender.add("JOR");
        });
        step("Pruefungen beim Kauf", () -> {
            CrownsData d = CrownsData.get(server);
            Nation sau = nat("SAU");
            var yem = de.niklasmetzger.crownscore.world.WorldMap.byCode("YEM");
            var aus = de.niklasmetzger.crownscore.world.WorldMap.byCode("AUS");
            check("Kauf: Jemen ist frei", de.niklasmetzger.crownscore.world.EroberungService.frei(d, yem), "");
            String err = de.niklasmetzger.crownscore.world.EroberungService.pruefen(d, sau, yem, true);
            check("Kauf: Saudi-Arabien darf den angrenzenden Jemen kaufen", err == null, err);
            err = de.niklasmetzger.crownscore.world.EroberungService.pruefen(d, sau, aus, true);
            check("Kauf: Australien ist zu weit weg", err != null && err.contains("zu weit"), err);
            double erwartet = de.niklasmetzger.crownscore.config.CrownsConfig.EROBERUNG_KAUF_GRUNDPREIS.get()
                    + de.niklasmetzger.crownscore.config.CrownsConfig.EROBERUNG_KAUF_PRO_CHUNK.get() * yem.fields();
            check("Kauf: Preis = Grundpreis + Chunks", Math.abs(de.niklasmetzger.crownscore.world.EroberungService.kaufpreis(yem) - erwartet) < 1e-6, "");
            check("Nachbarn: Deutschland grenzt an Frankreich", de.niklasmetzger.crownscore.world.WorldMap.nahe("FRA", List.of("DEU"), 0), "");
            check("Nachbarn: Spanien grenzt nicht an Deutschland", !de.niklasmetzger.crownscore.world.WorldMap.nahe("ESP", List.of("DEU"), 0), "");
            check("Nachbarn: Grossbritannien liegt mit 16 Chunks Abstand nahe Frankreich", de.niklasmetzger.crownscore.world.WorldMap.nahe("GBR", List.of("FRA"), 16), "");
        });
        step("Jemen kaufen", () -> {
            CrownsData d = CrownsData.get(server);
            Nation sau = nat("SAU");
            double vorher = de.niklasmetzger.crownscore.treasury.TreasuryService.stand(d, sau.key());
            String err = de.niklasmetzger.crownscore.world.EroberungService.kaufen(server, sau, de.niklasmetzger.crownscore.world.WorldMap.byCode("YEM"));
            check("Kauf: gekauft", err == null, err);
            check("Kauf: Saudi-Arabien haelt YEM", sau.laender.contains("YEM") && !nat("YEM").laender.contains("YEM"), String.valueOf(sau.laender));
            check("Kauf: Preis abgebucht", vorher - de.niklasmetzger.crownscore.treasury.TreasuryService.stand(d, sau.key()) > 1, "");
            check("Kauf: Jemen ist danach gesperrt", d.landSperre.containsKey("YEM")
                    || de.niklasmetzger.crownscore.config.CrownsConfig.EROBERUNG_SPERRE_TAGE.get() == 0, "");
        });
        stepUntil("Karte nach Kauf", () -> !de.niklasmetzger.crownscore.claims.OpacTerritory.running(), 4000);
        step("Kartenflaggen", () -> {
            CrownsData d = CrownsData.get(server);
            Nation sau = nat("SAU");
            var m = de.niklasmetzger.crownscore.flag.MapFlagService.current(d);
            check("Kartenflaggen: Saudi-Arabien hat eine Flagge fuer sein Staatskonto", m.containsKey(sau.staat()), m.size() + " Flaggen");
            check("Kartenflaggen: Sanaa gehoert jetzt dem Staatskonto von Saudi-Arabien", sau.staat().equals(ownerAt(44.2, 15.35)), String.valueOf(ownerAt(44.2, 15.35)));
        });
        step("Besetzen ohne Krieg", () -> {
            CrownsData d = CrownsData.get(server);
            Nation jor = nat("JOR");
            jor.mitglieder.put(jorBuerger, "Testbuerger_JOR");
            gegruendet[0] = jor.gegruendet;
            jor.gegruendet = 0;
            String err = de.niklasmetzger.crownscore.world.EroberungService.pruefen(d, nat("SAU"), de.niklasmetzger.crownscore.world.WorldMap.byCode("JOR"), false);
            check("Besetzen: ohne Krieg nicht erlaubt", err != null && err.contains("Krieg"), err);
            krieg[0] = de.niklasmetzger.crownscore.war.WarService.declare(server, nat("SAU"), jor, de.niklasmetzger.crownscore.data.WarType.KRIEG,
                    "Selbsttest der Besetzung", "TestAlpha");
            err = de.niklasmetzger.crownscore.world.EroberungService.pruefen(d, nat("SAU"), de.niklasmetzger.crownscore.world.WorldMap.byCode("JOR"), false);
            check("Besetzen: im Krieg erlaubt", err == null, err);
        });
        step("Besatzungsblock setzen", () -> {
            int x = (int) Math.floor(36.0 * bpg), z = (int) Math.floor(-31.0 * bpg);
            lvl.getChunk(x >> 4, z >> 4);
            int y = lvl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            a.setPos(x + 0.5, y, z + 0.5);
            de.niklasmetzger.crownscore.config.CrownsConfig.EROBERUNG_VERTEIDIGER_ONLINE.set(false);
            String err = de.niklasmetzger.crownscore.world.EroberungService.besetzen(a, nat("SAU"));
            check("Besetzen: Block gesetzt", err == null, err);
            CrownsData d = CrownsData.get(server);
            var b = d.besatzungen.isEmpty() ? null : d.besatzungen.get(0);
            check("Besetzen: Besetzung laeuft", b != null && b.land.equals("JOR"), "");
            if (b != null) {
                check("Besetzen: Block steht", lvl.getBlockState(b.pos).is(de.niklasmetzger.crownscore.world.EroberungService.block()), b.pos.toShortString());
                b.rest = 3;
            }
        });
        stepWait(100, "warten", () -> {});
        step("Haltezeit vorbei", () -> {
            CrownsData d = CrownsData.get(server);
            Nation sau = nat("SAU");
            check("Besetzen: Jordanien gehoert jetzt Saudi-Arabien", sau.laender.contains("JOR") && !nat("JOR").laender.contains("JOR"), String.valueOf(sau.laender));
            check("Besetzen: keine Besetzung mehr offen", d.besatzungen.isEmpty(), "");
        });
        stepUntil("Karte nach Besetzung", () -> !de.niklasmetzger.crownscore.claims.OpacTerritory.running(), 4000);
        step("Jordanien zurueck", () -> {
            de.niklasmetzger.crownscore.world.WeltkarteCommand.uebertragen(server, List.of("JOR"), nat("JOR"), null, LOG::info);
        });
        stepUntil("Karte nach Rueckgabe", () -> !de.niklasmetzger.crownscore.claims.OpacTerritory.running(), 4000);
        step("Zweite Besetzung, Block wird zerstoert", () -> {
            CrownsData d = CrownsData.get(server);
            d.landSperre.clear();
            String err = de.niklasmetzger.crownscore.world.EroberungService.besetzen(a, nat("SAU"));
            check("Besetzen: zweite Besetzung beginnt", err == null, err);
            var b = d.besatzungen.isEmpty() ? null : d.besatzungen.get(0);
            if (b != null) {
                lvl.setChunkForced(b.pos.getX() >> 4, b.pos.getZ() >> 4, true);
                lvl.removeBlock(b.pos, false);
            }
        });
        stepWait(60, "warten", () -> {});
        step("Gescheitert", () -> {
            CrownsData d = CrownsData.get(server);
            check("Besetzen: zerstoerter Block beendet die Besetzung", d.besatzungen.isEmpty(), "");
            check("Besetzen: Jordanien bleibt bei Jordanien", nat("JOR").laender.contains("JOR"), "");
            int bx = (int) Math.floor(36.0 * bpg), bz = (int) Math.floor(-31.0 * bpg);
            lvl.setChunkForced(bx >> 4, bz >> 4, false);
        });
        stepUntil("Karte", () -> !de.niklasmetzger.crownscore.claims.OpacTerritory.running(), 4000);
        step("Aufraeumen", () -> {
            CrownsData d = CrownsData.get(server);
            de.niklasmetzger.crownscore.config.CrownsConfig.EROBERUNG_VERTEIDIGER_ONLINE.set(online[0]);
            if (krieg[0] != null) de.niklasmetzger.crownscore.war.WarService.end(server, krieg[0], "Selbsttest beendet", false);
            Nation sau = nat("SAU"), jor = nat("JOR");
            jor.mitglieder.remove(jorBuerger);
            jor.gegruendet = gegruendet[0];
            sau.mitglieder.remove(A_ID);
            sau.chef = null;
            de.niklasmetzger.crownscore.world.WeltkarteCommand.uebertragen(server, List.of("YEM"), nat("YEM"), null, LOG::info);
            d.landSperre.clear();
            d.setDirty();
            check("Aufraeumen: Jemen wieder beim Jemen", nat("YEM").laender.contains("YEM"), "");
        });
        stepUntil("Karte zurueck", () -> !de.niklasmetzger.crownscore.claims.OpacTerritory.running(), 4000);
    }

    /** Staedte: Antrag, Bestaetigung, Villager-Haendler mit Grenze und Preis, Eroberung einer Stadt und damit des Landes. */
    private void staedte(MinecraftServer srv) {
        server = srv;
        lvl = srv.overworld();
        pass = fail = 0;
        steps.clear();
        LOG.info("[CCTEST] START Staedte");
        if (a == null) a = FakePlayerFactory.get(lvl, new GameProfile(A_ID, "TestAlpha"));
        double bpg = de.niklasmetzger.crownscore.world.WorldMap.blocksPerDegree();
        UUID jorBuerger = UUID.fromString("00000000-0000-0000-0000-0000000004a0");
        final boolean[] online = {de.niklasmetzger.crownscore.config.CrownsConfig.EROBERUNG_VERTEIDIGER_ONLINE.get()};
        final long[] gegruendet = {0};
        final de.niklasmetzger.crownscore.data.War[] krieg = {null};
        final List<BlockPos> gesetzt = new java.util.ArrayList<>();
        final net.minecraft.world.entity.Entity[] gekauft = {null};
        // Betten und Glocke an einer Stelle aufbauen, Spieler in die Mitte stellen
        java.util.function.BiFunction<Double, Double, BlockPos> dorf = (lon, lat) -> {
            int x = (int) Math.floor(lon * bpg), z = (int) Math.floor(-lat * bpg);
            lvl.getChunk(x >> 4, z >> 4);
            lvl.setChunkForced(x >> 4, z >> 4, true);
            int y = lvl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 3;
            for (int i = 0; i < 10; i++) {
                BlockPos foot = new BlockPos(x - 10 + i * 2, y, z + 4), head = foot.south();
                var bed = net.minecraft.world.level.block.Blocks.RED_BED.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.SOUTH);
                lvl.setBlock(foot, bed.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT), 2);
                lvl.setBlock(head, bed.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD), 2);
                gesetzt.add(foot);
                gesetzt.add(head);
            }
            BlockPos glocke = new BlockPos(x + 2, y, z);
            lvl.setBlock(glocke, net.minecraft.world.level.block.Blocks.BELL.defaultBlockState(), 2);
            gesetzt.add(glocke);
            a.setPos(x + 0.5, y, z + 0.5);
            return new BlockPos(x, y, z);
        };
        step("Vorbereitung", () -> {
            CrownsData d = CrownsData.get(server);
            Nation sau = nat("SAU"), jor = nat("JOR");
            // Testspieler aus allen Nationen loesen (Reste abgebrochener Laeufe)
            for (Nation x : d.nations.values()) {
                x.mitglieder.remove(A_ID);
                x.beamte.remove(A_ID);
                if (A_ID.equals(x.chef)) x.chef = null;
            }
            sau.mitglieder.put(A_ID, "TestAlpha");
            sau.chef = A_ID;
            jor.mitglieder.put(jorBuerger, "Testbuerger_JOR");
            gegruendet[0] = jor.gegruendet;
            jor.gegruendet = 0;
            d.staedte.removeIf(s -> s.name.startsWith("Test"));
            d.besatzungen.clear();
            d.landSperre.clear();
            d.villagerGekauft.remove(sau.key());
            d.villagerNachfrage.remove(sau.key());
            for (Nation n : d.nations.values()) if (n != jor) n.laender.remove("JOR");
            jor.laender.add("JOR");
            check("Staedte: Saudi-Arabien hat am Anfang keine Stadt", de.niklasmetzger.crownscore.handel.StadtService.anzahl(d, sau.key()) == 0, "");
        });
        step("Antrag Riad", () -> {
            CrownsData d = CrownsData.get(server);
            Nation sau = nat("SAU");
            dorf.apply(46.72, 24.69);
            var pr = de.niklasmetzger.crownscore.handel.StadtService.pruefen(a, sau);
            check("Stadt: 10 Betten gezaehlt", pr.betten() == 10, String.valueOf(pr.betten()));
            check("Stadt: Glocke gefunden", pr.glocke(), "");
            String err = de.niklasmetzger.crownscore.handel.StadtService.gruenden(a, sau, "Testriad");
            check("Stadt: Antrag gestellt", err == null, err);
            check("Stadt: Antrag wartet auf Admin", de.niklasmetzger.crownscore.handel.StadtService.offen(d).stream().anyMatch(s -> s.name.equals("Testriad")), "");
            check("Stadt: wartender Antrag zaehlt nicht", de.niklasmetzger.crownscore.handel.StadtService.anzahl(d, sau.key()) == 0, "");
            err = de.niklasmetzger.crownscore.handel.StadtService.gruenden(a, sau, "Testriad2");
            check("Stadt: zweiter Antrag waehrend der erste offen ist abgelehnt", err != null, err);
            check("Postfach: Antragsteller hat eine Nachricht", de.niklasmetzger.crownscore.handel.Postfach.von(d, A_ID).stream().anyMatch(n -> n.titel.contains("Testriad")), "");
            check("Haendler: ohne Stadt Grenze 0", de.niklasmetzger.crownscore.handel.VillagerHandel.grenze(d, sau.key()) == 0, "");
        });
        step("Annehmen", () -> {
            CrownsData d = CrownsData.get(server);
            Nation sau = nat("SAU");
            String err = de.niklasmetzger.crownscore.handel.StadtService.annehmen(server, "Testriad", "Selbsttest", true);
            check("Stadt: angenommen", err == null, err);
            check("Stadt: zaehlt jetzt", de.niklasmetzger.crownscore.handel.StadtService.anzahl(d, sau.key()) == 1, "");
            check("Haendler: mit einer Stadt Grenze 5", de.niklasmetzger.crownscore.handel.VillagerHandel.grenze(d, sau.key()) == 5, "");
        });
        step("Villager kaufen", () -> {
            CrownsData d = CrownsData.get(server);
            Nation sau = nat("SAU");
            var angebot = de.niklasmetzger.crownscore.handel.VillagerHandel.angebot("villager");
            check("Haendler: Angebot Dorfbewohner da", angebot != null, "");
            if (angebot == null) return;
            double vorher = de.niklasmetzger.crownscore.handel.VillagerHandel.geldpreis(d, sau.key(), angebot);
            a.getInventory().clearContent();
            angebot.rohstoffe().forEach((item, n) -> a.getInventory().add(new net.minecraft.world.item.ItemStack(item, n)));
            String err = de.niklasmetzger.crownscore.handel.VillagerHandel.kaufen(a, angebot, true);
            check("Haendler: mit Rohstoffen gekauft", err == null, err);
            check("Haendler: Rohstoffe weg", a.getInventory().isEmpty(), "");
            var v = lvl.getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class, new net.minecraft.world.phys.AABB(a.blockPosition()).inflate(3));
            check("Haendler: Villager steht neben dem Kaeufer", !v.isEmpty(), "");
            if (!v.isEmpty()) gekauft[0] = v.get(0);
            check("Haendler: zaehlt fuer die Grenze", de.niklasmetzger.crownscore.handel.VillagerHandel.gekauft(d, sau.key()) == 1, "");
            double nachher = de.niklasmetzger.crownscore.handel.VillagerHandel.geldpreis(d, sau.key(), angebot);
            check("Haendler: naechster Kauf ist teurer", nachher > vorher, vorher + " -> " + nachher);
            err = de.niklasmetzger.crownscore.handel.VillagerHandel.kaufen(a, angebot, true);
            check("Haendler: ohne Rohstoffe kein Kauf", err != null, err);
            d.villagerGekauft.put(sau.key(), 5);
            err = de.niklasmetzger.crownscore.handel.VillagerHandel.kaufen(a, angebot, false);
            check("Haendler: Grenze erreicht", err != null && err.contains("Grenze"), err);
        });
        step("Stadt in Jordanien", () -> {
            CrownsData d = CrownsData.get(server);
            Nation jor = nat("JOR");
            dorf.apply(35.93, 31.95);
            String err = de.niklasmetzger.crownscore.handel.StadtService.gruenden(a, jor, "Testamman");
            check("Stadt: Amman beantragt", err == null, err);
            de.niklasmetzger.crownscore.handel.StadtService.annehmen(server, "Testamman", "Selbsttest", true);
            check("Stadt: Amman liegt in Jordanien", de.niklasmetzger.crownscore.handel.StadtService.imLand(d, "JOR").stream().anyMatch(s -> s.name.equals("Testamman")), "");
            krieg[0] = de.niklasmetzger.crownscore.war.WarService.declare(server, nat("SAU"), jor, de.niklasmetzger.crownscore.data.WarType.KRIEG, "Selbsttest der Staedte", "TestAlpha");
        });
        step("Ausserhalb der Stadt besetzen geht nicht", () -> {
            int x = (int) Math.floor(37.5 * bpg), z = (int) Math.floor(-30.5 * bpg);
            lvl.getChunk(x >> 4, z >> 4);
            a.setPos(x + 0.5, lvl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z + 0.5);
            String err = de.niklasmetzger.crownscore.world.EroberungService.besetzen(a, nat("SAU"));
            check("Besetzen: Land mit Staedten nur ueber die Staedte", err != null && err.contains("Testamman"), err);
        });
        step("Stadt besetzen", () -> {
            CrownsData d = CrownsData.get(server);
            var s = de.niklasmetzger.crownscore.handel.StadtService.byName(d, "Testamman");
            int bx = s.pos.getX() + 3, bz = s.pos.getZ() - 3;
            a.setPos(bx + 0.5, lvl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz), bz + 0.5);
            de.niklasmetzger.crownscore.config.CrownsConfig.EROBERUNG_VERTEIDIGER_ONLINE.set(false);
            // eigenes Startgeld, die Suite darf nicht vom Stand frueherer Laeufe abhaengen
            de.niklasmetzger.crownscore.treasury.TreasuryService.credit(d, nat("SAU").key(), 1.0E6);
            String err = de.niklasmetzger.crownscore.world.EroberungService.besetzen(a, nat("SAU"));
            check("Besetzen: Block in Amman gesetzt", err == null, err);
            var b = d.besatzungen.isEmpty() ? null : d.besatzungen.get(0);
            check("Besetzen: Ziel ist die Stadt", b != null && "Testamman".equals(b.stadt), b == null ? "" : b.stadt);
            if (b != null) b.rest = 3;
        });
        stepWait(100, "warten", () -> {});
        stepUntil("Karte", () -> !de.niklasmetzger.crownscore.claims.OpacTerritory.running(), 4000);
        step("Ganzes Land", () -> {
            CrownsData d = CrownsData.get(server);
            Nation sau = nat("SAU");
            var s = de.niklasmetzger.crownscore.handel.StadtService.byName(d, "Testamman");
            check("Eroberung: Amman gehoert Saudi-Arabien", s != null && s.nation.equals(sau.key()), s == null ? "" : s.nation);
            check("Eroberung: einzige Stadt erobert, ganz Jordanien gehoert Saudi-Arabien", sau.laender.contains("JOR"), String.valueOf(sau.laender));
            check("Postfach: Jordanien hat die Meldung", de.niklasmetzger.crownscore.handel.Postfach.von(d, jorBuerger).stream().anyMatch(n -> n.titel.contains("Stadt verloren")), "");
        });
        step("Aufraeumen", () -> {
            CrownsData d = CrownsData.get(server);
            de.niklasmetzger.crownscore.config.CrownsConfig.EROBERUNG_VERTEIDIGER_ONLINE.set(online[0]);
            if (krieg[0] != null) de.niklasmetzger.crownscore.war.WarService.end(server, krieg[0], "Selbsttest beendet", false);
            Nation sau = nat("SAU"), jor = nat("JOR");
            de.niklasmetzger.crownscore.world.WeltkarteCommand.uebertragen(server, List.of("JOR"), jor, null, LOG::info);
            d.staedte.removeIf(s -> s.name.startsWith("Test"));
            d.landSperre.clear();
            d.villagerGekauft.remove(sau.key());
            d.villagerNachfrage.remove(sau.key());
            d.nachrichten.removeIf(n -> A_ID.equals(n.an) || jorBuerger.equals(n.an));
            jor.mitglieder.remove(jorBuerger);
            jor.gegruendet = gegruendet[0];
            sau.mitglieder.remove(A_ID);
            sau.chef = null;
            if (gekauft[0] != null) gekauft[0].discard();
            for (BlockPos p : gesetzt) {
                lvl.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                lvl.setChunkForced(p.getX() >> 4, p.getZ() >> 4, false);
            }
            d.setDirty();
            check("Aufraeumen: Jordanien wieder bei Jordanien", jor.laender.contains("JOR"), "");
        });
        stepUntil("Karte zurueck", () -> !de.niklasmetzger.crownscore.claims.OpacTerritory.running(), 4000);
    }

    private static int[] rgb(java.awt.image.BufferedImage img, int x, int y) {
        int c = img.getRGB(x, y);
        return new int[]{(c >> 16) & 0xff, (c >> 8) & 0xff, c & 0xff};
    }

    /** Flaggen und Umhaenge: Landesflaggen, Umhang-Bild, Zuordnung, Upload ueber den Datenaustausch, Freigabe. */
    private void flagge(MinecraftServer srv) {
        server = srv;
        lvl = srv.overworld();
        pass = fail = 0;
        steps.clear();
        LOG.info("[CCTEST] START Flagge");
        if (a == null) a = FakePlayerFactory.get(lvl, new GameProfile(A_ID, "TestAlpha"));
        final String[] code = {null};
        final String[] alt = {null, null, null};
        final UUID buerger = UUID.fromString("00000000-0000-0000-0000-00000000f1a6");
        step("Landesflaggen", () -> {
            int n = de.niklasmetzger.crownscore.flag.FlagStore.landesflaggen();
            check("Flagge: mindestens 235 Landesflaggen (alle Laender mit Flaeche ausser Siachen, Somaliland, Nordzypern)", n >= 235, String.valueOf(n));
            check("Flagge: Saudi-Arabien hat eine Landesflagge", de.niklasmetzger.crownscore.flag.FlagStore.land("SAU") != null, "");
        });
        step("Umhang-Bild", () -> {
            String h = de.niklasmetzger.crownscore.flag.CapeService.texture(de.niklasmetzger.crownscore.flag.FlagStore.land("DEU"));
            byte[] png = de.niklasmetzger.crownscore.flag.CapeService.texturePng(h);
            check("Umhang: Textur fuer Deutschland erzeugt", png != null, h);
            if (png == null) return;
            try {
                var img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
                int s = de.niklasmetzger.crownscore.flag.FlagImages.SCALE, fw = de.niklasmetzger.crownscore.flag.FlagImages.FACE_W;
                check("Umhang: Textur ist 512 x 256", img.getWidth() == 64 * s && img.getHeight() == 32 * s, img.getWidth() + "x" + img.getHeight());
                int[] links = rgb(img, s + 4, s + 64), mitte = rgb(img, s + fw / 2, s + 64), rechts = rgb(img, s + fw - 5, s + 64);
                check("Umhang: gedreht, links Gold", links[0] > 200 && links[1] > 150 && links[2] < 80, java.util.Arrays.toString(links));
                check("Umhang: Mitte Rot", mitte[0] > 180 && mitte[1] < 60, java.util.Arrays.toString(mitte));
                check("Umhang: rechts Schwarz (Mastseite oben)", rechts[0] < 40 && rechts[1] < 40 && rechts[2] < 40, java.util.Arrays.toString(rechts));
            } catch (Exception e) {
                check("Umhang: Textur lesbar", false, e.toString());
            }
        });
        step("Zuordnung", () -> {
            CrownsData d = CrownsData.get(server);
            Nation deu = nat("DEU");
            deu.mitglieder.put(buerger, "Flaggenbuerger");
            String erwartet = de.niklasmetzger.crownscore.flag.CapeService.texture(de.niklasmetzger.crownscore.flag.FlagStore.land("DEU"));
            check("Umhang: Buerger traegt die Landesflagge", erwartet.equals(de.niklasmetzger.crownscore.flag.CapeService.hashFor(d, buerger)), "");
            check("Umhang: Staatenlose tragen keinen", de.niklasmetzger.crownscore.flag.CapeService.hashFor(d, UUID.randomUUID()).isEmpty(), "");
            de.niklasmetzger.crownscore.flag.FlagStore.setAus(buerger, true);
            check("Umhang: /umhang aus schaltet ihn ab", de.niklasmetzger.crownscore.flag.CapeService.hashFor(d, buerger).isEmpty(), "");
            de.niklasmetzger.crownscore.flag.FlagStore.setAus(buerger, false);
        });
        step("Upload", () -> {
            alt[0] = de.niklasmetzger.crownscore.config.CrownsConfig.RECHTE_EDITOR_URL.get();
            alt[1] = de.niklasmetzger.crownscore.config.CrownsConfig.UMHANG_FREIGABE.get().toString();
            de.niklasmetzger.crownscore.config.CrownsConfig.RECHTE_EDITOR_URL.set("http://127.0.0.1:8096/editor/");
            de.niklasmetzger.crownscore.config.CrownsConfig.UMHANG_FREIGABE.set(true);
            try {
                // eigene Flagge: blau mit weissem Kreuz, wie sie die Upload-Seite schicken wuerde
                var img = new java.awt.image.BufferedImage(300, 200, java.awt.image.BufferedImage.TYPE_INT_RGB);
                var g = img.createGraphics();
                g.setColor(new java.awt.Color(20, 60, 200));
                g.fillRect(0, 0, 300, 200);
                g.setColor(java.awt.Color.WHITE);
                g.fillRect(130, 0, 40, 200);
                g.fillRect(0, 80, 300, 40);
                g.dispose();
                byte[] body = de.niklasmetzger.crownscore.flag.FlagImages.png(img);
                var res = java.net.http.HttpClient.newHttpClient().send(java.net.http.HttpRequest.newBuilder(
                                java.net.URI.create("http://127.0.0.1:8096/data/post")).header("Content-Type", "image/png")
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(body)).build(), java.net.http.HttpResponse.BodyHandlers.ofString());
                code[0] = com.google.gson.JsonParser.parseString(res.body()).getAsJsonObject().get("key").getAsString();
                check("Upload: Datenaustausch gibt einen Code", code[0] != null && !code[0].isEmpty(), code[0]);
                check("Upload: Server kann das Bild abholen", de.niklasmetzger.crownscore.flag.FlagCommand.download(code[0]).length == body.length, "");
            } catch (Exception e) {
                check("Upload: Datenaustausch erreichbar (Testserver auf 8096)", false, e.toString());
                return;
            }
            Nation deu = nat("DEU");
            alt[2] = deu.chef == null ? "" : deu.chef.toString();
            deu.chef = a.getUUID();
            deu.mitglieder.put(a.getUUID(), "TestAlpha");
            server.getCommands().performPrefixedCommand(a.createCommandSourceStack(), "nation flagge anwenden " + code[0]);
        });
        stepUntil("Eingereicht", () -> de.niklasmetzger.crownscore.flag.FlagStore.wartend(nat("DEU")) != null, 200);
        step("Freigabe", () -> {
            CrownsData d = CrownsData.get(server);
            Nation deu = nat("DEU");
            String land = de.niklasmetzger.crownscore.flag.CapeService.texture(de.niklasmetzger.crownscore.flag.FlagStore.land("DEU"));
            check("Freigabe: Flagge wartet, Buerger tragen noch die Landesflagge", de.niklasmetzger.crownscore.flag.FlagStore.wartend(deu) != null
                    && land.equals(de.niklasmetzger.crownscore.flag.CapeService.hashFor(d, buerger)), "");
            cmd("cccore flaggen annehmen Deutschland");
            String neu = de.niklasmetzger.crownscore.flag.CapeService.hashFor(d, buerger);
            check("Freigabe: nach /cccore flaggen annehmen traegt der Buerger die neue Flagge", !neu.isEmpty() && !neu.equals(land)
                    && de.niklasmetzger.crownscore.flag.FlagStore.eigene(deu) != null, neu);
            server.getCommands().performPrefixedCommand(a.createCommandSourceStack(), "nation flagge entfernen");
            check("Entfernen: wieder die Landesflagge", land.equals(de.niklasmetzger.crownscore.flag.CapeService.hashFor(d, buerger)), "");
            // aufraeumen
            deu.chef = alt[2] == null || alt[2].isEmpty() ? null : UUID.fromString(alt[2]);
            deu.mitglieder.remove(a.getUUID());
            deu.mitglieder.remove(buerger);
            de.niklasmetzger.crownscore.config.CrownsConfig.RECHTE_EDITOR_URL.set(alt[0]);
            de.niklasmetzger.crownscore.config.CrownsConfig.UMHANG_FREIGABE.set(Boolean.parseBoolean(alt[1]));
        });
    }

    private void start(MinecraftServer srv) {
        server = srv;
        lvl = srv.overworld();
        base = new BlockPos(3000, 120, 3000);
        pass = fail = 0;
        startMs = System.currentTimeMillis();
        steps.clear();
        LOG.info("[CCTEST] START");

        // ---- Aufbau ------------------------------------------------------
        stepWait(5, "Aufbau", () -> {
            // Chunks festhalten: ohne Spieler in der Naehe wuerden sie zwischen den Schritten entladen
            for (int cx = -1; cx <= 1; cx++) for (int cz = -1; cz <= 1; cz++) lvl.setChunkForced((base.getX() >> 4) + cx, (base.getZ() >> 4) + cz, true);
            for (int x = -8; x <= 8; x++) {
                for (int z = -8; z <= 8; z++) {
                    lvl.setBlock(at(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
                    for (int y = 0; y <= 6; y++) lvl.setBlock(at(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
            a = FakePlayerFactory.get(lvl, new GameProfile(A_ID, "TestAlpha"));
            b = FakePlayerFactory.get(lvl, new GameProfile(B_ID, "TestBeta"));
            a.setPos(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
            b.setPos(base.getX() + 2.5, base.getY(), base.getZ() + 0.5);
            CrownsData d = CrownsData.get(server);
            for (War w : d.wars) {
                if (w.aktiv && (w.beteiligt(Nation.key("Testland Alpha")) || w.beteiligt(Nation.key("Testland Beta")))) w.aktiv = false;
            }
            d.nations.remove(Nation.key("Testland Alpha"));
            d.nations.remove(Nation.key("Testland Beta"));
            na = new Nation("Testland Alpha", "TSA");
            na.chef = a.getUUID();
            na.mitglieder.put(a.getUUID(), "TestAlpha");
            nb = new Nation("Testland Beta", "TSB");
            nb.mitglieder.put(b.getUUID(), "TestBeta");
            d.nations.put(na.key(), na);
            d.nations.put(nb.key(), nb);
            d.setDirty();
            ModeService.reset(a);
            ModeService.reset(b);
            check("Aufbau", true, "");
        });

        step("Nation vor dem Namen im Chat", () -> {
            ModeService.refresh(a);
            ModeService.refresh(b);
            String na = a.getDisplayName().getString().replaceAll("§.", "");
            String nbn = b.getDisplayName().getString().replaceAll("§.", "");
            check("Nation vor dem Namen im Chat", na.startsWith("[Testland Alpha]") && nbn.startsWith("[Testland Beta]"), "\"" + na + "\", \"" + nbn + "\"");
        });

        // ---- PvP und Modi ------------------------------------------------
        step("Ohne Krieg kein PvP", () -> check("Ohne Krieg kein PvP", blocked(a, b), ""));
        step("Krieg erklaeren", () -> {
            WarService.declare(server, na, nb, WarType.KRIEG, "Selbsttest der PvP-Regeln", "TestAlpha");
            check("Krieg erklaeren", CrownsData.get(server).activeWar(na.key(), nb.key()) != null, "");
        });
        step("Krieg + beide RP: Angriff erlaubt", () -> check("Krieg + beide RP: Angriff erlaubt", !blocked(a, b), ""));
        step("Ziel im Farm-Modus: Angriff gesperrt", () -> {
            ModeService.gruendeVergessen(a.getUUID()); // Reste eines frueheren Laufs
            ModeService.set(b, PlayerMode.FARM, 0);
            check("Ziel im Farm-Modus: Angriff gesperrt", blocked(a, b), "");
        });
        step("Ziel im AFK-Modus: Angriff gesperrt", () -> {
            ModeService.set(b, PlayerMode.AFK, System.currentTimeMillis() + 3_600_000L);
            check("Ziel im AFK-Modus: Angriff gesperrt", blocked(a, b), "");
        });
        step("Mit Grund: Angriff erlaubt", () -> {
            ModeService.grund(a.getUUID(), b.getUUID());
            check("Mit Grund: Angriff erlaubt", !blocked(a, b), "");
        });
        step("Grund gilt nur fuer dieses Ziel", () -> {
            ModeService.set(a, PlayerMode.FARM, 0);
            check("Grund gilt nur fuer dieses Ziel", blocked(b, a), "Beta greift Alpha (Farm) ohne Grund an");
        });
        step("Angreifer im Farm-Modus landet im RP", () -> {
            ModeService.set(b, PlayerMode.RP, 0);
            boolean allowed = !blocked(a, b);
            check("Angreifer im Farm-Modus landet im RP", allowed && ModeService.mode(a.getUUID()) == PlayerMode.RP, "Modus jetzt " + ModeService.mode(a.getUUID()));
        });
        step("Kampfsperre nach Angriff", () -> check("Kampfsperre nach Angriff", ModeService.kampfSperre(a.getUUID()) > 0,
                ModeService.kampfSperre(a.getUUID()) + " s"));
        stepWait(100, "AFK-Parkscheibe setzen", () -> ModeService.set(b, PlayerMode.AFK, System.currentTimeMillis() + 500));
        step("AFK laeuft ab und faellt auf RP", () -> check("AFK laeuft ab und faellt auf RP", ModeService.mode(b.getUUID()) == PlayerMode.RP,
                "Modus " + ModeService.mode(b.getUUID())));
        step("Tabliste zeigt Modus", () -> {
            String tab = a.getTabListDisplayName() == null ? "" : a.getTabListDisplayName().getString();
            check("Tabliste zeigt Modus", tab.contains("RP"), "\"" + tab + "\"");
        });

        // ---- Blockprotokoll ----------------------------------------------
        step("Bloecke setzen und abbauen", () -> {
            place(a, at(1, 0, -2), new ItemStack(Items.OAK_PLANKS, 16));
            place(a, at(2, 0, -2), new ItemStack(Items.OAK_PLANKS, 16));
            place(a, at(3, 0, -2), new ItemStack(Items.OAK_PLANKS, 16));
            a.gameMode.destroyBlock(at(0, -1, 3));
            check("Bloecke setzen und abbauen", lvl.getBlockState(at(1, 0, -2)).is(Blocks.OAK_PLANKS) && lvl.getBlockState(at(0, -1, 3)).isAir(),
                    "Planke=" + lvl.getBlockState(at(1, 0, -2)) + ", Boden=" + lvl.getBlockState(at(0, -1, 3)));
        });
        step("Kiste: Diamanten herausnehmen", () -> {
            BlockPos cp = at(-3, 0, 0);
            lvl.setBlock(cp, Blocks.CHEST.defaultBlockState(), 3);
            ChestBlockEntity ch = (ChestBlockEntity) lvl.getBlockEntity(cp);
            ch.setItem(0, new ItemStack(Items.DIAMOND, 5));
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(cp), Direction.UP, cp, false);
            ForgeHooks.onRightClickBlock(a, InteractionHand.MAIN_HAND, cp, hit);
            a.openMenu(ch);
            a.containerMenu.getSlot(0).set(new ItemStack(Items.DIAMOND, 2));
            a.closeContainer();
            check("Kiste: Diamanten herausnehmen", diamonds(cp) == 2, diamonds(cp) + " Diamanten");
        });
        step("Kuh toeten", () -> {
            Cow cow = EntityType.COW.create(lvl);
            cow.setPos(base.getX() + 0.5, base.getY(), base.getZ() - 4.5);
            lvl.addFreshEntity(cow);
            int before = cows();
            cow.hurt(a.damageSources().playerAttack(a), 1000f);
            cow.discard(); // Leiche sofort weg, sonst zaehlt sie noch
            cowsAfterKill = cows();
            check("Kuh toeten", cowsAfterKill == before - 1, before + " -> " + cowsAfterKill);
        });
        stepWait(40, "Explosion", () -> {
            for (int x = 4; x <= 6; x++) lvl.setBlock(at(x, 0, 5), Blocks.DIRT.defaultBlockState(), 3);
            lvl.explode(null, base.getX() + 5.5, base.getY() + 0.5, base.getZ() + 5.5, 2.5f, Level.ExplosionInteraction.TNT);
            check("Explosion", lvl.getBlockState(at(5, 0, 5)).isAir(), "");
        });
        step("Protokoll: gesetzte Bloecke", () -> countLog("Protokoll: gesetzte Bloecke", "u = ? AND a = 1 AND t >= ?", List.of("TestAlpha", startMs), 3));
        step("Protokoll: abgebauter Block", () -> countLog("Protokoll: abgebauter Block", "u = ? AND a = 0", List.of("TestAlpha"), 1));
        step("Protokoll: Kiste", () -> countLog("Protokoll: Kiste", "u = ? AND a = 10 AND amount = 3", List.of("TestAlpha"), 1));
        step("Protokoll: Toetung", () -> countLog("Protokoll: Toetung", "u = ? AND a = 20 AND blk = 'minecraft:cow'", List.of("TestAlpha"), 1));
        stepWait(20, "Protokoll: Explosion", () -> countLog("Protokoll: Explosion", "u = ? AND a = 0", List.of("#explosion"), 3));

        // ---- Rollback, Restore, Undo -------------------------------------
        stepWait(60, "Rollback starten", () -> cmd("cccore admin rollback u:TestAlpha r:#global " + since()));
        step("Rollback: Bloecke, Kiste, Kuh", () -> {
            boolean planks = lvl.getBlockState(at(1, 0, -2)).isAir() && lvl.getBlockState(at(3, 0, -2)).isAir();
            boolean floor = lvl.getBlockState(at(0, -1, 3)).is(Blocks.STONE);
            int dia = diamonds(at(-3, 0, 0));
            int cows = cows();
            check("Rollback: gesetzte Planken weg", planks, "");
            check("Rollback: abgebauter Boden zurueck", floor, lvl.getBlockState(at(0, -1, 3)).toString());
            check("Rollback: Diamanten zurueck in der Kiste", dia == 5, dia + " Diamanten");
            check("Rollback: Kuh wieder da", cows == cowsAfterKill + 1, cowsAfterKill + " -> " + cows + " Kuehe");
        });
        stepWait(60, "Restore starten", () -> cmd("cccore admin restore u:TestAlpha r:#global " + since()));
        step("Restore", () -> {
            check("Restore: Planken wieder da", lvl.getBlockState(at(1, 0, -2)).is(Blocks.OAK_PLANKS), "");
            check("Restore: Boden wieder abgebaut", lvl.getBlockState(at(0, -1, 3)).isAir(), "");
            check("Restore: Diamanten wieder heraus", diamonds(at(-3, 0, 0)) == 2, diamonds(at(-3, 0, 0)) + " Diamanten");
            check("Restore: Kuh wieder weg", cows() == cowsAfterKill, cows() + " Kuehe, erwartet " + cowsAfterKill);
        });
        stepWait(60, "Undo starten", () -> cmd("cccore admin undo"));
        step("Undo macht Restore rueckgaengig", () -> check("Undo macht Restore rueckgaengig",
                lvl.getBlockState(at(1, 0, -2)).isAir() && lvl.getBlockState(at(0, -1, 3)).is(Blocks.STONE), ""));
        stepWait(60, "Explosion zuruecksetzen", () -> cmd("cccore admin rollback u:#explosion r:#global " + since()));
        step("Explosion zurueckgesetzt", () -> check("Explosion zurueckgesetzt", lvl.getBlockState(at(5, 0, 5)).is(Blocks.DIRT),
                lvl.getBlockState(at(5, 0, 5)).toString()));
        step("Lookup-Ausgabe", () -> cmd("cccore admin lookup u:TestAlpha " + since()));

        // ---- Inspektor ---------------------------------------------------
        step("Inspektor faengt Klick ab", () -> {
            Inspector.toggle(a.getUUID());
            PlayerInteractEvent.LeftClickBlock ev = new PlayerInteractEvent.LeftClickBlock(a, at(0, -1, 3), Direction.UP,
                    PlayerInteractEvent.LeftClickBlock.Action.START);
            boolean canceled = MinecraftForge.EVENT_BUS.post(ev);
            Inspector.toggle(a.getUUID());
            check("Inspektor faengt Klick ab", canceled, "");
        });

        // ---- Befehlssichtbarkeit und Rechte ------------------------------
        stepOhneLp("Sichtbarkeit nach Rolle", () -> {
            CommandSourceStack sa = a.createCommandSourceStack();
            CommandSourceStack sb = b.createCommandSourceStack();
            CommandSourceStack console = server.createCommandSourceStack();
            check("Admin-Befehle fuer Spieler unsichtbar", !canUse(sb, "cccore", "admin") && !canUse(sb, "cccore", "perms"), "");
            check("Admin-Befehle fuer Konsole sichtbar", canUse(console, "cccore", "admin"), "");
            check("Staatschef sieht /krieg erklaeren", canUse(sa, "krieg", "erklaeren"), "");
            check("Buerger sieht /krieg erklaeren nicht", !canUse(sb, "krieg", "erklaeren"), "");
            check("Buerger sieht /krieg liste", canUse(sb, "krieg", "liste"), "");
            check("/aktivitaet ja, /aktivitaet <Spieler> nein", canUse(sb, "aktivitaet") && !canUse(sb, "aktivitaet", "<arg>"), "");
            check("/pause fuer Spieler unsichtbar", !canUse(sb, "pause"), "");
            check("/gamemode ohne OP unsichtbar", !canUse(sb, "gamemode"), "");
            long sichtbar = server.getCommands().getDispatcher().getRoot().getChildren().stream().filter(n -> n.canUse(sb)).count();
            long alle = server.getCommands().getDispatcher().getRoot().getChildren().size();
            check("Befehlsbaum fuer Buerger gefiltert", sichtbar < alle, sichtbar + " von " + alle + " Wurzelbefehlen sichtbar");
        });
        stepOhneLp("Recht schaltet Befehl frei", () -> {
            CommandSourceStack sb = b.createCommandSourceStack();
            PermData.User u = PermissionManager.userOrCreate(B_ID, "TestBeta");
            u.permissions.add(new PermData.Node("crownscore.krieg.erklaeren", true, 0, ""));
            u.permissions.add(new PermData.Node("command.gamemode", true, 0, ""));
            u.permissions.add(new PermData.Node("crownscore.krieg.liste", false, 0, ""));
            PermissionManager.changed();
            check("Recht crownscore.krieg.erklaeren schaltet frei", canUse(sb, "krieg", "erklaeren"), "");
            check("Recht command.gamemode schaltet Vanilla-Befehl frei", canUse(sb, "gamemode"), "");
            check("Recht = false sperrt /krieg liste", !canUse(sb, "krieg", "liste"), "");
        });
        stepOhneLp("Gruppen, Vererbung, Platzhalter, Praefix", () -> {
            PermData d = PermissionManager.data();
            PermData.Group g = new PermData.Group();
            g.weight = 10;
            g.prefix = "&c[Test]";
            g.permissions.add(new PermData.Node("crownscore.cccore.admin.*", true, 0, ""));
            d.groups.put("cctest", g);
            PermData.User ua = PermissionManager.userOrCreate(A_ID, "TestAlpha");
            ua.parents.add(new PermData.Parent("cctest", 0));
            PermissionManager.changed();
            CommandSourceStack sa = a.createCommandSourceStack();
            ModeService.refresh(a);
            check("Platzhalter crownscore.cccore.admin.* gibt KEINEN Admin-Befehl frei", !canUse(sa, "cccore", "admin", "lookup"), "");
            // Gruppe mit hoeherem Gewicht verbietet per Platzhalter, niedrigere erlaubt genau: das Verbot gewinnt
            PermData.Group sperre = new PermData.Group();
            sperre.weight = 50;
            sperre.permissions.add(new PermData.Node("crownscore.cccore.admin.*", false, 0, ""));
            d.groups.put("cctest_sperre", sperre);
            g.permissions.add(new PermData.Node("crownscore.cccore.admin.lookup", true, 0, ""));
            ua.parents.add(new PermData.Parent("cctest_sperre", 0));
            PermissionManager.changed();
            check("Platzhalter-Verbot der hoeheren Gruppe schlaegt genaues Recht der niedrigeren", !canUse(sa, "cccore", "admin", "lookup"), "");
            ua.parents.removeIf(x -> "cctest_sperre".equals(x.group));
            d.groups.remove("cctest_sperre");
            PermissionManager.changed();
            check("Genauer Knoten crownscore.cccore.admin.lookup gibt Zugriff", canUse(sa, "cccore", "admin", "lookup"), "");
            // *=false in einer Gruppe entscheidet nie, ob jemand Admin ist (sonst waeren alle Admins ausgesperrt)
            PermData.Node allesAus = new PermData.Node("*", false, 0, "");
            g.permissions.add(allesAus);
            PermissionManager.changed();
            String dimA = a.level().dimension().location().toString();
            check("*=false laesst crownscore.admin offen", PermissionManager.check(A_ID, de.niklasmetzger.crownscore.util.Perms.ADMIN_NODE, dimA) == PermissionManager.Tri.UNDEFINED, "");
            check("*=false sperrt fremde Befehle (command.msg)", PermissionManager.check(A_ID, "command.msg", dimA) == PermissionManager.Tri.FALSE, "");
            g.permissions.remove(allesAus);
            PermissionManager.changed();
            check("Nation und Praefix im Anzeigenamen", a.getDisplayName().getString().replaceAll("§.", "").equals("[Testland Alpha] [Test] TestAlpha"), "\"" + a.getDisplayName().getString() + "\"");
            check("Forge-PermissionAPI nutzt crownscore:permissions",
                    "crownscore:permissions".equals(String.valueOf(PermissionAPI.getActivePermissionHandler())), String.valueOf(PermissionAPI.getActivePermissionHandler()));
        });

        // ---- Aufraeumen --------------------------------------------------
        step("Aufraeumen", () -> {
            CrownsData d = CrownsData.get(server);
            for (War w : d.wars) {
                if (w.aktiv && w.beteiligt(na.key())) WarService.end(server, w, "Selbsttest beendet", false);
            }
            d.nations.remove(na.key());
            d.nations.remove(nb.key());
            d.setDirty();
            PermissionManager.data().groups.remove("cctest");
            PermissionManager.data().users.remove(A_ID.toString());
            PermissionManager.data().users.remove(B_ID.toString());
            PermissionManager.changed();
            ModeService.forget(A_ID);
            ModeService.forget(B_ID);
            for (int cx = -1; cx <= 1; cx++) for (int cz = -1; cz <= 1; cz++) lvl.setChunkForced((base.getX() >> 4) + cx, (base.getZ() >> 4) + cz, false);
            check("Aufraeumen", true, "");
        });
    }
}
