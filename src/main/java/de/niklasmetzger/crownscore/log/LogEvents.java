package de.niklasmetzger.crownscore.log;

import de.niklasmetzger.crownscore.config.CrownsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Sammelt alle protokollierten Ereignisse und reicht sie an die Datenbank weiter. */
public class LogEvents {
    /** Zuletzt angeklickter Block je Spieler, fuer die Zuordnung geoeffneter Behaelter. */
    private final Map<UUID, BlockPos> lastClick = new HashMap<>();
    private final Map<UUID, Long> lastClickTime = new HashMap<>();
    /** Inhalt beim Oeffnen eines Behaelters. */
    private final Map<UUID, OpenContainer> open = new HashMap<>();
    /** Aenderungen, die erst im naechsten Tick sichtbar sind (Eimer, Feuerzeug). */
    private final List<Deferred> deferred = new ArrayList<>();

    private record OpenContainer(ServerLevel level, BlockPos pos, Map<String, Agg> before) {}

    private static final class Agg {
        final ItemStack proto;
        int count;

        Agg(ItemStack proto) {
            this.proto = proto;
        }
    }

    private record Deferred(ServerLevel level, String user, List<BlockPos> pos, List<BlockState> before, int tick) {}

    private int tick;

    private static boolean on() {
        return CrownsConfig.LOG_AKTIV.get() && LogDatabase.accepting();
    }

    // ---- Hilfen -----------------------------------------------------------

    public static String dim(Level level) {
        return level.dimension().location().toString();
    }

    public static String stateString(BlockState s) {
        return NbtUtils.writeBlockState(s).toString();
    }

    public static String blockId(BlockState s) {
        return BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
    }

    static String beNbt(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be == null ? null : be.saveWithFullMetadata().toString();
    }

    static String userOf(Entity e) {
        if (e == null) return "#unbekannt";
        if (e instanceof Player p) return p.getGameProfile().getName();
        return "#" + EntityType.getKey(e.getType()).getPath();
    }

    private static void block(Level level, String user, BlockPos pos, LogAction a, BlockState oldS, BlockState newS, String oldNbt, String newNbt) {
        if (!(level instanceof ServerLevel)) return;
        LogEntry e = LogEntry.of(System.currentTimeMillis(), user, dim(level), pos.getX(), pos.getY(), pos.getZ(), a);
        BlockState shown = a == LogAction.PLACE ? newS : oldS;
        e.blk = blockId(shown);
        e.oldS = stateString(oldS);
        e.newS = stateString(newS);
        e.oldNbt = oldNbt;
        e.newNbt = newNbt;
        LogDatabase.add(e);
    }

