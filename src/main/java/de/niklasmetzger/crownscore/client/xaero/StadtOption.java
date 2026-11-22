package de.niklasmetzger.crownscore.client.xaero;

import de.niklasmetzger.crownscore.client.ClientMapFlags;
import de.niklasmetzger.crownscore.client.StadtAntragScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.map.gui.IRightClickableElement;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

import java.util.ArrayList;

/** Eintrag "Stadt hier beantragen" im Rechtsklick-Menue der World Map. */
public final class StadtOption extends RightClickOption {
    private final int x, z;

    private StadtOption(int index, IRightClickableElement target, int x, int z) {
        super("Stadt hier beantragen", index, target);
        this.x = x;
        this.z = z;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void anhaengen(ArrayList list, Object karte, int x, int z, ResourceKey<Level> dim) {
        if (list == null || dim != Level.OVERWORLD || !ClientMapFlags.serverHatStaedte()) return;
        list.add(new StadtOption(list.size(), (IRightClickableElement) karte, x, z));
    }

    @Override
    public void onAction(Screen screen) {
        Minecraft.getInstance().setScreen(new StadtAntragScreen(screen, x, z));
    }
}
