package de.niklasmetzger.crownscore.data;

import de.niklasmetzger.crownscore.util.NbtUtil;
import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashSet;
import java.util.Set;

/** Ein erklaerter Konflikt zwischen zwei Nationen. */
public class War {
    public int id;
    public String angreifer;
    public String verteidiger;
    public WarType art = WarType.KRIEG;
    public String grund = "";
    public long start;
    public long ende;
    public boolean aktiv = true;
    public long ersterKampf;
    /** Nationen, die Frieden vorgeschlagen haben. Beide drin = Frieden. */
    public final Set<String> friedensvorschlag = new LinkedHashSet<>();
    public String ergebnis = "";

    public boolean beteiligt(String nationKey) {
        return nationKey != null && (nationKey.equals(angreifer) || nationKey.equals(verteidiger));
    }

    public boolean zwischen(String a, String b) {
        if (a == null || b == null) return false;
        return (a.equals(angreifer) && b.equals(verteidiger)) || (b.equals(angreifer) && a.equals(verteidiger));
    }

    public String gegner(String nationKey) {
        return nationKey.equals(angreifer) ? verteidiger : angreifer;
    }

    public CompoundTag toNbt() {
        CompoundTag t = new CompoundTag();
        t.putInt("id", id);
        t.putString("angreifer", angreifer);
        t.putString("verteidiger", verteidiger);
        t.putString("art", art.name());
        t.putString("grund", grund);
        t.putLong("start", start);
        t.putLong("ende", ende);
        t.putBoolean("aktiv", aktiv);
        t.putLong("ersterKampf", ersterKampf);
        t.put("friedensvorschlag", NbtUtil.strings(friedensvorschlag));
        t.putString("ergebnis", ergebnis);
        return t;
    }

    public static War fromNbt(CompoundTag t) {
        War w = new War();
        w.id = t.getInt("id");
        w.angreifer = t.getString("angreifer");
        w.verteidiger = t.getString("verteidiger");
        WarType art = WarType.parse(t.getString("art"));
        w.art = art == null ? WarType.KRIEG : art;
        w.grund = t.getString("grund");
        w.start = t.getLong("start");
        w.ende = t.getLong("ende");
        w.aktiv = t.getBoolean("aktiv");
        w.ersterKampf = t.getLong("ersterKampf");
        w.friedensvorschlag.addAll(NbtUtil.stringSet(t, "friedensvorschlag"));
        w.ergebnis = t.getString("ergebnis");
        return w;
    }
}
