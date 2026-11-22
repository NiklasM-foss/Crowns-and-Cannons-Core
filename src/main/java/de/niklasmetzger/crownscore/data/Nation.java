package de.niklasmetzger.crownscore.data;

import de.niklasmetzger.crownscore.util.NbtUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Eine Nation mit Staatschef, Mitgliedern, Beamten und Einstellungen. */
public class Nation {
    public String name;
    public String kuerzel;
    public UUID chef;
    /** Mitglieder mit zuletzt bekanntem Namen. */
    public final Map<UUID, String> mitglieder = new LinkedHashMap<>();
    /** Beamte duerfen Visa erteilen, Gebiete verwalten und im Parlament abstimmen. */
    public final Set<UUID> beamte = new LinkedHashSet<>();
    /** Offene Beitrittsanfragen mit Namen. */
    public final Map<UUID, String> anfragen = new LinkedHashMap<>();
    /** KEINE, DIREKT oder PARLAMENT. */
    public String wahlmodus = "DIREKT";
    public int amtszeitTage = 0;
    /** Nationen (Schluessel), die die Wegsteine dieser Nation nutzen duerfen. */
    public final Set<String> wegsteinFreigaben = new LinkedHashSet<>();
    public long gegruendet;
    /** Stammland auf der Weltkarte (Natural-Earth-Code wie DEU), leer = keins. */
    public String land = "";
    /** Alle Laender der Weltkarte, die diese Nation haelt: das Stammland plus Eroberungen. */
    public final Set<String> laender = new LinkedHashSet<>();
    /** Staatskonto: gehoert die Claims in Open Parties and Claims, bleibt bei Umbenennung gleich. */
    public UUID staat;
    /** Party der Nation in Open Parties and Claims. */
    public UUID partei;

    public Nation() {}

    public Nation(String name, String kuerzel) {
        this.name = name;
        this.kuerzel = kuerzel.toUpperCase(Locale.ROOT);
        this.gegruendet = System.currentTimeMillis();
    }

    public String key() {
        return key(name);
    }

    public static String key(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }

    /** Staatskonto, beim ersten Aufruf aus Stammland oder Name abgeleitet und dann fest gespeichert. */
    public UUID staat() {
        if (staat == null) {
            String basis = land != null && !land.isEmpty() ? land : key();
            staat = UUID.nameUUIDFromBytes(("crownscore-staat:" + basis).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return staat;
    }

    /** Wird die Nation gespielt (Staatschef oder Mitglieder)? */
    public boolean gespielt() {
        return chef != null || !mitglieder.isEmpty();
    }

    public boolean isMember(UUID id) {
        return id != null && mitglieder.containsKey(id);
    }

    public boolean isChef(UUID id) {
        return id != null && id.equals(chef);
    }

    public boolean isBeamter(UUID id) {
        return isChef(id) || (id != null && beamte.contains(id));
    }

    public String anzeige() {
        return name + " [" + kuerzel + "]";
    }

    public CompoundTag toNbt() {
        CompoundTag t = new CompoundTag();
        t.putString("name", name);
        t.putString("kuerzel", kuerzel);
        NbtUtil.putUuid(t, "chef", chef);
        t.put("mitglieder", mapToList(mitglieder));
        t.put("beamte", NbtUtil.uuids(beamte));
        t.put("anfragen", mapToList(anfragen));
        t.putString("wahlmodus", wahlmodus);
        t.putInt("amtszeitTage", amtszeitTage);
        t.put("wegsteinFreigaben", NbtUtil.strings(wegsteinFreigaben));
        t.putLong("gegruendet", gegruendet);
        t.putString("land", land == null ? "" : land);
        t.put("laender", NbtUtil.strings(laender));
        NbtUtil.putUuid(t, "staat", staat);
        NbtUtil.putUuid(t, "partei", partei);
        return t;
    }

    public static Nation fromNbt(CompoundTag t) {
        Nation n = new Nation();
        n.name = t.getString("name");
        n.kuerzel = t.getString("kuerzel");
        n.chef = NbtUtil.uuid(t, "chef");
        listToMap(t.getList("mitglieder", Tag.TAG_COMPOUND), n.mitglieder);
        n.beamte.addAll(NbtUtil.uuidSet(t, "beamte"));
        listToMap(t.getList("anfragen", Tag.TAG_COMPOUND), n.anfragen);
        n.wahlmodus = t.contains("wahlmodus") ? t.getString("wahlmodus") : "DIREKT";
        n.amtszeitTage = t.getInt("amtszeitTage");
        n.wegsteinFreigaben.addAll(NbtUtil.stringSet(t, "wegsteinFreigaben"));
        n.gegruendet = t.getLong("gegruendet");
        n.land = t.getString("land");
        n.laender.addAll(NbtUtil.stringSet(t, "laender"));
        if (!t.contains("laender") && !n.land.isEmpty()) n.laender.add(n.land); // nur Spielstaende vor den Eroberungen
        n.staat = NbtUtil.uuid(t, "staat");
        n.partei = NbtUtil.uuid(t, "partei");
        return n;
    }

    private static ListTag mapToList(Map<UUID, String> map) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, String> e : map.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putString("id", e.getKey().toString());
            c.putString("name", e.getValue() == null ? "" : e.getValue());
            list.add(c);
        }
        return list;
    }

    private static void listToMap(ListTag list, Map<UUID, String> map) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            UUID id = NbtUtil.uuid(c, "id");
            if (id != null) map.put(id, c.getString("name"));
        }
    }
}
