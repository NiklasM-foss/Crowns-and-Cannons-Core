package de.niklasmetzger.crownscore.claims;

import com.mojang.authlib.GameProfile;
import de.niklasmetzger.crownscore.CrownsCore;
import de.niklasmetzger.crownscore.config.CrownsConfig;
import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.Nation;
import de.niklasmetzger.crownscore.world.WorldMap;
import de.niklasmetzger.crownscore.world.WorldMap.Country;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.parties.party.member.PartyMemberRank;
import xaero.pac.common.parties.party.member.api.IPartyMemberAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;
import xaero.pac.common.server.parties.party.api.IPartyManagerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;
import xaero.pac.common.server.player.config.api.IPlayerConfigAPI;
import xaero.pac.common.server.player.config.api.PlayerConfigOptions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Laender in Open Parties and Claims: Jede Nation hat ein Staatskonto, dem ihre Chunks gehoeren (Name und
 * Farbe des Landes, ohne Bauschutz), und eine Party, deren Besitzer der Staatschef ist. Erobert eine Nation
 * ein Land, gehen dessen Chunks an ihr Staatskonto, die Grenze dazwischen verschwindet auf Xaero's Karten.
 * Claims von Spielern werden nie angefasst.
 */
public final class OpacTerritory {
    public enum Mode { FLAECHE, UMRISS, GRENZE, AUS }

    private static final UUID SERVER = new UUID(0L, 0L);
    private static final String OLD_PREFIX = "cc-"; // Unterclaims der ersten Fassung, werden aufgeraeumt

    private static final class Job {
        Mode mode;
        Consumer<String> report;
        Map<String, UUID> ownerByCode = new HashMap<>();
        Map<Long, UUID> ownerByOverride = new HashMap<>();
        Set<UUID> states = new HashSet<>();
        Set<Integer> oldSubs = new HashSet<>();
        int minX, maxX, minZ, maxZ, x, z;
        long total, done, claimed, removed, skipped;
        int lastPercent;
        boolean quiet;
    }

    private static Job job;
    /** Gebiete, die waehrend eines laufenden Abgleichs angefragt wurden: {int[] region, Mode, Consumer}. */
    private static final java.util.ArrayDeque<Object[]> WARTEND = new java.util.ArrayDeque<>();
    /**
     * Nach /nation loeschen: jeden Claim des Staatskontos freigeben (in allen Dimensionen, auch eroberte Staedte
     * und zugewiesene Chunks) und die Party aufloesen, damit fruehere Buerger frei fuer neue Nationen sind.
     * Wirkt sofort und haengt nicht vom laufenden Abgleich ab, uebersteht also auch einen Neustart.
     */
    public static void freigeben(MinecraftServer server, Nation n) {
        OpenPACServerAPI api = api(server);
        if (api == null) return;
        UUID staat = n.staat();
        int frei = 0;
        try {
            var info = api.getServerClaimsManager().getPlayerInfo(staat);
            if (info != null) {
                List<Object[]> chunks = new ArrayList<>();
                info.getStream().forEach(dim -> dim.getValue().getStream().forEach(list -> list.getStream()
                        .forEach(pos -> chunks.add(new Object[]{dim.getKey(), pos}))));
                for (Object[] c : chunks) {
                    ChunkPos pos = (ChunkPos) c[1];
                    api.getServerClaimsManager().unclaim((ResourceLocation) c[0], pos.x, pos.z);
                    frei++;
                }
            }
        } catch (Throwable t) {
            CrownsCore.LOGGER.warn("Claims von {} nicht vollstaendig freigegeben: {}", n.name, t.toString());
        }
        try {
            IPartyManagerAPI pm = api.getPartyManager();
            IServerPartyAPI party = n.partei == null ? null : pm.getPartyById(n.partei);
            if (party == null) party = pm.getPartyByOwner(staat);
            if (party != null) pm.removeParty(party);
        } catch (Throwable t) {
            CrownsCore.LOGGER.warn("Party von {} nicht aufgeloest: {}", n.name, t.toString());
        }
        SIGNATURES.remove(n.key());
        CrownsCore.LOGGER.info("Nation {} geloescht: {} Claims freigegeben, Party aufgeloest", n.name, frei);
    }
    private static final Map<String, String> SIGNATURES = new HashMap<>();
    private static int tickCounter;

    private OpacTerritory() {}

    public static boolean running() {
        return job != null;
    }

    public static String status() {
        if (job == null) return "keine Aktualisierung in Arbeit";
        return job.mode.name().toLowerCase() + ", " + (job.done * 100 / Math.max(1, job.total)) + " %";
    }

    private static OpenPACServerAPI api(MinecraftServer server) {
        return OpenPACServerAPI.get(server);
    }

    // ---- Staatskonto und Party --------------------------------------------

