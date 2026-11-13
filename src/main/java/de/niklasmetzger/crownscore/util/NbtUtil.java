package de.niklasmetzger.crownscore.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Kleine Helfer fuer NBT-Listen. */
public final class NbtUtil {
    private NbtUtil() {}

    public static ListTag strings(Collection<String> values) {
        ListTag list = new ListTag();
        for (String s : values) list.add(StringTag.valueOf(s));
        return list;
    }

    public static List<String> stringList(CompoundTag tag, String key) {
        List<String> out = new ArrayList<>();
        ListTag list = tag.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) out.add(list.getString(i));
        return out;
    }

    public static Set<String> stringSet(CompoundTag tag, String key) {
        return new LinkedHashSet<>(stringList(tag, key));
    }

    public static ListTag uuids(Collection<UUID> values) {
        ListTag list = new ListTag();
        for (UUID u : values) list.add(StringTag.valueOf(u.toString()));
        return list;
    }

    public static Set<UUID> uuidSet(CompoundTag tag, String key) {
        Set<UUID> out = new LinkedHashSet<>();
        for (String s : stringList(tag, key)) {
            try {
                out.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return out;
    }

    public static UUID uuid(CompoundTag tag, String key) {
        if (!tag.contains(key)) return null;
        try {
            return UUID.fromString(tag.getString(key));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static void putUuid(CompoundTag tag, String key, UUID id) {
        if (id != null) tag.putString(key, id.toString());
    }
}
