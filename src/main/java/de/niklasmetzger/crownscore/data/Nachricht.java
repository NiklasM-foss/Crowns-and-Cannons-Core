package de.niklasmetzger.crownscore.data;

import de.niklasmetzger.crownscore.util.NbtUtil;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/** Eine Nachricht im Postfach eines Spielers (Antraege, Kriegsmeldungen, Freigaben). */
public class Nachricht {
    public int id;
    public UUID an;
    public String titel = "";
    public String text = "";
    public long zeit;
    public boolean gelesen;
    /** Befehl hinter einem Knopf in der Nachricht, leer = keiner. */
    public String befehl = "";

    public CompoundTag toNbt() {
        CompoundTag t = new CompoundTag();
        t.putInt("id", id);
        NbtUtil.putUuid(t, "an", an);
        t.putString("titel", titel);
        t.putString("text", text);
        t.putLong("zeit", zeit);
        t.putBoolean("gelesen", gelesen);
        t.putString("befehl", befehl);
        return t;
    }

    public static Nachricht fromNbt(CompoundTag t) {
        Nachricht n = new Nachricht();
        n.id = t.getInt("id");
        n.an = NbtUtil.uuid(t, "an");
        n.titel = t.getString("titel");
        n.text = t.getString("text");
        n.zeit = t.getLong("zeit");
        n.gelesen = t.getBoolean("gelesen");
        n.befehl = t.getString("befehl");
        return n;
    }
}