    /** Name, Farbe und fehlenden Bauschutz des Staatskontos setzen. */
    static void ensureState(OpenPACServerAPI api, Nation n) {
        UUID staat = n.staat();
        try {
            Object info = api.getServerClaimsManager().getPlayerInfo(staat);
            if (info instanceof xaero.pac.common.server.claims.player.ServerPlayerClaimInfo spi) spi.setPlayerUsername(n.name);
        } catch (Throwable t) {
            CrownsCore.LOGGER.debug("Staatsname fuer {} nicht gesetzt", n.name, t);
        }
        IPlayerConfigAPI cfg = api.getPlayerConfigs().getLoadedConfig(staat);
        cfg.tryToSet(PlayerConfigOptions.CLAIMS_NAME, n.name);
        cfg.tryToSet(PlayerConfigOptions.CLAIMS_COLOR, WorldMap.colorOf(n));
        cfg.tryToSet(PlayerConfigOptions.PROTECT_CLAIMED_CHUNKS, false);
        cfg.tryToSet(PlayerConfigOptions.PARTY_NAME, n.name);
    }

    /** Party der Nation: Besitzer ist der Staatschef (sonst das Staatskonto), Mitglieder sind die Buerger. */
    static void syncParty(OpenPACServerAPI api, Nation n) {
        IPartyManagerAPI pm = api.getPartyManager();
        UUID staat = n.staat();
        IServerPartyAPI party = n.partei == null ? null : pm.getPartyById(n.partei);
        if (party == null) party = pm.getPartyByOwner(staat);
        if (party == null) {
            IServerPartyAPI other = pm.getPartyByMember(staat);
            if (other != null) party = other;
            else party = pm.createPartyForOwner(new GameProfile(staat, n.name));
        }
        if (party == null) {
            CrownsCore.LOGGER.warn("Party fuer {} liess sich nicht anlegen", n.name);
            return;
        }
        n.partei = party.getId();

        Set<UUID> soll = new HashSet<>(n.mitglieder.keySet());
        if (n.chef != null) soll.add(n.chef);
        for (UUID m : soll) {
            if (party.getMemberInfo(m) != null) continue;
            IServerPartyAPI other = pm.getPartyByMember(m);
            if (other != null && !other.getId().equals(party.getId())) continue; // schon in einer anderen Party
            party.addMember(m, n.isBeamter(m) ? PartyMemberRank.ADMIN : PartyMemberRank.MEMBER, name(n, m));
        }
        UUID wantOwner = n.chef != null && party.getMemberInfo(n.chef) != null ? n.chef : staat;
        UUID curOwner = party.getOwner().getUUID();
        if (!curOwner.equals(wantOwner)) {
            if (party.getMemberInfo(wantOwner) == null) party.addMember(wantOwner, PartyMemberRank.ADMIN, wantOwner.equals(staat) ? n.name : name(n, wantOwner));
            try {
                ((xaero.pac.common.server.parties.party.ServerParty) party).changeOwner(wantOwner, wantOwner.equals(staat) ? n.name : name(n, wantOwner));
            } catch (Throwable t) {
                CrownsCore.LOGGER.warn("Besitzerwechsel der Party {} nicht moeglich: {}", n.name, t.toString());
            }
        }
        UUID owner = party.getOwner().getUUID();
        List<UUID> raus = new ArrayList<>();
        for (IPartyMemberAPI mi : party.getMemberInfoStream().toList()) {
            UUID id = mi.getUUID();
            if (!id.equals(owner) && !id.equals(staat) && !soll.contains(id)) raus.add(id);
        }
        for (UUID id : raus) party.removeMember(id);
        final IServerPartyAPI p = party;
        List<IPartyMemberAPI> members = p.getMemberInfoStream().toList();
        for (IPartyMemberAPI mi : members) {
            if (mi.getUUID().equals(owner) || mi.getUUID().equals(staat)) continue;
            PartyMemberRank want = n.isBeamter(mi.getUUID()) ? PartyMemberRank.ADMIN : PartyMemberRank.MEMBER;
            if (mi.getRank() != want) p.setRank(mi, want);
        }
        api.getPlayerConfigs().getLoadedConfig(owner).tryToSet(PlayerConfigOptions.PARTY_NAME, n.name);
    }

    private static String name(Nation n, UUID id) {
        String s = n.mitglieder.get(id);
        return s == null || s.isEmpty() ? id.toString().substring(0, 8) : s;
    }

    private static boolean beteiligt(Nation n) {
        return !n.laender.isEmpty() || n.partei != null;
    }

    private static String signature(Nation n) {
        return n.name + "|" + n.chef + "|" + n.mitglieder.keySet() + "|" + n.beamte + "|" + n.laender;
    }

