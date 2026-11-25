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
    /** Startpunkt der Angriffslinie auf der Karte (naechster Punkt im Gebiet des Angreifers). */
    public int vonX, vonZ;
    /** Sekunden ohne Angreifer bzw. ohne Verteidiger in der Naehe (ueberleben einen Neustart). */
    public int angreiferWeg, verteidigerWeg;
    /** Wer den Block zerstoert hat, solange der Chunk nicht geladen war; null = steht noch. */
    public String zerstoertVon;
    /** Verteidiger, die waehrend dieser Besetzung gefallen sind und sie nicht mehr blockieren. */
    public final java.util.Set<UUID> gefallen = new java.util.HashSet<>();
    /** Verteidiger, die waehrend dieser Besetzung gekickt oder gebannt wurden und noch nicht zurueck sind. */
    public final java.util.Set<UUID> gesperrt = new java.util.HashSet<>();

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
        t.putInt("vonX", vonX);
        t.putInt("vonZ", vonZ);
        t.putInt("angreiferWeg", angreiferWeg);
        t.putInt("verteidigerWeg", verteidigerWeg);
        if (zerstoertVon != null) t.putString("zerstoertVon", zerstoertVon);
        t.put("gefallen", uuids(gefallen));
        t.put("gesperrt", uuids(gesperrt));
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
        b.vonX = t.getInt("vonX");
        b.vonZ = t.getInt("vonZ");
        b.angreiferWeg = t.getInt("angreiferWeg");
        b.verteidigerWeg = t.getInt("verteidigerWeg");
        b.zerstoertVon = t.contains("zerstoertVon") ? t.getString("zerstoertVon") : null;
        leseUuids(t, "gefallen", b.gefallen);
        leseUuids(t, "gesperrt", b.gesperrt);
        return b;
    }

    private static net.minecraft.nbt.ListTag uuids(java.util.Set<UUID> ids) {
        net.minecraft.nbt.ListTag l = new net.minecraft.nbt.ListTag();
        for (UUID id : ids) l.add(net.minecraft.nbt.StringTag.valueOf(id.toString()));
        return l;
    }

    private static void leseUuids(CompoundTag t, String key, java.util.Set<UUID> ziel) {
        for (net.minecraft.nbt.Tag x : t.getList(key, net.minecraft.nbt.Tag.TAG_STRING)) {
            try {
                ziel.add(UUID.fromString(x.getAsString()));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}
