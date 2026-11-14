package de.niklasmetzger.crownscore.log;

import de.niklasmetzger.crownscore.CrownsCore;
import de.niklasmetzger.crownscore.config.CrownsConfig;
import de.niklasmetzger.crownscore.util.Msg;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Setzt Protokolleintraege zurueck (Rollback) oder wieder ein (Restore). Laeuft stueckweise im Server-Tick,
 * damit auch grosse Rollbacks den Server nicht einfrieren.
 */
public final class RollbackService {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    /** Letzter Vorgang je Ausloeser, fuer /cccore admin undo. */
    public record LastOp(List<Long> ids, boolean rollback) {}

    private static final class Job {
        final MinecraftServer server;
        final List<LogEntry> entries;
        final boolean rollback;
        final String key;
        final Consumer<String> report;
        final long start = System.currentTimeMillis();
        final List<Long> done = new ArrayList<>();
        int idx, blocks, items, entities, fehler;

        Job(MinecraftServer server, List<LogEntry> entries, boolean rollback, String key, Consumer<String> report) {
            this.server = server;
            this.entries = entries;
            this.rollback = rollback;
            this.key = key;
            this.report = report;
        }
    }

    private static final Deque<Job> JOBS = new ArrayDeque<>();
    private static final Map<String, LastOp> LAST = new HashMap<>();
    /** Offene Vorschauen je Spieler. */
    private static final Map<UUID, Preview> PREVIEWS = new HashMap<>();

    public record Preview(List<LogEntry> entries, boolean rollback, String beschreibung) {}

    private RollbackService() {}

    public static boolean busy() {
        return !JOBS.isEmpty();
    }

    public static LastOp last(String key) {
        return LAST.get(key);
    }

    public static void start(MinecraftServer server, List<LogEntry> entries, boolean rollback, String key, Consumer<String> report) {
        JOBS.add(new Job(server, entries, rollback, key, report));
    }

    /** Pro Tick einen Teil abarbeiten. */
    public static void tick() {
        Job job = JOBS.peek();
        if (job == null) return;
        int limit = CrownsConfig.LOG_PRO_TICK.get();
        int n = 0;
        while (job.idx < job.entries.size() && n < limit) {
            LogEntry e = job.entries.get(job.idx++);
            n++;
            try {
                if (apply(job.server, e, job.rollback, job)) job.done.add(e.id);
            } catch (Exception ex) {
                job.fehler++;
                CrownsCore.LOGGER.debug("Rollback: Eintrag {} uebersprungen", e.id, ex);
            }
        }
        if (job.idx >= job.entries.size()) {
            JOBS.poll();
            LogDatabase.markRolledBack(job.server, job.done, job.rollback);
            LAST.put(job.key, new LastOp(job.done, job.rollback));
            double sec = (System.currentTimeMillis() - job.start) / 1000.0;
            job.report.accept((job.rollback ? "Rollback" : "Wiederherstellung") + " fertig in " + String.format(java.util.Locale.GERMANY, "%.1f", sec)
                    + " s: " + job.blocks + " Bloecke, " + job.items + " Item-Aenderungen, " + job.entities + " Tiere/Mobs"
                    + (job.fehler > 0 ? ", " + job.fehler + " uebersprungen" : "") + ".");
        }
    }