    /** Staatskonto und Party einer Nation sofort abgleichen. */
    public static void syncNation(MinecraftServer server, Nation n) {
        OpenPACServerAPI api = api(server);
        if (api == null) return;
        ensureState(api, n);
        syncParty(api, n);
        SIGNATURES.put(n.key(), signature(n));
        CrownsData.get(server).setDirty();
    }

    /** Alle 30 Sekunden: geaenderte Nationen nachziehen (neuer Staatschef, neue Buerger, Umbenennung). */
    private static void syncChanged(MinecraftServer server) {
        CrownsData d = CrownsData.get(server);
        if (d.karteModus.isEmpty() || d.karteModus.equals("AUS")) return;
        OpenPACServerAPI api = api(server);
        if (api == null) return;
        for (Nation n : d.nations.values()) {
            if (!beteiligt(n)) continue;
            String sig = signature(n);
            if (sig.equals(SIGNATURES.get(n.key()))) continue;
            try {
                ensureState(api, n);
                syncParty(api, n);
                SIGNATURES.put(n.key(), sig);
                d.setDirty();
            } catch (Throwable t) {
                CrownsCore.LOGGER.warn("Abgleich von {} mit Open Parties and Claims fehlgeschlagen", n.name, t);
                SIGNATURES.put(n.key(), sig);
            }
        }
    }

    // ---- Gebiet -----------------------------------------------------------

    /** Startet den Abgleich der Claims, ganz oder fuer einen Chunkbereich. Liefert eine Fehlermeldung oder null. */
    public static String start(MinecraftServer server, Mode mode, int[] region, boolean quiet, Consumer<String> report) {
        if (job != null) {
            if (region == null || mode == Mode.AUS) {
                return "Die Laender werden gerade schon aktualisiert (" + status() + ").";
            }
            // Gebiet merken und nach dem laufenden Durchgang mit frischen Daten nachholen
            synchronized (WARTEND) {
                if (WARTEND.size() < 200) WARTEND.add(new Object[]{region.clone(), mode, report});
            }
            return null;
        }
        OpenPACServerAPI api = api(server);
        if (api == null) return "Open Parties and Claims ist nicht bereit.";
        CrownsData d = CrownsData.get(server);
        Job j = new Job();
        j.mode = mode;
        j.report = report;
        j.quiet = quiet;
        for (Nation n : d.nations.values()) {
            j.states.add(n.staat());
            if (mode == Mode.AUS) continue;
            boolean used = false;
            for (String code : n.laender) {
                j.ownerByCode.put(code.toUpperCase(), n.staat());
                used = true;
            }
            if (used) ensureState(api, n);
        }
        Map<Long, String> overrides = d.claims.get(Level.OVERWORLD.location().toString());
        if (overrides != null && mode != Mode.AUS) {
            for (Map.Entry<Long, String> e : overrides.entrySet()) {
                Nation n = d.nations.get(e.getValue());
                if (n == null) continue;
                j.ownerByOverride.put(e.getKey(), n.staat());
                ensureState(api, n);
            }
        }
        IPlayerConfigAPI server0 = api.getPlayerConfigs().getServerClaimConfig();
        for (String id : server0.getSubConfigIds()) {
            if (id.startsWith(OLD_PREFIX) && server0.getSubConfig(id) != null) j.oldSubs.add(server0.getSubConfig(id).getSubIndex());
        }
        if (region == null) {
            j.minX = WorldMap.minChunkX();
            j.maxX = WorldMap.maxChunkX();
            j.minZ = WorldMap.minChunkZ();
            j.maxZ = WorldMap.maxChunkZ();
        } else {
            j.minX = region[0];
            j.maxX = region[1];
            j.minZ = region[2];
            j.maxZ = region[3];
        }
        j.x = j.minX;
        j.z = j.minZ;
        j.total = (long) (j.maxX - j.minX + 1) * (j.maxZ - j.minZ + 1);
        if (region == null) d.karteModus = mode.name();
        d.setDirty();
        job = j;
        return null;
    }

    /** Gewuenschter Besitzer eines Chunks: eigener Claim der Nation, sonst das Land auf der Karte. */
    private static UUID want(Job j, int x, int z) {
        UUID o = j.ownerByOverride.get(ChunkPos.asLong(x, z));
        if (o != null) return o;
        Country c = WorldMap.atChunk(x, z);
        if (c == null) return null;
        UUID owner = j.ownerByCode.get(c.code().toUpperCase());
        if (owner == null) return null;
        return switch (j.mode) {
            case FLAECHE -> owner;
            case UMRISS -> border(j, owner, x, z, true) ? owner : null;
            case GRENZE -> border(j, owner, x, z, false) ? owner : null;
            default -> null;
        };
    }

