package de.niklasmetzger.crownscore.data;

import de.niklasmetzger.crownscore.util.NbtUtil;
import de.niklasmetzger.crownscore.util.TimeUtil;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/** Ein Eintrag in der Beweisakte. */
public class EvidenceEntry {
    public long zeit;
    /** BLOCK_ABBAU, BLOCK_SETZEN, KISTE, FEUER, CAMPING, EINREISE, TOETUNG, KRIEG. */
    public String typ;
    public UUID taeter;
    public String taeterName = "";
    public String taeterNation = "";
    public String gebietNation = "";
    public String dim = "";
    public int x, y, z;
    public String detail = "";

    public CompoundTag toNbt() {
        CompoundTag t = new CompoundTag();
        t.putLong("zeit", zeit);
        t.putString("typ", typ);
        NbtUtil.putUuid(t, "taeter", taeter);
        t.putString("taeterName", taeterName);
        t.putString("taeterNation", taeterNation);
        t.putString("gebietNation", gebietNation);
        t.putString("dim", dim);
        t.putInt("x", x);
        t.putInt("y", y);
        t.putInt("z", z);
        t.putString("detail", detail);
        return t;
    }

    public static EvidenceEntry fromNbt(CompoundTag t) {
        EvidenceEntry e = new EvidenceEntry();
        e.zeit = t.getLong("zeit");
        e.typ = t.getString("typ");
        e.taeter = NbtUtil.uuid(t, "taeter");
        e.taeterName = t.getString("taeterName");
        e.taeterNation = t.getString("taeterNation");
        e.gebietNation = t.getString("gebietNation");
        e.dim = t.getString("dim");
        e.x = t.getInt("x");
        e.y = t.getInt("y");
        e.z = t.getInt("z");
        e.detail = t.getString("detail");
        return e;
    }

    public String zeile() {
        String ort = dim.isEmpty() ? "" : " @ " + x + " " + y + " " + z + (dim.equals("minecraft:overworld") ? "" : " (" + dim + ")");
        String gebiet = gebietNation.isEmpty() ? "" : " in Gebiet " + gebietNation;
        return TimeUtil.fmtShort(zeit) + " " + typ + " " + taeterName
                + (taeterNation.isEmpty() ? "" : " (" + taeterNation + ")") + gebiet + ort
                + (detail.isEmpty() ? "" : ": " + detail);
    }
}
