package de.niklasmetzger.crownscore.data;

import de.niklasmetzger.crownscore.util.NbtUtil;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Hinterlegte Kopie eines Vertragsbuchs, unveraenderlich und mit Pruefsumme. */
public class Contract {
    public int id;
    public String titel = "";
    public String buchTitel = "";
    public String autor = "";
    public final List<String> seiten = new ArrayList<>();
    public String hash = "";
    public UUID hinterlegtVon;
    public String hinterlegtVonName = "";
    public String nation = "";
    public long zeit;

    public CompoundTag toNbt() {
        CompoundTag t = new CompoundTag();
        t.putInt("id", id);
        t.putString("titel", titel);
        t.putString("buchTitel", buchTitel);
        t.putString("autor", autor);
        t.put("seiten", NbtUtil.strings(seiten));
        t.putString("hash", hash);
        NbtUtil.putUuid(t, "hinterlegtVon", hinterlegtVon);
        t.putString("hinterlegtVonName", hinterlegtVonName);
        t.putString("nation", nation);
        t.putLong("zeit", zeit);
        return t;
    }

    public static Contract fromNbt(CompoundTag t) {
        Contract c = new Contract();
        c.id = t.getInt("id");
        c.titel = t.getString("titel");
        c.buchTitel = t.getString("buchTitel");
        c.autor = t.getString("autor");
        c.seiten.addAll(NbtUtil.stringList(t, "seiten"));
        c.hash = t.getString("hash");
        c.hinterlegtVon = NbtUtil.uuid(t, "hinterlegtVon");
        c.hinterlegtVonName = t.getString("hinterlegtVonName");
        c.nation = t.getString("nation");
        c.zeit = t.getLong("zeit");
        return c;
    }
}
