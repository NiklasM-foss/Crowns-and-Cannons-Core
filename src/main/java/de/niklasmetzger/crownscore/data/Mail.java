package de.niklasmetzger.crownscore.data;

import de.niklasmetzger.crownscore.util.NbtUtil;
import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Ein Brief zwischen Nationen. */
public class Mail {
    public int id;
    public UUID von;
    public String vonName = "";
    public String vonNation = "";
    public String anNation = "";
    public String text = "";
    public long zeit;
    /** Nationen, die eine abgefangene Kopie besitzen. */
    public final Set<String> abgefangenVon = new LinkedHashSet<>();
    public boolean gelesen;

    public CompoundTag toNbt() {
        CompoundTag t = new CompoundTag();
        t.putInt("id", id);
        NbtUtil.putUuid(t, "von", von);
        t.putString("vonName", vonName);
        t.putString("vonNation", vonNation);
        t.putString("anNation", anNation);
        t.putString("text", text);
        t.putLong("zeit", zeit);
        t.put("abgefangenVon", NbtUtil.strings(abgefangenVon));
        t.putBoolean("gelesen", gelesen);
        return t;
    }

    public static Mail fromNbt(CompoundTag t) {
        Mail m = new Mail();
        m.id = t.getInt("id");
        m.von = NbtUtil.uuid(t, "von");
        m.vonName = t.getString("vonName");
        m.vonNation = t.getString("vonNation");
        m.anNation = t.getString("anNation");
        m.text = t.getString("text");
        m.zeit = t.getLong("zeit");
        m.abgefangenVon.addAll(NbtUtil.stringSet(t, "abgefangenVon"));
        m.gelesen = t.getBoolean("gelesen");
        return m;
    }
}
