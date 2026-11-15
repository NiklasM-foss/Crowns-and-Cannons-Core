package de.niklasmetzger.crownscore.claims;

import de.niklasmetzger.crownscore.data.CrownsData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.fml.ModList;

import java.util.Map;
import java.util.UUID;

/**
 * Wem gehoert ein Chunk? Erst die internen Gebiete, dann Open Parties and Claims.
 * Liefert immer den Nationsschluessel oder null (Natur).
 */
public final class Claims {
    private static Boolean opac;

    private Claims() {}

    private static boolean opacLoaded() {
        if (opac == null) opac = ModList.get().isLoaded("openpartiesandclaims");
        return opac;
    }

    public static String nationAt(ServerLevel level, BlockPos pos) {
        return nationAt(level, new ChunkPos(pos));
    }

    public static String nationAt(ServerLevel level, ChunkPos chunk) {
        CrownsData data = CrownsData.get(level.getServer());
        String dim = level.dimension().location().toString();
        Map<Long, String> map = data.claims.get(dim);
        if (map != null) {
            String n = map.get(chunk.toLong());
            if (n != null && data.nations.containsKey(n)) return n;
        }
        if (opacLoaded()) {
            try {
                UUID owner = OpacBridge.ownerAt(level, chunk);
                if (owner != null) {
                    String n = data.nationKeyOf(owner);
                    if (n != null) return n;
                }
            } catch (Throwable ignored) {
                // OPAC-API nicht verfuegbar, dann nur interne Gebiete
            }
        }
        // Zuletzt die Weltkarte: Land an dieser Stelle und die damit verknuepfte Nation. Nur Nationen, die
        // gespielt werden (Staatschef oder Mitglieder), besitzen ihr Land; unbespielte Laender sind freies Land.
        if (level.dimension() == net.minecraft.world.level.Level.OVERWORLD && de.niklasmetzger.crownscore.world.WorldMap.active()) {
            String key = de.niklasmetzger.crownscore.world.WorldMap.nationFor(data, de.niklasmetzger.crownscore.world.WorldMap.atChunk(chunk.x, chunk.z));
            de.niklasmetzger.crownscore.data.Nation n = key == null ? null : data.nations.get(key);
            if (n != null && (n.chef != null || !n.mitglieder.isEmpty())) return key;
        }
        return null;
    }

    /**
     * Nation des eigenen Chunks, sonst die eines Nachbarchunks.
     * Damit zaehlt auch das Herumstehen direkt vor einer fremden Mauer.
     */
    public static String nationNear(ServerLevel level, BlockPos pos) {
        ChunkPos c = new ChunkPos(pos);
        String own = nationAt(level, c);
        if (own != null) return own;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                String n = nationAt(level, new ChunkPos(c.x + dx, c.z + dz));
                if (n != null) return n;
            }
        }
        return null;
    }

    public static int countInternal(CrownsData data, String nationKey) {
        int n = 0;
        for (Map<Long, String> m : data.claims.values()) {
            for (String v : m.values()) if (v.equals(nationKey)) n++;
        }
        return n;
    }
}
