package de.niklasmetzger.crownscore.data;

import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashMap;
import java.util.Map;

/** Spielzeit eines Spielers je Kalenderwoche, getrennt nach Werktag und Wochenende. */
public class ActivityRecord {
    public String name = "";
    /** Wochenschluessel zu {Werktagsekunden, Wochenendsekunden}. */
    public final Map<String, long[]> wochen = new LinkedHashMap<>();
    public long pauseBis;
    public long zuletztOnline;

    public long[] woche(String key) {
        return wochen.computeIfAbsent(key, k -> new long[2]);
    }

    public long gesamt(String key) {
        long[] w = wochen.get(key);
        return w == null ? 0 : w[0] + w[1];
    }

    public long wochenende(String key) {
        long[] w = wochen.get(key);
        return w == null ? 0 : w[1];
    }

    public CompoundTag toNbt() {
        CompoundTag t = new CompoundTag();
        t.putString("name", name);
        t.putLong("pauseBis", pauseBis);
        t.putLong("zuletztOnline", zuletztOnline);
        CompoundTag w = new CompoundTag();
        for (Map.Entry<String, long[]> e : wochen.entrySet()) {
            w.putLongArray(e.getKey(), e.getValue());
        }
        t.put("wochen", w);
        return t;
    }

    public static ActivityRecord fromNbt(CompoundTag t) {
        ActivityRecord r = new ActivityRecord();
        r.name = t.getString("name");
        r.pauseBis = t.getLong("pauseBis");
        r.zuletztOnline = t.getLong("zuletztOnline");
        CompoundTag w = t.getCompound("wochen");
        for (String k : w.getAllKeys()) {
            long[] arr = w.getLongArray(k);
            if (arr.length == 2) r.wochen.put(k, arr);
        }
        return r;
    }
}
