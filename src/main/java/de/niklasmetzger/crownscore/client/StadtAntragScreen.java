package de.niklasmetzger.crownscore.client;

import de.niklasmetzger.crownscore.net.CcNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Kleiner Dialog aus der World Map: Name eingeben, Antrag fuer die angeklickte Stelle stellen. */
public final class StadtAntragScreen extends Screen {
    private final Screen zurueck;
    private final int x, z;
    private EditBox name;
    private Button ok;

    public StadtAntragScreen(Screen zurueck, int x, int z) {
        super(Component.literal("Stadt beantragen"));
        this.zurueck = zurueck;
        this.x = x;
        this.z = z;
    }

    @Override
    protected void init() {
        int cx = width / 2, cy = height / 2;
        name = new EditBox(font, cx - 100, cy - 10, 200, 20, Component.literal("Name der Stadt"));
        name.setMaxLength(32);
        name.setHint(Component.literal("Name der Stadt"));
        name.setResponder(t -> ok.active = t.trim().length() >= 2);
        addRenderableWidget(name);
        ok = addRenderableWidget(Button.builder(Component.literal("Beantragen"), b -> senden()).bounds(cx - 100, cy + 20, 98, 20).build());
        ok.active = false;
        addRenderableWidget(Button.builder(Component.literal("Abbrechen"), b -> onClose()).bounds(cx + 2, cy + 20, 98, 20).build());
        setInitialFocus(name);
    }

    private void senden() {
        String n = name.getValue().trim();
        if (n.length() < 2) return;
        CcNetwork.CHANNEL.sendToServer(new CcNetwork.StadtAnfrage(n, x, z));
        if (minecraft != null) minecraft.setScreen(null);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((key == 257 || key == 335) && ok.active) {
            senden();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int cx = width / 2, cy = height / 2;
        g.drawCenteredString(font, title, cx, cy - 52, 0xF2C230);
        g.drawCenteredString(font, Component.literal("bei x " + x + ", z " + z + ". Ein Admin prueft den Antrag."), cx, cy - 36, 0xAAAAAA);
        g.drawCenteredString(font, Component.literal("Dort muessen Betten und eine Glocke stehen."), cx, cy - 26, 0x888888);
        super.render(g, mx, my, pt);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(zurueck);
    }
}
