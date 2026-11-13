package de.niklasmetzger.crownscore.data;

import de.niklasmetzger.crownscore.util.NbtUtil;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/** Ein Visum: Spieler darf das Gebiet einer Nation bis zu einem Zeitpunkt betreten. */
public class Visa {
    public UUID spieler;
    public String spielerName = "";
    public String nation = "";
    public long bis;
    public UUID erteiltVon;
    public long erteilt;

    public boolean gueltig(long now) {
        return bis > now;
    }

    public CompoundTag toNbt() {
        CompoundTag t = new CompoundTag();
        NbtUtil.putUuid(t, "spieler", spieler);
        t.putString("spielerName", spielerName);
        t.putString("nation", nation);
        t.putLong("bis", bis);
        NbtUtil.putUuid(t, "erteiltVon", erteiltVon);
        t.putLong("erteilt", erteilt);
        return t;
    }

    public static Visa fromNbt(CompoundTag t) {
        Visa v = new Visa();
        v.spieler = NbtUtil.uuid(t, "spieler");
        v.spielerName = t.getString("spielerName");
        v.nation = t.getString("nation");
        v.bis = t.getLong("bis");
        v.erteiltVon = NbtUtil.uuid(t, "erteiltVon");
        v.erteilt = t.getLong("erteilt");
        return v;
    }
}
