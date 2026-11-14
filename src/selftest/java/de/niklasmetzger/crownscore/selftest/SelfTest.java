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
        e.getDispatcher().register(Commands.literal("cctest").requires(s -> s.hasPermission(4)).executes(ctx -> {
            start(ctx.getSource().getServer());
            return 1;
        }));
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

        // ---- PvP und Modi ------------------------------------------------
        step("Ohne Krieg kein PvP", () -> check("Ohne Krieg kein PvP", blocked(a, b), ""));
        step("Krieg erklaeren", () -> {
            WarService.declare(server, na, nb, WarType.KRIEG, "Selbsttest der PvP-Regeln", "TestAlpha");
            check("Krieg erklaeren", CrownsData.get(server).activeWar(na.key(), nb.key()) != null, "");
        });
        step("Krieg + beide RP: Angriff erlaubt", () -> check("Krieg + beide RP: Angriff erlaubt", !blocked(a, b), ""));
        step("Ziel im Farm-Modus: Angriff gesperrt", () -> {
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
        step("Sichtbarkeit nach Rolle", () -> {
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
        step("Recht schaltet Befehl frei", () -> {
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
        step("Gruppen, Vererbung, Platzhalter, Praefix", () -> {
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
            check("Platzhalter crownscore.cccore.admin.* gibt Zugriff", canUse(sa, "cccore", "admin", "lookup"), "");
            check("Praefix im Anzeigenamen", a.getDisplayName().getString().contains("[Test]"), "\"" + a.getDisplayName().getString() + "\"");
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
