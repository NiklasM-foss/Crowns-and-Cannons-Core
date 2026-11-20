package de.niklasmetzger.crownscore.selftest;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-Test fuer die Umhaenge: nur aktiv mit -Dcrownscore.shot=1 (./gradlew runClient -PselftestClient).
 * Wartet nach dem Betreten der Welt, schaltet auf die Ansicht von hinten und von vorne,
 * speichert je einen Screenshot unter run/screenshots/ und beendet das Spiel.
 */
@Mod.EventBusSubscriber(modid = "crownscoretest", value = Dist.CLIENT)
public final class ClientShot {
    private static final Logger LOG = LoggerFactory.getLogger("CCTEST");
    private static int ticks = -1;

    private ClientShot() {}

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !"1".equals(System.getProperty("crownscore.shot"))) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        ticks++;
        switch (ticks) {
            case 100 -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            case 160 -> shot(mc, "umhang_hinten.png");
            case 170 -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            case 230 -> shot(mc, "umhang_vorne.png");
            case 240 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                mc.player.setYRot(mc.player.getYRot() + 35);
                mc.player.setXRot(20);
            }
            case 300 -> shot(mc, "umhang_schraeg.png");
            case 320 -> {
                LOG.info("[CCTEST] Client-Screenshots fertig");
                mc.stop();
            }
            default -> { }
        }
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), msg -> LOG.info("[CCTEST] {}", msg.getString()));
    }
}
