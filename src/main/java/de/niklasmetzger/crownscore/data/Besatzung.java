package de.niklasmetzger.crownscore.data;

import de.niklasmetzger.crownscore.util.NbtUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/** Eine laufende Besetzung: ein Besatzungsblock steht in einem fremden Land und muss gehalten werden. */
public class Besatzung {
    public int id;
    /** Nation, die besetzt. */
    public String angreifer;
    /** Nation, der das Land gerade gehoert. */
    public String verteidiger;
    /** Laendercode auf der Weltkarte. */
    public String land;
    /** Name der besetzten Stadt, leer = ganzes Land. */
    public String stadt = "";
    public String dim;
    public BlockPos pos;
    public long start;
    /** Noch zu haltende Sekunden. */
    public long rest;
    public UUID von;
    public double gebuehr;

    public CompoundTag toNbt() {
        CompoundTag t = new CompoundTag();
        t.putInt("id", id);
        t.putString("angreifer", angreifer);
        t.putString("verteidiger", verteidiger);
        t.putString("land", land);
        t.putString("stadt", stadt);
        t.putString("dim", dim);
        t.putLong("pos", pos.asLong());
        t.putLong("start", start);
        t.putLong("rest", rest);
        NbtUtil.putUuid(t, "von", von);
        t.putDouble("gebuehr", gebuehr);
        return t;
    }

    public static Besatzung fromNbt(CompoundTag t) {
        Besatzung b = new Besatzung();
        b.id = t.getInt("id");
        b.angreifer = t.getString("angreifer");
        b.verteidiger = t.getString("verteidiger");
        b.land = t.getString("land");
        b.stadt = t.getString("stadt");
        b.dim = t.getString("dim");
        b.pos = BlockPos.of(t.getLong("pos"));
        b.start = t.getLong("start");
        b.rest = t.getLong("rest");
        b.von = NbtUtil.uuid(t, "von");
        b.gebuehr = t.getDouble("gebuehr");
        return b;
    }
}
