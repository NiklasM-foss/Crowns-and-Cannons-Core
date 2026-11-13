package de.niklasmetzger.crownscore.claims;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;

import java.util.UUID;

/** Zugriff auf Open Parties and Claims. Nur laden, wenn die Mod da ist. */
final class OpacBridge {
    private static final UUID SERVER_CLAIM = new UUID(0L, 0L);

    private OpacBridge() {}

    static UUID ownerAt(ServerLevel level, ChunkPos chunk) {
        OpenPACServerAPI api = OpenPACServerAPI.get(level.getServer());
        if (api == null) return null;
        IPlayerChunkClaimAPI claim = api.getServerClaimsManager().get(level.dimension().location(), chunk.x, chunk.z);
        if (claim == null) return null;
        UUID owner = claim.getPlayerId();
        if (owner == null || SERVER_CLAIM.equals(owner)) return null;
        return owner;
    }
}
