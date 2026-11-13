package de.niklasmetzger.crownscore.evidence;

import de.niklasmetzger.crownscore.claims.Claims;
import de.niklasmetzger.crownscore.data.CrownsData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Protokolliert, was Spieler in fremdem Gebiet tun. Blockt nichts, Pluendern bleibt erlaubt,
 * aber das Gericht hat Fakten.
 */
public class EvidenceEvents {
    /** Letzte Kistenmeldung je Spieler und Position, damit Doppelklicks nicht fluten. */
    private final Map<UUID, Long> lastContainer = new HashMap<>();
    private final Map<UUID, BlockPos> lastContainerPos = new HashMap<>();

    private static String foreign(ServerPlayer p, ServerLevel level, BlockPos pos) {
        String owner = Claims.nationAt(level, pos);
        if (owner == null) return null;
        CrownsData d = CrownsData.get(p.server);
        String own = d.nationKeyOf(p.getUUID());
        return owner.equals(own) ? null : owner;
    }

    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer p)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        String owner = foreign(p, level, event.getPos());
        if (owner == null) return;
        EvidenceLog.log(p, level, event.getPos(), "BLOCK_ABBAU", owner, event.getState().getBlock().getName().getString());
    }

    @SubscribeEvent
    public void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        String owner = foreign(p, level, event.getPos());
        if (owner == null) return;
        BlockState state = event.getPlacedBlock();
        String typ = (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.LAVA) || state.is(Blocks.TNT)) ? "FEUER" : "BLOCK_SETZEN";
        EvidenceLog.log(p, level, event.getPos(), typ, owner, state.getBlock().getName().getString());
    }

    @SubscribeEvent
    public void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        String owner = foreign(p, level, pos);
        if (owner == null) return;
        ItemStack held = event.getItemStack();
        if (held.is(Items.FLINT_AND_STEEL) || held.is(Items.FIRE_CHARGE) || held.is(Items.LAVA_BUCKET)) {
            EvidenceLog.log(p, level, pos, "FEUER", owner, held.getHoverName().getString() + " benutzt");
            return;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return;
        boolean container = be instanceof Container || be.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent();
        if (!container) return;
        long now = System.currentTimeMillis();
        Long last = lastContainer.get(p.getUUID());
        if (last != null && now - last < 5000 && pos.equals(lastContainerPos.get(p.getUUID()))) return;
        lastContainer.put(p.getUUID(), now);
        lastContainerPos.put(p.getUUID(), pos);
        EvidenceLog.log(p, level, pos, "KISTE", owner, level.getBlockState(pos).getBlock().getName().getString() + " geoeffnet");
    }
}
