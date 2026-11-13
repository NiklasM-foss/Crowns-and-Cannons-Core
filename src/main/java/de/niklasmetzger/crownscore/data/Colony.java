package de.niklasmetzger.crownscore.data;

import net.minecraft.nbt.CompoundTag;

import java.util.List;

/** Ein Kolonialgebiet im Verfahren der Kolonialbehoerde. */
public class Colony {
    public static final List<String> STUFEN_HERRSCHER = List.of(
            "Expedition", "Diplomatische Mission", "Vertrag", "Einfluss und Praesenz", "Kolonialstatus");
    public static final List<String> STUFEN_FREI = List.of(
            "Expedition", "Niederlassung", "Erschliessung", "Kolonialanspruch");

    public String name;
    public String nation;
    /** HERRSCHER oder FREI. */
    public String typ = "FREI";
    /** Anzahl abgeschlossener Stufen. */
    public int stufe;
    public String dim = "minecraft:overworld";
    public int x, z;
    public int radius;
    public long angelegt;
    /** Antrag auf die letzte Stufe liegt bei der Behoerde. */
    public boolean antragOffen;
    public boolean anerkannt;
    public double gezahlt;
    public String notiz = "";

    public List<String> stufen() {
        return "HERRSCHER".equals(typ) ? STUFEN_HERRSCHER : STUFEN_FREI;
    }

    public String stufenName(int index) {
        List<String> s = stufen();
        return index >= 0 && index < s.size() ? s.get(index) : "?";
    }

    public boolean fertig() {
        return anerkannt;
    }

    public String status() {
        if (anerkannt) return "anerkannte Kolonie";
        if (antragOffen) return "Antrag liegt bei der Kolonialbehoerde";
        if (stufe >= stufen().size()) return "alle Stufen abgeschlossen";
        return "Stufe " + (stufe + 1) + "/" + stufen().size() + " offen: " + stufenName(stufe);
    }

    public CompoundTag toNbt() {
        CompoundTag t = new CompoundTag();
        t.putString("name", name);
        t.putString("nation", nation);
        t.putString("typ", typ);
        t.putInt("stufe", stufe);
        t.putString("dim", dim);
        t.putInt("x", x);
        t.putInt("z", z);
        t.putInt("radius", radius);
        t.putLong("angelegt", angelegt);
        t.putBoolean("antragOffen", antragOffen);
        t.putBoolean("anerkannt", anerkannt);
        t.putDouble("gezahlt", gezahlt);
        t.putString("notiz", notiz);
        return t;
    }

    public static Colony fromNbt(CompoundTag t) {
        Colony c = new Colony();
        c.name = t.getString("name");
        c.nation = t.getString("nation");
        c.typ = t.getString("typ");
        c.stufe = t.getInt("stufe");
        c.dim = t.getString("dim");
        c.x = t.getInt("x");
        c.z = t.getInt("z");
        c.radius = t.getInt("radius");
        c.angelegt = t.getLong("angelegt");
        c.antragOffen = t.getBoolean("antragOffen");
        c.anerkannt = t.getBoolean("anerkannt");
        c.gezahlt = t.getDouble("gezahlt");
        c.notiz = t.getString("notiz");
        return c;
    }
}
