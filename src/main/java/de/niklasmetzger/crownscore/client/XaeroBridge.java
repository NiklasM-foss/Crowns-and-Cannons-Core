package de.niklasmetzger.crownscore.client;

import de.niklasmetzger.crownscore.CrownsCore;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.ModList;

/**
 * Verbindung zu Xaero's World Map und Minimap, ohne deren Code zu veraendern oder mitzuliefern. Beide Karten haben
 * eine Liste von "Highlightern", die Chunks einfaerben (so zeichnet auch OPAC seine Claims). Hier kommt ein eigener
 * dazu, der die Flagge malt, und der OPAC-Highlighter wird so eingepackt, dass er Staatsgebiete mit Flagge auslaesst.
 * Die Klassen mit Xaero-Bezug (client.xaero) werden nur geladen, wenn die Mods da sind. Passt eine Xaero-Version
 * nicht, schaltet sich das ab und die Karten zeigen wie bisher die einfarbigen Claims.
 */
public final class XaeroBridge {
    private static boolean wmAus, mmAus;
    private static Boolean wm, mm, opac;
    private static int tick;
    private static volatile boolean dirty;

    private XaeroBridge() {}

    private static boolean wm() {
        if (wm == null) wm = ModList.get().isLoaded("xaeroworldmap");
        return wm && opac() && !wmAus;
    }

    private static boolean mm() {
        if (mm == null) mm = ModList.get().isLoaded("xaerominimap") || ModList.get().isLoaded("xaerobetterpvp");
        return mm && opac() && !mmAus;
    }

    private static boolean opac() {
        if (opac == null) opac = ModList.get().isLoaded("openpartiesandclaims");
        return opac;
    }

    /** Alle 2 Sekunden aus dem Client-Tick: nach jedem Weltwechsel bauen die Karten ihre Listen neu, dann wieder einhaengen. */
    public static void tick() {
        tick++;
        if (dirty && tick % 10 == 0) {
            dirty = false;
            redraw();
        }
        if (tick % 40 != 0 || Minecraft.getInstance().player == null) return;
        if (mm()) {
            try {
                de.niklasmetzger.crownscore.client.xaero.MinimapHook.wegpunkte(ClientMapFlags.staedteListe(), ClientMapFlags.staedteStand());
            } catch (Throwable t) {
                CrownsCore.LOGGER.debug("Wegpunkte der Staedte nicht gesetzt: {}", t.toString());
            }
        }
        if (wm()) {
            try {
                de.niklasmetzger.crownscore.client.xaero.WorldMapHook.install();
            } catch (Throwable t) {
                wmAus = true;
                CrownsCore.LOGGER.warn("Flaggen auf der World Map abgeschaltet, Xaero-Version passt nicht: {}", t.toString());
            }
        }
        if (mm()) {
            try {
                de.niklasmetzger.crownscore.client.xaero.MinimapHook.install();
            } catch (Throwable t) {
                mmAus = true;
                CrownsCore.LOGGER.warn("Flaggen auf der Minimap abgeschaltet, Xaero-Version passt nicht: {}", t.toString());
            }
        }
    }

    /** Karten neu zeichnen lassen, nachdem sich Flaggen geaendert haben (gesammelt, hoechstens zweimal pro Sekunde). */
    public static void refresh() {
        dirty = true;
    }

    private static void redraw() {
        if (wm()) {
            try {
                de.niklasmetzger.crownscore.client.xaero.WorldMapHook.refresh();
            } catch (Throwable t) {
                CrownsCore.LOGGER.debug("World Map nicht aktualisiert: {}", t.toString());
            }
        }
        if (mm()) {
            try {
                de.niklasmetzger.crownscore.client.xaero.MinimapHook.refresh();
            } catch (Throwable t) {
                CrownsCore.LOGGER.debug("Minimap nicht aktualisiert: {}", t.toString());
            }
        }
    }
}
