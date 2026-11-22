package de.niklasmetzger.crownscore.client.xaero;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.pac.client.api.OpenPACClientAPI;
import xaero.pac.client.claims.api.IClientClaimsManagerAPI;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;

import java.util.UUID;

/** Besitzer eines Chunks laut den Claims, die OPAC an den Client schickt. */
final class Claims {
    private Claims() {}

    static UUID owner(ResourceKey<Level> dim, int cx, int cz) {
        OpenPACClientAPI api = OpenPACClientAPI.get();
        if (api == null) return null;
        IClientClaimsManagerAPI cm = api.getClaimsManager();
        if (cm == null) return null;
        IPlayerChunkClaimAPI c = cm.get(dim.location(), cx, cz);
        return c == null ? null : c.getPlayerId();
    }
}
