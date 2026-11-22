package de.niklasmetzger.crownscore.client;

import de.niklasmetzger.crownscore.CrownsCore;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.util.List;

/** Client-Teil von Crowns Core: Flaggen als Umhang und auf Xaero's Karten. Wird nur auf dem Client geladen. */
public final class ClientSetup {
    private ClientSetup() {}

    @Mod.EventBusSubscriber(modid = CrownsCore.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModEvents {
        private ModEvents() {}

        @SubscribeEvent
        public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
            int n = 0;
            for (String skin : event.getSkins()) {
                PlayerRenderer r = event.getSkin(skin);
                if (r != null && install(r)) n++;
            }
            CrownsCore.LOGGER.info("Umhaenge: Flaggen-Ebene in {} Spielermodelle eingebaut", n);
        }

        /** Die Umhang-Ebene von Minecraft durch die eigene ersetzen (die faellt ohne Flagge auf sie zurueck). */
        @SuppressWarnings({"unchecked", "rawtypes"})
        private static boolean install(PlayerRenderer r) {
            try {
                Object raw = ObfuscationReflectionHelper.getPrivateValue((Class) LivingEntityRenderer.class, r, "f_115291_");
                List<RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>> layers = (List) raw;
                for (int i = 0; i < layers.size(); i++) {
                    if (layers.get(i) instanceof FlagCapeLayer) return true;
                    if (layers.get(i) instanceof CapeLayer cape) {
                        layers.set(i, new FlagCapeLayer(r, cape));
                        return true;
                    }
                }
                layers.add(new FlagCapeLayer(r, null));
                return true;
            } catch (Exception e) {
                CrownsCore.LOGGER.error("Umhang-Ebene konnte nicht eingebaut werden", e);
                return false;
            }
        }
    }

    @Mod.EventBusSubscriber(modid = CrownsCore.MODID, value = Dist.CLIENT)
    public static final class ForgeEvents {
        private ForgeEvents() {}

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientCapes.clear();
            ClientMapFlags.clear();
        }

        @SubscribeEvent
        public static void onTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
            if (event.phase == net.minecraftforge.event.TickEvent.Phase.END) XaeroBridge.tick();
        }
    }
}
