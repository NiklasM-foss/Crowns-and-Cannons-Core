package de.niklasmetzger.crownscore.net;

import de.niklasmetzger.crownscore.CrownsCore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Netzwerkkanal zwischen Server und Client. Beide Seiten duerfen ohne die Mod sein:
 * Spieler ohne Crowns Core koennen sich verbinden, sie sehen dann nur keine Umhaenge.
 */
public final class CcNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(CrownsCore.MODID, "main"), () -> VERSION,
            NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));

    private CcNetwork() {}

    /** Textur eines Umhangs (PNG), einmal pro Spieler und Textur. */
    public record CapeTexture(String hash, byte[] png) {
        void write(FriendlyByteBuf b) {
            b.writeUtf(hash, 64);
            b.writeByteArray(png);
        }

        static CapeTexture read(FriendlyByteBuf b) {
            return new CapeTexture(b.readUtf(64), b.readByteArray(1 << 21));
        }
    }

    /** Welcher Spieler welchen Umhang traegt. Leerer Hash = keiner. full = vorherige Liste verwerfen. */
    public record CapeMap(boolean full, Map<UUID, String> capes) {
        void write(FriendlyByteBuf b) {
            b.writeBoolean(full);
            b.writeVarInt(capes.size());
            capes.forEach((id, h) -> {
                b.writeUUID(id);
                b.writeUtf(h, 64);
            });
        }

        static CapeMap read(FriendlyByteBuf b) {
            boolean full = b.readBoolean();
            int n = b.readVarInt();
            Map<UUID, String> m = new LinkedHashMap<>();
            for (int i = 0; i < n; i++) m.put(b.readUUID(), b.readUtf(64));
            return new CapeMap(full, m);
        }
    }

    public static void init() {
        CHANNEL.messageBuilder(CapeTexture.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CapeTexture::write).decoder(CapeTexture::read)
                .consumerMainThread((msg, ctx) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> de.niklasmetzger.crownscore.client.ClientCapes.texture(msg.hash(), msg.png())))
                .add();
        CHANNEL.messageBuilder(CapeMap.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CapeMap::write).decoder(CapeMap::read)
                .consumerMainThread((msg, ctx) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> de.niklasmetzger.crownscore.client.ClientCapes.map(msg.full(), msg.capes())))
                .add();
    }

    /** Hat der Client des Spielers Crowns Core? */
    public static boolean present(ServerPlayer p) {
        return p.connection != null && CHANNEL.isRemotePresent(p.connection.connection);
    }

    public static void send(ServerPlayer p, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), msg);
    }
}
