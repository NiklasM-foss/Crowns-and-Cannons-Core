package de.niklasmetzger.crownscore.log;

import de.niklasmetzger.crownscore.util.Msg;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Inspektor: Klick auf einen Block zeigt seine Geschichte, statt ihn abzubauen oder zu benutzen. */
public class Inspector {
    private static final Set<UUID> ACTIVE = new HashSet<>();
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();

    public static boolean toggle(UUID id) {
        if (ACTIVE.remove(id)) return false;
        ACTIVE.add(id);
        return true;
    }

    public static void forget(UUID id) {
        ACTIVE.remove(id);
        COOLDOWN.remove(id);
    }

    private static boolean ready(ServerPlayer p) {
        long now = System.currentTimeMillis();
        Long last = COOLDOWN.get(p.getUUID());
        if (last != null && now - last < 300) return false;
        COOLDOWN.put(p.getUUID(), now);
        return true;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLeft(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !ACTIVE.contains(p.getUUID())) return;
        event.setCanceled(true);
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START || !ready(p)) return;
        BlockPos pos = event.getPos();
        query(p, "Bei " + pos.getX() + " " + pos.getY() + " " + pos.getZ(),
                "dim = ? AND x = ? AND y = ? AND z = ? AND a IN (0,1)", List.of(LogEvents.dim(p.level()), pos.getX(), pos.getY(), pos.getZ()));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer p && ACTIVE.contains(p.getUUID())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRight(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !ACTIVE.contains(p.getUUID())) return;
        event.setCanceled(true);
        if (!ready(p)) return;
        ServerLevel level = p.serverLevel();
        BlockPos pos = event.getPos();
        String dim = LogEvents.dim(level);
        if (LogEvents.containerAt(level, pos) != null) {
            // Behaelter: alle Bewegungen, bei Doppelkisten beide Haelften
            List<BlockPos> spots = new ArrayList<>(List.of(pos));
            BlockState st = level.getBlockState(pos);
            if (st.getBlock() instanceof ChestBlock && st.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                spots.add(pos.relative(ChestBlock.getConnectedDirection(st)));
            }
            StringBuilder w = new StringBuilder("dim = ? AND a IN (10,11) AND (");
            List<Object> args = new ArrayList<>(List.of(dim));
            for (int i = 0; i < spots.size(); i++) {
                w.append(i == 0 ? "" : " OR ").append("(x = ? AND y = ? AND z = ?)");
                args.add(spots.get(i).getX());
                args.add(spots.get(i).getY());
                args.add(spots.get(i).getZ());
            }
            w.append(")");
            query(p, "Behaelter bei " + pos.getX() + " " + pos.getY() + " " + pos.getZ(), w.toString(), args);
            return;
        }
        Direction face = event.getFace() == null ? Direction.UP : event.getFace();
        BlockPos adj = pos.relative(face);
        query(p, "Bei " + adj.getX() + " " + adj.getY() + " " + adj.getZ() + " und Benutzung von " + LogFormat.shortId(LogEvents.blockId(level.getBlockState(pos))),
                "dim = ? AND ((x = ? AND y = ? AND z = ? AND a IN (0,1)) OR (x = ? AND y = ? AND z = ? AND a = 2))",
                List.of(dim, adj.getX(), adj.getY(), adj.getZ(), pos.getX(), pos.getY(), pos.getZ()));
    }

    private static void query(ServerPlayer p, String titel, String where, List<Object> args) {
        if (!LogDatabase.ready()) {
            Msg.player(p, "§cDas Blockprotokoll ist nicht aktiv.");
            return;
        }
        LogDatabase.async(p.server, c -> LogDatabase.select(c, where, new ArrayList<>(args), 200, false),
                list -> LogCommand.show(p.createCommandSourceStack(), titel, list, false),
                e -> Msg.player(p, "§cAbfrage fehlgeschlagen: " + e.getMessage()));
    }
}