    private static UUID ownerOf(Job j, int x, int z) {
        UUID o = j.ownerByOverride.get(ChunkPos.asLong(x, z));
        if (o != null) return o;
        Country c = WorldMap.atChunk(x, z);
        return c == null ? null : j.ownerByCode.get(c.code().toUpperCase());
    }

    private static boolean border(Job j, UUID owner, int x, int z, boolean coast) {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            UUID n = ownerOf(j, x + d[0], z + d[1]);
            if (owner.equals(n)) continue;
            if (n != null || coast) return true;
        }
        return false;
    }

    private static boolean ours(Job j, IPlayerChunkClaimAPI cur) {
        if (cur == null) return false;
        UUID id = cur.getPlayerId();
        if (j.states.contains(id)) return true;
        return SERVER.equals(id) && j.oldSubs.contains(cur.getSubConfigIndex());
    }

    private static void apply(IServerClaimsManagerAPI claims, ResourceLocation dim, Job j, int x, int z) {
        UUID want = want(j, x, z);
        IPlayerChunkClaimAPI cur = claims.get(dim, x, z);
        if (cur != null && !ours(j, cur)) {
            if (want != null) j.skipped++; // Spieler-Claim bleibt
            return;
        }
        if (want == null) {
            if (cur != null) {
                claims.unclaim(dim, x, z);
                j.removed++;
            }
        } else if (cur == null || !want.equals(cur.getPlayerId()) || cur.getSubConfigIndex() != -1) {
            claims.claim(dim, want, -1, x, z, false);
            j.claimed++;
        }
    }

    /** Einzelne Chunks sofort abgleichen, z.B. nach /gebiet zuweisen. */
    public static void syncChunks(MinecraftServer server, Collection<ChunkPos> chunks) {
        CrownsData d = CrownsData.get(server);
        if (d.karteModus.isEmpty() || d.karteModus.equals("AUS") || chunks.isEmpty()) return;
        OpenPACServerAPI api = api(server);
        if (api == null) return;
        Mode mode = Mode.valueOf(d.karteModus);
        // Fuer Umriss und Grenze haengt die Anzeige von den Nachbarn ab: kleinen Bereich drumherum neu rechnen
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (ChunkPos c : chunks) {
            minX = Math.min(minX, c.x - 1);
            maxX = Math.max(maxX, c.x + 1);
            minZ = Math.min(minZ, c.z - 1);
            maxZ = Math.max(maxZ, c.z + 1);
        }
        if (job != null) {
            // laeuft gerade ein Abgleich mit altem Stand: Gebiet hinten anstellen
            start(server, mode, new int[]{minX, maxX, minZ, maxZ}, true, msg -> {});
            return;
        }
        start(server, mode, new int[]{minX, maxX, minZ, maxZ}, true, msg -> {});
        while (job != null) tick(server);
    }

    public static void tick(MinecraftServer server) {
        if (++tickCounter % 600 == 0) {
            try {
                syncChanged(server);
            } catch (Throwable t) {
                CrownsCore.LOGGER.warn("Laender-Abgleich fehlgeschlagen", t);
            }
        }
        Job j = job;
        if (j == null) {
            Object[] w;
            synchronized (WARTEND) {
                w = WARTEND.poll();
            }
            if (w != null) {
                @SuppressWarnings("unchecked")
                Consumer<String> rep = (Consumer<String>) w[2];
                String err = start(server, (Mode) w[1], (int[]) w[0], true, rep == null ? s -> {} : rep);
                if (err != null && rep != null) rep.accept("Karte nicht nachgezogen: " + err);
            }
            return;
        }
        OpenPACServerAPI api = api(server);
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
            apply(claims, dim, j, x, z);
        }
        int pct = (int) (j.done * 100 / Math.max(1, j.total));
        if (!j.quiet && pct / 25 > j.lastPercent / 25 && pct < 100) j.report.accept("Laender auf der Karte: " + pct + " %");
        j.lastPercent = pct;
        if (j.z > j.maxZ) {
            job = null;
            if (!j.quiet || j.claimed + j.removed > 0) {
                j.report.accept("Laender auf der Karte fertig (" + j.mode.name().toLowerCase() + "): " + j.claimed + " Chunks vergeben, " + j.removed
                        + " entfernt" + (j.skipped > 0 ? ", " + j.skipped + " uebersprungen, weil dort Spieler-Claims liegen" : "") + ".");
            }
            if (j.mode != Mode.AUS) {
                CrownsData d = CrownsData.get(server);
                for (Nation n : d.nations.values()) {
                    if (!beteiligt(n)) continue;
                    try {
                        syncParty(api, n);
                        SIGNATURES.put(n.key(), signature(n));
                    } catch (Throwable t) {
                        CrownsCore.LOGGER.warn("Party von {} nicht abgeglichen", n.name, t);
                    }
                }
                d.setDirty();
            }
        }
    }
}
