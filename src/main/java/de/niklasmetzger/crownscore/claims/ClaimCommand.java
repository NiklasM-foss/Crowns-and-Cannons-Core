package de.niklasmetzger.crownscore.claims;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.niklasmetzger.crownscore.config.CrownsConfig;
import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.Nation;
import de.niklasmetzger.crownscore.util.CommandUtil;
import de.niklasmetzger.crownscore.util.Msg;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.Map;

/**
 * Interne Gebiete auf Chunk-Basis. Braucht man nur, solange Open Parties and Claims
 * nicht im Pack ist, funktioniert aber auch parallel dazu.
 */
public final class ClaimCommand {
    private ClaimCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("gebiet")
                .then(Commands.literal("hier").executes(ClaimCommand::hier))
                .then(Commands.literal("claim")
                        .executes(ctx -> claim(ctx, 0))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(0, 8))
                                .executes(ctx -> claim(ctx, IntegerArgumentType.getInteger(ctx, "radius")))))
                .then(Commands.literal("freigeben")
                        .executes(ctx -> unclaim(ctx, 0))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(0, 8))
                                .executes(ctx -> unclaim(ctx, IntegerArgumentType.getInteger(ctx, "radius")))))
                .then(Commands.literal("anzahl").executes(ClaimCommand::anzahl)));
    }

    private static int hier(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        CrownsData d = CommandUtil.data(ctx);
        String n = Claims.nationAt(p.serverLevel(), p.blockPosition());
        ChunkPos c = new ChunkPos(p.blockPosition());
        if (n == null) Msg.info(ctx.getSource(), "Chunk " + c.x + "/" + c.z + " gehoert niemandem (Natur).");
        else Msg.info(ctx.getSource(), "Chunk " + c.x + "/" + c.z + " gehoert " + d.anzeige(n) + ".");
        return 1;
    }

    private static int claim(CommandContext<CommandSourceStack> ctx, int radius) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        CrownsData d = CommandUtil.data(ctx);
        Nation n = CommandUtil.own(ctx.getSource(), d, p);
        if (n == null) return 0;
        if (!CommandUtil.beamter(ctx.getSource(), n, p)) return 0;
        ServerLevel level = p.serverLevel();
        String dim = level.dimension().location().toString();
        Map<Long, String> map = d.claims.computeIfAbsent(dim, k -> new HashMap<>());
        ChunkPos center = new ChunkPos(p.blockPosition());
        int max = CrownsConfig.GEBIET_MAX_CHUNKS.get();
        int have = Claims.countInternal(d, n.key());
        int added = 0, fremd = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                ChunkPos c = new ChunkPos(center.x + dx, center.z + dz);
                String owner = Claims.nationAt(level, c);
                if (owner != null && !owner.equals(n.key())) {
                    fremd++;
                    continue;
                }
                if (owner == null) {
                    if (have + added >= max) break;
                    map.put(c.toLong(), n.key());
                    added++;
                }
            }
        }
        d.setDirty();
        Msg.ok(ctx.getSource(), added + " Chunks fuer " + n.name + " beansprucht"
                + (fremd > 0 ? ", " + fremd + " gehoeren schon einer anderen Nation" : "")
                + ". Gesamt: " + (have + added) + "/" + max);
        return added;
    }

    private static int unclaim(CommandContext<CommandSourceStack> ctx, int radius) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        CrownsData d = CommandUtil.data(ctx);
        Nation n = CommandUtil.own(ctx.getSource(), d, p);
        if (n == null) return 0;
        if (!CommandUtil.beamter(ctx.getSource(), n, p)) return 0;
        String dim = p.serverLevel().dimension().location().toString();
        Map<Long, String> map = d.claims.get(dim);
        if (map == null) {
            Msg.err(ctx.getSource(), "Hier gibt es keine internen Gebiete.");
            return 0;
        }
        ChunkPos center = new ChunkPos(p.blockPosition());
        int removed = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                long key = new ChunkPos(center.x + dx, center.z + dz).toLong();
                if (n.key().equals(map.get(key)) || (ctx.getSource().hasPermission(2) && map.containsKey(key))) {
                    map.remove(key);
                    removed++;
                }
            }
        }
        d.setDirty();
        Msg.ok(ctx.getSource(), removed + " Chunks freigegeben.");
        return removed;
    }

    private static int anzahl(CommandContext<CommandSourceStack> ctx) {
        CrownsData d = CommandUtil.data(ctx);
        Msg.info(ctx.getSource(), "Interne Gebiete je Nation:");
        for (Nation n : d.nations.values()) {
            int c = Claims.countInternal(d, n.key());
            if (c > 0) Msg.line(ctx.getSource(), " §7- " + n.name + ": " + c + " Chunks");
        }
        return 1;
    }
}
