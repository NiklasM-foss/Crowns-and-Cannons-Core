package de.niklasmetzger.crownscore.claims;

import de.niklasmetzger.crownscore.config.CrownsConfig;
import de.niklasmetzger.crownscore.world.WorldMap;
import de.niklasmetzger.crownscore.world.WorldMap.Country;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;
import xaero.pac.common.server.player.config.api.IPlayerConfigAPI;
import xaero.pac.common.server.player.config.api.PlayerConfigOptions;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Laendergrenzen auf Xaero's Minimap und Weltkarte: Open Parties and Claims zeigt Server-Claims mit Name
 * und Farbe an. Jedes Land bekommt einen eigenen Server-Unterclaim ohne Bauschutz. Claims von Spielern
 * werden nie angefasst. Laeuft stueckweise im Server-Tick.
 */
public final class OpacBorders {
    public enum Mode { UMRISS, GRENZE, FLAECHE, AUS }

    private static final UUID SERVER = new UUID(0L, 0L);
    private static final String PREFIX = "cc-";

    private static final class Job {
        Mode mode;
        Consumer<String> report;
        Map<Integer, Integer> subByCountry = new HashMap<>();
        Set<Integer> ours = new HashSet<>();
        int minX, maxX, minZ, maxZ, x, z;
        long total, done, claimed, removed, skipped;
        int lastPercent;
    }

    private static Job job;

    private OpacBorders() {}

    public static boolean running() {
        return job != null;
    }

    public static String status() {
        if (job == null) return "keine Grenzanzeige in Arbeit";
        return job.mode.name().toLowerCase() + ", " + (job.done * 100 / Math.max(1, job.total)) + " %";
    }

    /** Startet den Abgleich. Liefert eine Fehlermeldung oder null. */
    public static String start(MinecraftServer server, Mode mode, Consumer<String> report) {
        if (job != null) return "Die Grenzanzeige wird gerade schon aufgebaut (" + status() + ").";
        OpenPACServerAPI api = OpenPACServerAPI.get(server);
        if (api == null) return "Open Parties and Claims ist nicht bereit.";
        IPlayerConfigAPI cfg = api.getPlayerConfigs().getServerClaimConfig();
        Job j = new Job();
        j.mode = mode;
        j.report = report;

        if (mode != Mode.AUS) {
            int missing = 0;
            for (Country c : WorldMap.countries()) if (!cfg.subConfigExists(PREFIX + c.code().toLowerCase())) missing++;
            int free = cfg.getSubConfigLimit() - cfg.getSubCount();
            if (missing > free) {
                return "Open Parties and Claims erlaubt nur " + cfg.getSubConfigLimit() + " Unterclaims, gebraucht werden " + (cfg.getSubCount() + missing)
                        + ". In world/serverconfig/openpartiesandclaims-server.toml playerSubConfigLimit auf " + (cfg.getSubCount() + missing + 16)
                        + " setzen und den Server neu starten.";
            }
            for (Country c : WorldMap.countries()) {
                String id = PREFIX + c.code().toLowerCase();
                IPlayerConfigAPI sub = cfg.subConfigExists(id) ? cfg.getSubConfig(id) : cfg.createSubConfig(id);
                if (sub == null) return "Unterclaim " + id + " liess sich nicht anlegen.";
                sub.tryToSet(PlayerConfigOptions.CLAIMS_NAME, c.name());
                sub.tryToSet(PlayerConfigOptions.CLAIMS_COLOR, c.color());
                sub.tryToSet(PlayerConfigOptions.PROTECT_CLAIMED_CHUNKS, false);
                j.subByCountry.put(c.index(), sub.getSubIndex());
            }
        }
        for (String id : cfg.getSubConfigIds()) {
            if (id.startsWith(PREFIX) && cfg.getSubConfig(id) != null) j.ours.add(cfg.getSubConfig(id).getSubIndex());
        }
        j.minX = WorldMap.minChunkX();
        j.maxX = WorldMap.maxChunkX();
        j.minZ = WorldMap.minChunkZ();
        j.maxZ = WorldMap.maxChunkZ();
        j.x = j.minX;
        j.z = j.minZ;
        j.total = (long) (j.maxX - j.minX + 1) * (j.maxZ - j.minZ + 1);
        job = j;
        return null;
    }

    private static boolean border(Country c, int x, int z, boolean coast) {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            Country n = WorldMap.atChunk(x + d[0], z + d[1]);
            if (n == c) continue;
            if (n != null || coast) return true;
        }
        return false;
    }

    public static void tick(MinecraftServer server) {
        Job j = job;
        if (j == null) return;
        OpenPACServerAPI api = OpenPACServerAPI.get(server);
        if (api == null) return;
        IServerClaimsManagerAPI claims = api.getServerClaimsManager();
        ResourceLocation dim = Level.OVERWORLD.location();
        int budget = CrownsConfig.KARTE_CHUNKS_PRO_TICK.get();
        while (budget-- > 0 && j.z <= j.maxZ) {
            int x = j.x, z = j.z;
            if (++j.x > j.maxX) {
                j.x = j.minX;
                j.z++;
            }
            j.done++;
            Integer want = null;
            if (j.mode != Mode.AUS) {
                Country c = WorldMap.atChunk(x, z);
                if (c != null) {
                    boolean show = switch (j.mode) {
                        case FLAECHE -> true;
                        case UMRISS -> border(c, x, z, true);
                        case GRENZE -> border(c, x, z, false);
                        default -> false;
                    };
                    if (show) want = j.subByCountry.get(c.index());
                }
            }
            IPlayerChunkClaimAPI cur = claims.get(dim, x, z);
            boolean ours = cur != null && SERVER.equals(cur.getPlayerId()) && j.ours.contains(cur.getSubConfigIndex());
            if (cur != null && !ours) {
                if (want != null) j.skipped++; // Spieler-Claim bleibt
                continue;
            }
            if (want == null) {
                if (ours) {
                    claims.unclaim(dim, x, z);
                    j.removed++;
                }
            } else if (cur == null || cur.getSubConfigIndex() != want) {
                claims.claim(dim, SERVER, want, x, z, false);
                j.claimed++;
            }
        }
        int pct = (int) (j.done * 100 / Math.max(1, j.total));
        if (pct / 25 > j.lastPercent / 25 && pct < 100) j.report.accept("Grenzanzeige: " + pct + " %");
        j.lastPercent = pct;
        if (j.z > j.maxZ) {
            job = null;
            j.report.accept("Grenzanzeige fertig (" + j.mode.name().toLowerCase() + "): " + j.claimed + " Chunks markiert, " + j.removed
                    + " entfernt" + (j.skipped > 0 ? ", " + j.skipped + " uebersprungen, weil dort Spieler-Claims liegen" : "") + ".");
        }
    }
}
