package de.niklasmetzger.crownscore.util;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

/** Spielernamen aus UUIDs, auch wenn der Spieler offline ist. */
public final class Names {
    private Names() {}

    public static String of(MinecraftServer server, UUID id) {
        if (id == null) return "?";
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) return online.getGameProfile().getName();
        if (server.getProfileCache() != null) {
            Optional<GameProfile> gp = server.getProfileCache().get(id);
            if (gp.isPresent() && gp.get().getName() != null) return gp.get().getName();
        }
        return id.toString().substring(0, 8);
    }

    /** UUID zu einem Namen, online oder aus dem Profil-Cache. */
    public static Optional<UUID> lookup(MinecraftServer server, String name) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) return Optional.of(online.getUUID());
        if (server.getProfileCache() != null) {
            Optional<GameProfile> gp = server.getProfileCache().get(name);
            if (gp.isPresent()) return Optional.ofNullable(gp.get().getId());
        }
        return Optional.empty();
    }
}
