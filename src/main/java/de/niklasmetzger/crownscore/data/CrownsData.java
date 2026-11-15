package de.niklasmetzger.crownscore.data;

import de.niklasmetzger.crownscore.config.CrownsConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Gesamter Spielstand der Mod, gespeichert in der Oberwelt unter data/crownscore.dat. */
public class CrownsData extends SavedData {
    public static final String NAME = "crownscore";

    /** Nationen nach Schluessel (Name in Kleinbuchstaben). */
    public final Map<String, Nation> nations = new LinkedHashMap<>();
    public final List<War> wars = new ArrayList<>();
    public int nextWarId = 1;
    public final Map<String, Treasury> treasuries = new HashMap<>();
    public final Deque<EvidenceEntry> evidence = new ArrayDeque<>();
    public final Map<UUID, ActivityRecord> activity = new HashMap<>();
    public String lastActivityReportWeek = "";
    /** Anzeige der Laender auf der Minimap: FLAECHE, UMRISS, GRENZE oder AUS (leer = noch nie aufgebaut). */
    public String karteModus = "";
    public final List<Election> elections = new ArrayList<>();
    public int nextElectionId = 1;
    public final List<Mail> mails = new ArrayList<>();
    public int nextMailId = 1;
    public final Map<String, Colony> colonies = new LinkedHashMap<>();
    public final List<Visa> visas = new ArrayList<>();
    public final List<Contract> contracts = new ArrayList<>();
    public int nextContractId = 1;
    /** Interne Gebiete: Dimension zu (Chunk-Long zu Nationsschluessel). */
    public final Map<String, Map<Long, String>> claims = new HashMap<>();
    /** Interne Geldboerse, nur wenn keine Wirtschaftsmod da ist. */
    public final Map<UUID, Double> wallet = new HashMap<>();

