package de.niklasmetzger.crownscore.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;

/**
 * Haengt "Stadt hier beantragen" in das Rechtsklick-Menue von Xaero's World Map. Xaero selbst wird nicht
 * veraendert oder mitgeliefert; fehlt die World Map, wird dieser Mixin uebersprungen (Pseudo, nicht Pflicht).
 */
@Pseudo
@Mixin(targets = "xaero.map.gui.GuiMap", remap = false)
public abstract class GuiMapMixin {
    @Shadow
    private int rightClickX;
    @Shadow
    private int rightClickZ;
    @Shadow
    private ResourceKey<Level> rightClickDim;

    @Inject(method = "getRightClickOptions", at = @At("RETURN"), remap = false)
    private void crownscore$stadtAntrag(CallbackInfoReturnable<ArrayList<Object>> cir) {
        try {
            de.niklasmetzger.crownscore.client.xaero.StadtOption.anhaengen(cir.getReturnValue(), this, rightClickX, rightClickZ, rightClickDim);
        } catch (Throwable t) {
            de.niklasmetzger.crownscore.CrownsCore.LOGGER.debug("Stadt-Eintrag im Kartenmenue fehlgeschlagen: {}", t.toString());
        }
    }
}
