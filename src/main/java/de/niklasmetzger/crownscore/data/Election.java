package de.niklasmetzger.crownscore.data;

import de.niklasmetzger.crownscore.util.NbtUtil;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Eine Wahl innerhalb einer Nation oder eine Abstimmung im Parlament. */
public class Election {
    public int id;
    /** Nationsschluessel, leer bei Parlamentsabstimmungen. */
    public String nation = "";
    public boolean parlament;
    public String titel = "";
    public final List<String> optionen = new ArrayList<>();
    /** Stimmen: Spieler-UUID (Nation) oder Nationsschluessel (Parlament) zu Option. */
    public final Map<String, String> stimmen = new LinkedHashMap<>();
    public long start;
    public long ende;
    /** Gewinner wird Staatschef. */
    public boolean amt;
    public boolean beendet;
    public String ergebnis = "";

    public Map<String, Integer> auszaehlung() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String o : optionen) out.put(o, 0);
        for (String v : stimmen.values()) out.merge(v, 1, Integer::sum);
        return out;
    }

    public CompoundTag toNbt() {
        CompoundTag t = new CompoundTag();
        t.putInt("id", id);
        t.putString("nation", nation);
        t.putBoolean("parlament", parlament);
        t.putString("titel", titel);
        t.put("optionen", NbtUtil.strings(optionen));
        CompoundTag s = new CompoundTag();
        for (Map.Entry<String, String> e : stimmen.entrySet()) s.putString(e.getKey(), e.getValue());
        t.put("stimmen", s);
        t.putLong("start", start);
        t.putLong("ende", ende);
        t.putBoolean("amt", amt);
        t.putBoolean("beendet", beendet);
        t.putString("ergebnis", ergebnis);
        return t;
    }

    public static Election fromNbt(CompoundTag t) {
        Election e = new Election();
        e.id = t.getInt("id");
        e.nation = t.getString("nation");
        e.parlament = t.getBoolean("parlament");
        e.titel = t.getString("titel");
        e.optionen.addAll(NbtUtil.stringList(t, "optionen"));
        CompoundTag s = t.getCompound("stimmen");
        for (String k : s.getAllKeys()) e.stimmen.put(k, s.getString(k));
        e.start = t.getLong("start");
        e.ende = t.getLong("ende");
        e.amt = t.getBoolean("amt");
        e.beendet = t.getBoolean("beendet");
        e.ergebnis = t.getString("ergebnis");
        return e;
    }
}