    public static ServerLevel level(MinecraftServer server, String dim) {
        ResourceLocation rl = ResourceLocation.tryParse(dim);
        return rl == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, rl));
    }

    public static BlockState parseState(ServerLevel level, String s) throws Exception {
        return NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), TagParser.parseTag(s));
    }

    /** Zielzustand eines Blockeintrags fuer Rollback oder Restore. */
    public static BlockState target(ServerLevel level, LogEntry e, boolean rollback) throws Exception {
        return parseState(level, rollback ? e.oldS : e.newS);
    }

    private static boolean apply(MinecraftServer server, LogEntry e, boolean rollback, Job job) throws Exception {
        ServerLevel level = level(server, e.dim);
        if (level == null || e.action == null) return false;
        BlockPos pos = new BlockPos(e.x, e.y, e.z);
        switch (e.action) {
            case BREAK, PLACE -> {
                BlockState target = target(level, e, rollback);
                String nbt = rollback ? e.oldNbt : e.newNbt;
                // Inhalt nicht verstreuen: Behaelter vor dem Ersetzen leeren, er wird ueber das Protokoll gefuehrt
                if (level.getBlockEntity(pos) instanceof Container c) c.clearContent();
                level.setBlock(pos, target, FLAGS);
                if (nbt != null && !nbt.isEmpty()) {
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be != null) {
                        be.load(TagParser.parseTag(nbt));
                        be.setChanged();
                        level.sendBlockUpdated(pos, target, target, Block.UPDATE_ALL);
                    }
                }
                job.blocks++;
                return true;
            }
            case ADD, REMOVE -> {
                Container c = LogEvents.containerAt(level, pos);
                if (c == null || e.newNbt == null) return false;
                ItemStack proto = ItemStack.of(TagParser.parseTag(e.newNbt));
                boolean einlegen = (rollback && e.action == LogAction.REMOVE) || (!rollback && e.action == LogAction.ADD);
                if (einlegen) insert(c, proto, e.amount);
                else remove(c, proto, e.amount);
                c.setChanged();
                job.items++;
                return true;
            }
            case KILL -> {
                if (e.oldNbt == null) return false;
                CompoundTag tag = TagParser.parseTag(e.oldNbt);
                if (rollback) {
                    if (tag.hasUUID("UUID") && level.getEntity(tag.getUUID("UUID")) != null) return false;
                    Entity ent = EntityType.loadEntityRecursive(tag, level, x -> x);
                    if (ent == null) return false;
                    if (ent instanceof LivingEntity le) {
                        le.setHealth(le.getMaxHealth());
                        le.deathTime = 0;
                        le.clearFire();
                    }
                    if (!level.addFreshEntity(ent)) {
                        // ID noch belegt (z.B. Leiche noch nicht weg): mit neuer ID einsetzen
                        ent.setUUID(UUID.randomUUID());
                        if (!level.addFreshEntity(ent)) {
                            CrownsCore.LOGGER.warn("Rollback: {} bei {} {} {} konnte nicht gespawnt werden", e.blk, e.x, e.y, e.z);
                            return false;
                        }
                    }
                } else {
                    if (!tag.hasUUID("UUID")) return false;
                    Entity ent = level.getEntity(tag.getUUID("UUID"));
                    if (ent == null) return false;
                    ent.discard();
                }
                job.entities++;
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    static int insert(Container c, ItemStack proto, int amount) {
        int left = amount;
        int max = Math.min(c.getMaxStackSize(), proto.getMaxStackSize());
        for (int i = 0; i < c.getContainerSize() && left > 0; i++) {
            ItemStack s = c.getItem(i);
            if (!s.isEmpty() && ItemStack.isSameItemSameTags(s, proto) && s.getCount() < max) {
                int add = Math.min(left, max - s.getCount());
                s.grow(add);
                left -= add;
            }
        }
        for (int i = 0; i < c.getContainerSize() && left > 0; i++) {
            if (c.getItem(i).isEmpty() && c.canPlaceItem(i, proto)) {
                ItemStack n = proto.copy();
                int add = Math.min(left, max);
                n.setCount(add);
                c.setItem(i, n);
                left -= add;
            }
        }
        return left;
    }

    static int remove(Container c, ItemStack proto, int amount) {
        int left = amount;
        for (int i = c.getContainerSize() - 1; i >= 0 && left > 0; i--) {
            ItemStack s = c.getItem(i);
            if (!s.isEmpty() && ItemStack.isSameItemSameTags(s, proto)) {
                int take = Math.min(left, s.getCount());
                s.shrink(take);
                if (s.isEmpty()) c.setItem(i, ItemStack.EMPTY);
                left -= take;
            }
        }
        return amount - left;
    }

    // ---- Vorschau ---------------------------------------------------------

    /** Zeigt nur dem Spieler, wie die Bloecke nach dem Rollback aussehen wuerden. */
    public static int preview(ServerPlayer p, List<LogEntry> entries, boolean rollback, String beschreibung) {
        cancelPreview(p);
        int n = 0;
        ServerLevel own = p.serverLevel();
        String dim = LogEvents.dim(own);
        for (LogEntry e : entries) {
            if ((e.action != LogAction.BREAK && e.action != LogAction.PLACE) || !e.dim.equals(dim)) continue;
            try {
                p.connection.send(new ClientboundBlockUpdatePacket(new BlockPos(e.x, e.y, e.z), target(own, e, rollback)));
                n++;
            } catch (Exception ignored) {
                // kaputter Zustand, nicht anzeigen
            }
        }
        PREVIEWS.put(p.getUUID(), new Preview(entries, rollback, beschreibung));
        return n;
    }

    public static Preview takePreview(ServerPlayer p) {
        return PREVIEWS.remove(p.getUUID());
    }

    public static boolean cancelPreview(ServerPlayer p) {
        Preview pv = PREVIEWS.remove(p.getUUID());
        if (pv == null) return false;
        ServerLevel own = p.serverLevel();
        String dim = LogEvents.dim(own);
        for (LogEntry e : pv.entries()) {
            if (e.dim.equals(dim)) p.connection.send(new ClientboundBlockUpdatePacket(own, new BlockPos(e.x, e.y, e.z)));
        }
        return true;
    }

    public static void forget(UUID id) {
        PREVIEWS.remove(id);
    }

    /** Meldung an den Ausloeser, auch wenn er inzwischen offline ist (dann ins Log). */
    public static Consumer<String> reporter(MinecraftServer server, UUID player) {
        return msg -> {
            ServerPlayer p = player == null ? null : server.getPlayerList().getPlayer(player);
            if (p != null) Msg.player(p, "§a" + msg);
            else CrownsCore.LOGGER.info("Blockprotokoll: {}", msg);
        };
    }
}
