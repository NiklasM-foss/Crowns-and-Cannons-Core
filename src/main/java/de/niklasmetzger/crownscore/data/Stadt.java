package de.niklasmetzger.crownscore.data;

import de.niklasmetzger.crownscore.util.NbtUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/** Eine gegruendete Stadt einer Nation. Zaehlt fuer die Grenze beim Villager-Haendler. */
public class Stadt {
    public String name;
    public String nation;
    public String dim;
    public BlockPos pos;
    public long gegruendet;
    public UUID von;
    public int betten;
    /** Von einem Admin bestaetigt. Nur bestaetigte Staedte zaehlen. */
    public boolean bestaetigt;
    public String bestaetigtVon = "";
    public boolean glocke;
    /** Id beim Antrags-Dienst (Browser), leer = keiner. */
    public String antragId = "";
    /** Spieler, die schon einmal im Stadtgebiet waren, seit die Stadt anerkannt ist (sehen sie dann auf der Karte). */
    public final java.util.Set<UUID> entdeckt = new java.util.LinkedHashSet<>();
    /** Hauptbanner im Hauptgebaeude (/stadt banner), dort wird bei einer Besetzung gekaempft; null = Stadtmitte. */
    public BlockPos banner;

    /** Ort, um den bei einer Besetzung gekaempft wird. */
    public BlockPos bannerPos() {
        return banner != null ? banner : pos;
    }

    public CompoundTag toNbt() {
        CompoundTag t = new CompoundTag();
        t.putString("name", name);
        t.putString("nation", nation);
        t.putString("dim", dim);
        t.putLong("pos", pos.asLong());
        t.putLong("gegruendet", gegruendet);
        NbtUtil.putUuid(t, "von", von);
        t.putInt("betten", betten);
        t.putBoolean("bestaetigt", bestaetigt);
        t.putString("bestaetigtVon", bestaetigtVon);
        t.putBoolean("glocke", glocke);
        t.putString("antragId", antragId);
        t.put("entdeckt", NbtUtil.uuids(entdeckt));
        if (banner != null) t.putLong("banner", banner.asLong());
        return t;
    }

    public static Stadt fromNbt(CompoundTag t) {
        Stadt s = new Stadt();
        s.name = t.getString("name");
        s.nation = t.getString("nation");
        s.dim = t.getString("dim");
        s.pos = BlockPos.of(t.getLong("pos"));
        s.gegruendet = t.getLong("gegruendet");
        s.von = NbtUtil.uuid(t, "von");
        s.betten = t.getInt("betten");
        s.bestaetigt = t.getBoolean("bestaetigt");
        s.bestaetigtVon = t.getString("bestaetigtVon");
        s.glocke = t.getBoolean("glocke");
        s.antragId = t.getString("antragId");
        s.entdeckt.addAll(NbtUtil.uuidSet(t, "entdeckt"));
        s.banner = t.contains("banner") ? BlockPos.of(t.getLong("banner")) : null;
        return s;
    }
}
