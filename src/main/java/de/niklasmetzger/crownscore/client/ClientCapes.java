package de.niklasmetzger.crownscore.client;

import com.mojang.blaze3d.platform.NativeImage;
import de.niklasmetzger.crownscore.CrownsCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Umhaenge auf dem Client: Texturen vom Server und wer welchen traegt. Nur auf dem Client geladen. */
public final class ClientCapes {
    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();
    private static final Map<UUID, String> PLAYERS = new HashMap<>();

    private ClientCapes() {}

    public static void texture(String hash, byte[] png) {
        if (TEXTURES.containsKey(hash) || !hash.matches("[0-9a-f]{8,64}")) return;
        try {
            NativeImage img = NativeImage.read(new ByteArrayInputStream(png));
            ResourceLocation rl = new ResourceLocation(CrownsCore.MODID, "umhang/" + hash);
            Minecraft.getInstance().getTextureManager().register(rl, new DynamicTexture(img));
            TEXTURES.put(hash, rl);
        } catch (Exception e) {
            CrownsCore.LOGGER.warn("Umhang-Textur {} nicht lesbar: {}", hash, e.toString());
        }
    }

    public static void map(boolean full, Map<UUID, String> capes) {
        if (full) PLAYERS.clear();
        capes.forEach((id, h) -> {
            if (h == null || h.isEmpty()) PLAYERS.remove(id);
            else PLAYERS.put(id, h);
        });
    }

    /** Textur des Umhangs eines Spielers oder null. */
    public static ResourceLocation get(UUID player) {
        String h = PLAYERS.get(player);
        return h == null ? null : TEXTURES.get(h);
    }

    public static void clear() {
        PLAYERS.clear();
        for (ResourceLocation rl : TEXTURES.values()) Minecraft.getInstance().getTextureManager().release(rl);
        TEXTURES.clear();
    }
}