    public static CrownsData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(CrownsData::load, CrownsData::new, NAME);
    }

    // ---- Nationen -------------------------------------------------------

    public Nation nation(String nameOrKuerzel) {
        if (nameOrKuerzel == null) return null;
        Nation n = nations.get(Nation.key(nameOrKuerzel));
        if (n != null) return n;
        String k = nameOrKuerzel.trim().toUpperCase(Locale.ROOT);
        for (Nation x : nations.values()) {
            if (x.kuerzel.equals(k)) return x;
        }
        return null;
    }

    public Nation nationOf(UUID player) {
        if (player == null) return null;
        for (Nation n : nations.values()) {
            if (n.isMember(player)) return n;
        }
        return null;
    }

    public String nationKeyOf(UUID player) {
        Nation n = nationOf(player);
        return n == null ? null : n.key();
    }

    public String anzeige(String key) {
        Nation n = nations.get(key);
        return n == null ? key : n.name;
    }

    // ---- Kriege ---------------------------------------------------------

    public War activeWar(String a, String b) {
        for (War w : wars) {
            if (w.aktiv && w.zwischen(a, b)) return w;
        }
        return null;
    }

    public List<War> activeWarsOf(String nationKey) {
        List<War> out = new ArrayList<>();
        for (War w : wars) if (w.aktiv && w.beteiligt(nationKey)) out.add(w);
        return out;
    }

    public War warById(int id) {
        for (War w : wars) if (w.id == id) return w;
        return null;
    }

    // ---- Staatskasse ----------------------------------------------------

    public Treasury treasury(String nationKey) {
        return treasuries.computeIfAbsent(nationKey, k -> new Treasury());
    }

    // ---- Akte -----------------------------------------------------------

    public void addEvidence(EvidenceEntry e) {
        evidence.addLast(e);
        int max = CrownsConfig.AKTEN_MAX.get();
        while (evidence.size() > max) evidence.pollFirst();
        setDirty();
    }

    // ---- Visa -----------------------------------------------------------

    public Visa visa(UUID player, String nationKey) {
        long now = System.currentTimeMillis();
        for (Visa v : visas) {
            if (v.spieler.equals(player) && v.nation.equals(nationKey) && v.gueltig(now)) return v;
        }
        return null;
    }

    // ---- Speichern ------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag t) {
        t.put("nations", list(nations.values(), Nation::toNbt));
        t.put("wars", list(wars, War::toNbt));
        t.putInt("nextWarId", nextWarId);
        CompoundTag tr = new CompoundTag();
        for (Map.Entry<String, Treasury> e : treasuries.entrySet()) tr.put(e.getKey(), e.getValue().toNbt());
        t.put("treasuries", tr);
        t.put("evidence", list(evidence, EvidenceEntry::toNbt));
        CompoundTag ac = new CompoundTag();
        for (Map.Entry<UUID, ActivityRecord> e : activity.entrySet()) ac.put(e.getKey().toString(), e.getValue().toNbt());
        t.put("activity", ac);
        t.putString("lastActivityReportWeek", lastActivityReportWeek);
        t.putString("karteModus", karteModus);
        t.put("elections", list(elections, Election::toNbt));
        t.putInt("nextElectionId", nextElectionId);
        t.put("mails", list(mails, Mail::toNbt));
        t.putInt("nextMailId", nextMailId);
        t.put("colonies", list(colonies.values(), Colony::toNbt));
        t.put("visas", list(visas, Visa::toNbt));
        t.put("contracts", list(contracts, Contract::toNbt));
        t.putInt("nextContractId", nextContractId);
        CompoundTag cl = new CompoundTag();
        for (Map.Entry<String, Map<Long, String>> dim : claims.entrySet()) {
            ListTag entries = new ListTag();
            for (Map.Entry<Long, String> c : dim.getValue().entrySet()) {
                CompoundTag e = new CompoundTag();
                e.putLong("c", c.getKey());
                e.putString("n", c.getValue());
                entries.add(e);
            }
            cl.put(dim.getKey(), entries);
        }
        t.put("claims", cl);
        CompoundTag w = new CompoundTag();
        for (Map.Entry<UUID, Double> e : wallet.entrySet()) w.putDouble(e.getKey().toString(), e.getValue());
        t.put("wallet", w);
        return t;
    }

    public static CrownsData load(CompoundTag t) {
        CrownsData d = new CrownsData();
        for (CompoundTag c : compounds(t, "nations")) {
            Nation n = Nation.fromNbt(c);
            d.nations.put(n.key(), n);
        }
        for (CompoundTag c : compounds(t, "wars")) d.wars.add(War.fromNbt(c));
        d.nextWarId = Math.max(1, t.getInt("nextWarId"));
        CompoundTag tr = t.getCompound("treasuries");
        for (String k : tr.getAllKeys()) d.treasuries.put(k, Treasury.fromNbt(tr.getCompound(k)));
        for (CompoundTag c : compounds(t, "evidence")) d.evidence.addLast(EvidenceEntry.fromNbt(c));
        CompoundTag ac = t.getCompound("activity");
        for (String k : ac.getAllKeys()) {
            try {
                d.activity.put(UUID.fromString(k), ActivityRecord.fromNbt(ac.getCompound(k)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        d.lastActivityReportWeek = t.getString("lastActivityReportWeek");
        d.karteModus = t.getString("karteModus");
        for (CompoundTag c : compounds(t, "elections")) d.elections.add(Election.fromNbt(c));
        d.nextElectionId = Math.max(1, t.getInt("nextElectionId"));
        for (CompoundTag c : compounds(t, "mails")) d.mails.add(Mail.fromNbt(c));
        d.nextMailId = Math.max(1, t.getInt("nextMailId"));
        for (CompoundTag c : compounds(t, "colonies")) {
            Colony col = Colony.fromNbt(c);
            d.colonies.put(col.name.toLowerCase(Locale.ROOT), col);
        }
        for (CompoundTag c : compounds(t, "visas")) d.visas.add(Visa.fromNbt(c));
        for (CompoundTag c : compounds(t, "contracts")) d.contracts.add(Contract.fromNbt(c));
        d.nextContractId = Math.max(1, t.getInt("nextContractId"));
        CompoundTag cl = t.getCompound("claims");
        for (String dim : cl.getAllKeys()) {
            Map<Long, String> map = new HashMap<>();
            ListTag entries = cl.getList(dim, Tag.TAG_COMPOUND);
            for (int i = 0; i < entries.size(); i++) {
                CompoundTag e = entries.getCompound(i);
                map.put(e.getLong("c"), e.getString("n"));
            }
            d.claims.put(dim, map);
        }
        CompoundTag w = t.getCompound("wallet");
        for (String k : w.getAllKeys()) {
            try {
                d.wallet.put(UUID.fromString(k), w.getDouble(k));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return d;
    }

    private static <T> ListTag list(Iterable<T> items, java.util.function.Function<T, CompoundTag> f) {
        ListTag l = new ListTag();
        for (T item : items) l.add(f.apply(item));
        return l;
    }

    private static List<CompoundTag> compounds(CompoundTag t, String key) {
        List<CompoundTag> out = new ArrayList<>();
        ListTag l = t.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) out.add(l.getCompound(i));
        return out;
    }
}