    /** Abgebauter Block plus die zweite Haelfte bei Tueren, hohen Pflanzen und Betten. */
    private static void breakWithHalves(Level level, String user, BlockPos pos, BlockState state) {
        block(level, user, pos, LogAction.BREAK, state, state.getFluidState().createLegacyBlock(), beNbt(level, pos), null);
        BlockPos other = null;
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            other = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
        } else if (state.getBlock() instanceof BedBlock && state.hasProperty(BlockStateProperties.BED_PART)) {
            Direction f = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            other = state.getValue(BlockStateProperties.BED_PART) == BedPart.FOOT ? pos.relative(f) : pos.relative(f.getOpposite());
        }
        if (other != null) {
            BlockState os = level.getBlockState(other);
            if (os.getBlock() == state.getBlock()) {
                block(level, user, other, LogAction.BREAK, os, os.getFluidState().createLegacyBlock(), beNbt(level, other), null);
            }
        }
    }

    // ---- Bloecke ----------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBreak(BlockEvent.BreakEvent event) {
        if (!on() || event.isCanceled() || !(event.getLevel() instanceof Level level)) return;
        breakWithHalves(level, event.getPlayer().getGameProfile().getName(), event.getPos(), event.getState());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!on() || event.isCanceled() || !(event.getLevel() instanceof Level level)) return;
        String user = userOf(event.getEntity());
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multi) {
            for (BlockSnapshot snap : multi.getReplacedBlockSnapshots()) {
                CompoundTag old = snap.getTag();
                block(level, user, snap.getPos(), LogAction.PLACE, snap.getReplacedBlock(), snap.getCurrentBlock(),
                        old == null ? null : old.toString(), beNbt(level, snap.getPos()));
            }
        } else {
            BlockSnapshot snap = event.getBlockSnapshot();
            CompoundTag old = snap.getTag();
            block(level, user, event.getPos(), LogAction.PLACE, snap.getReplacedBlock(), event.getPlacedBlock(),
                    old == null ? null : old.toString(), beNbt(level, event.getPos()));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onExplosion(ExplosionEvent.Detonate event) {
        if (!on()) return;
        Level level = event.getLevel();
        LivingEntity ind = event.getExplosion().getIndirectSourceEntity();
        String user = ind instanceof Player p ? p.getGameProfile().getName()
                : event.getExplosion().getExploder() != null ? userOf(event.getExplosion().getExploder()) : "#explosion";
        for (BlockPos pos : event.getAffectedBlocks()) {
            BlockState s = level.getBlockState(pos);
            if (s.isAir()) continue;
            block(level, user, pos, LogAction.BREAK, s, s.getFluidState().createLegacyBlock(), beNbt(level, pos), null);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBucket(FillBucketEvent event) {
        if (!on() || !(event.getLevel() instanceof ServerLevel level) || !(event.getTarget() instanceof BlockHitResult hit)) return;
        defer(level, event.getEntity().getGameProfile().getName(), hit.getBlockPos(), hit.getBlockPos().relative(hit.getDirection()));
    }

    private void defer(ServerLevel level, String user, BlockPos... pos) {
        List<BlockPos> list = new ArrayList<>();
        List<BlockState> before = new ArrayList<>();
        for (BlockPos p : pos) {
            list.add(p.immutable());
            before.add(level.getBlockState(p));
        }
        deferred.add(new Deferred(level, user, list, before, tick));
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tick++;
        if (deferred.isEmpty()) return;
        for (var it = deferred.iterator(); it.hasNext(); ) {
            Deferred d = it.next();
            if (d.tick() >= tick) continue;
            it.remove();
            for (int i = 0; i < d.pos().size(); i++) {
                BlockState now = d.level().getBlockState(d.pos().get(i));
                BlockState was = d.before().get(i);
                if (now == was) continue;
                block(d.level(), d.user(), d.pos().get(i), now.isAir() ? LogAction.BREAK : LogAction.PLACE, was, now, null, null);
            }
        }
    }

    // ---- Klicks und Behaelter ---------------------------------------------

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!on() || !(event.getEntity() instanceof ServerPlayer p) || event.isCanceled()) return;
        BlockPos pos = event.getPos();
        lastClick.put(p.getUUID(), pos.immutable());
        lastClickTime.put(p.getUUID(), System.currentTimeMillis());
        ServerLevel level = p.serverLevel();
        ItemStack held = event.getItemStack();
        if (held.getItem() instanceof FlintAndSteelItem || held.getItem() instanceof FireChargeItem) {
            defer(level, p.getGameProfile().getName(), pos, pos.relative(event.getFace() == null ? Direction.UP : event.getFace()));
        }
        if (CrownsConfig.LOG_KLICKS.get()) {
            BlockState s = level.getBlockState(pos);
            var b = s.getBlock();
            if (b instanceof DoorBlock || b instanceof TrapDoorBlock || b instanceof FenceGateBlock || b instanceof ButtonBlock || b instanceof LeverBlock) {
                LogEntry e = LogEntry.of(System.currentTimeMillis(), p.getGameProfile().getName(), dim(level), pos.getX(), pos.getY(), pos.getZ(), LogAction.CLICK);
                e.blk = blockId(s);
                LogDatabase.add(e);
            }
        }
    }

    /** Behaelter am Ort: Doppelkisten als Ganzes, sonst der Block selbst. */
    public static Container containerAt(ServerLevel level, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        if (st.getBlock() instanceof net.minecraft.world.level.block.ChestBlock cb) {
            Container c = net.minecraft.world.level.block.ChestBlock.getContainer(cb, st, level, pos, true);
            if (c != null) return c;
        }
        return level.getBlockEntity(pos) instanceof Container c ? c : null;
    }

    static String itemKey(ItemStack s) {
        return BuiltInRegistries.ITEM.getKey(s.getItem()) + (s.hasTag() ? s.getTag().toString() : "");
    }

    private static Map<String, Agg> snapshot(AbstractContainerMenu menu, Player p) {
        Map<String, Agg> out = new LinkedHashMap<>();
        for (Slot slot : menu.slots) {
            if (slot.container == p.getInventory()) continue;
            ItemStack s = slot.getItem();
            if (s.isEmpty()) continue;
            Agg a = out.computeIfAbsent(itemKey(s), k -> {
                ItemStack proto = s.copy();
                proto.setCount(1);
                return new Agg(proto);
            });
            a.count += s.getCount();
        }
        return out;
    }

    @SubscribeEvent
    public void onOpen(PlayerContainerEvent.Open event) {
        if (!on() || !(event.getEntity() instanceof ServerPlayer p)) return;
        BlockPos pos = lastClick.get(p.getUUID());
        Long t = lastClickTime.get(p.getUUID());
        if (pos == null || t == null || System.currentTimeMillis() - t > 3000) return;
        ServerLevel level = p.serverLevel();
        if (!(level.getBlockEntity(pos) instanceof Container)) return; // Endertruhe und Werkbank haben keinen eigenen Inhalt
        open.put(p.getUUID(), new OpenContainer(level, pos, snapshot(event.getContainer(), p)));
    }

    @SubscribeEvent
    public void onClose(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        OpenContainer oc = open.remove(p.getUUID());
        if (oc == null || !on()) return;
        diff(p, oc, snapshot(event.getContainer(), p));
    }

    private static void diff(ServerPlayer p, OpenContainer oc, Map<String, Agg> after) {
        Map<String, Agg> all = new LinkedHashMap<>(oc.before());
        for (Map.Entry<String, Agg> e : after.entrySet()) all.putIfAbsent(e.getKey(), e.getValue());
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Agg> e : all.entrySet()) {
            int b = oc.before().containsKey(e.getKey()) ? oc.before().get(e.getKey()).count : 0;
            int a = after.containsKey(e.getKey()) ? after.get(e.getKey()).count : 0;
            if (a == b) continue;
            LogEntry le = LogEntry.of(now, p.getGameProfile().getName(), dim(oc.level()), oc.pos().getX(), oc.pos().getY(), oc.pos().getZ(),
                    a > b ? LogAction.ADD : LogAction.REMOVE);
            ItemStack proto = e.getValue().proto;
            le.blk = BuiltInRegistries.ITEM.getKey(proto.getItem()).toString();
            le.amount = Math.abs(a - b);
            le.newNbt = proto.save(new CompoundTag()).toString();
            le.name = BuiltInRegistries.BLOCK.getKey(oc.level().getBlockState(oc.pos()).getBlock()).toString();
            LogDatabase.add(le);
        }
    }

    // ---- Items, Toetungen, Text -------------------------------------------

    private static void item(Player p, ItemStack s, LogAction a) {
        LogEntry e = LogEntry.of(System.currentTimeMillis(), p.getGameProfile().getName(), dim(p.level()),
                p.blockPosition().getX(), p.blockPosition().getY(), p.blockPosition().getZ(), a);
        e.blk = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
        e.amount = s.getCount();
        ItemStack proto = s.copy();
        e.newNbt = proto.save(new CompoundTag()).toString();
        LogDatabase.add(e);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onToss(ItemTossEvent event) {
        if (!on() || !CrownsConfig.LOG_ITEMS.get() || event.isCanceled()) return;
        if (event.getPlayer() instanceof ServerPlayer p) item(p, event.getEntity().getItem(), LogAction.DROP);
    }

    @SubscribeEvent
    public void onPickup(PlayerEvent.ItemPickupEvent event) {
        if (!on() || !CrownsConfig.LOG_ITEMS.get()) return;
        if (event.getEntity() instanceof ServerPlayer p) item(p, event.getStack(), LogAction.PICKUP);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDeath(LivingDeathEvent event) {
        if (!on() || event.isCanceled()) return;
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        Entity src = event.getSource().getEntity();
        if (src == null && event.getSource().getDirectEntity() instanceof Projectile pr) src = pr.getOwner();
        boolean playerInvolved = src instanceof Player || victim instanceof Player;
        if (!playerInvolved) return;
        BlockPos pos = victim.blockPosition();
        LogEntry e = LogEntry.of(System.currentTimeMillis(), src == null ? "#" + event.getSource().getMsgId() : userOf(src),
                dim(victim.level()), pos.getX(), pos.getY(), pos.getZ(), LogAction.KILL);
        e.blk = EntityType.getKey(victim.getType()).toString();
        e.name = victim instanceof Player vp ? vp.getGameProfile().getName() : victim.getName().getString();
        if (!(victim instanceof Player)) {
            CompoundTag tag = new CompoundTag();
            if (victim.save(tag)) e.oldNbt = tag.toString();
        }
        LogDatabase.add(e);
    }

    private static void text(ServerPlayer p, LogAction a, String msg) {
        LogEntry e = LogEntry.of(System.currentTimeMillis(), p.getGameProfile().getName(), dim(p.level()),
                p.blockPosition().getX(), p.blockPosition().getY(), p.blockPosition().getZ(), a);
        e.text = msg;
        LogDatabase.add(e);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onChat(ServerChatEvent event) {
        if (on() && CrownsConfig.LOG_CHAT.get() && !event.isCanceled()) text(event.getPlayer(), LogAction.CHAT, event.getRawText());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onCommand(CommandEvent event) {
        if (!on() || !CrownsConfig.LOG_BEFEHLE.get() || event.isCanceled()) return;
        if (event.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer p) {
            text(p, LogAction.COMMAND, "/" + event.getParseResults().getReader().getString());
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (on() && event.getEntity() instanceof ServerPlayer p) text(p, LogAction.LOGIN, "");
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        OpenContainer oc = open.remove(p.getUUID());
        if (oc != null && on()) diff(p, oc, snapshot(p.containerMenu, p));
        lastClick.remove(p.getUUID());
        lastClickTime.remove(p.getUUID());
        if (on()) text(p, LogAction.LOGOUT, "");
    }
}
