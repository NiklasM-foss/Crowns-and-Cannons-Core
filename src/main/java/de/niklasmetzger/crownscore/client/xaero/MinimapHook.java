package de.niklasmetzger.crownscore.client.xaero;

import de.niklasmetzger.crownscore.CrownsCore;
import de.niklasmetzger.crownscore.client.ClientMapFlags;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.common.minimap.highlight.AbstractHighlighter;
import xaero.common.minimap.highlight.HighlighterRegistry;
import xaero.common.minimap.write.MinimapWriter;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.info.render.compile.InfoDisplayCompiler;
import xaero.hud.minimap.module.MinimapSession;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Flaggen auf Xaero's Minimap. Nur laden, wenn Minimap und OPAC installiert sind. */
public final class MinimapHook {
    private static HighlighterRegistry installedFor;

    private MinimapHook() {}

    private static MinimapWriter writer() {
        MinimapSession s = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (s == null || s.getProcessor() == null) return null;
        return s.getProcessor().getMinimapWriter();
    }

    @SuppressWarnings("unchecked")
    public static void install() throws ReflectiveOperationException {
        MinimapWriter w = writer();
        if (w == null) return;
        Field rf = MinimapWriter.class.getDeclaredField("highlighterRegistry");
        rf.setAccessible(true);
        HighlighterRegistry reg = (HighlighterRegistry) rf.get(w);
        if (reg == null || reg == installedFor) return;
        List<AbstractHighlighter> list = reg.getHighlighters();
        if (list == null) return;
        for (AbstractHighlighter h : list) {
            if (h instanceof Flags) {
                installedFor = reg;
                return;
            }
        }
        List<AbstractHighlighter> neu = new ArrayList<>();
        AbstractHighlighter opac = null;
        for (AbstractHighlighter h : list) {
            if (opac == null && h instanceof xaero.common.mods.pac.highlight.ClaimsHighlighter) {
                opac = h;
                neu.add(new OhneFlaggen(h));
            } else {
                neu.add(h);
            }
        }
        neu.add(new Flags(opac));
        Field f = HighlighterRegistry.class.getDeclaredField("highlighters");
        f.setAccessible(true);
        f.set(reg, Collections.unmodifiableList(neu));
        installedFor = reg;
        CrownsCore.LOGGER.info("Flaggen auf der Minimap eingehaengt ({} Highlighter, OPAC {})", neu.size(), opac == null ? "fehlt" : "eingepackt");
        refresh();
    }

    private static final List<xaero.common.minimap.waypoints.Waypoint> UNSERE = new ArrayList<>();
    private static Object setFuer;
    private static int stand = Integer.MIN_VALUE;

    /** Ein voruebergehender Wegpunkt (wird nicht gespeichert) in jeder anerkannten Stadt, die der Spieler sehen darf. */
    public static void wegpunkte(List<de.niklasmetzger.crownscore.net.CcNetwork.StadtEintrag> staedte, int version) {
        MinimapSession s = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (s == null || s.getWorldManager() == null) return;
        var welt = s.getWorldManager().getCurrentWorld();
        if (welt == null) return;
        xaero.hud.minimap.waypoint.set.WaypointSet set = welt.getCurrentWaypointSet();
        if (set == null || (set == setFuer && version == stand)) return;
        if (setFuer instanceof xaero.hud.minimap.waypoint.set.WaypointSet alt) {
            for (xaero.common.minimap.waypoints.Waypoint wp : UNSERE) alt.remove(wp);
        }
        UNSERE.clear();
        for (de.niklasmetzger.crownscore.net.CcNetwork.StadtEintrag e : staedte) {
            if (!e.anerkannt()) continue;
            String kurz = e.name().replaceAll("[^A-Za-z0-9]", "");
            kurz = kurz.isEmpty() ? "S" : kurz.substring(0, Math.min(2, kurz.length())).toUpperCase(java.util.Locale.ROOT);
            xaero.common.minimap.waypoints.Waypoint wp = new xaero.common.minimap.waypoints.Waypoint(e.x(), 80, e.z(), e.name(), kurz, 6);
            wp.setTemporary(true);
            wp.setYIncluded(false);
            set.add(wp);
            UNSERE.add(wp);
        }
        setFuer = set;
        stand = version;
    }

    public static void refresh() {
        MinimapWriter w = writer();
        if (w != null && w.getDimensionHighlightHandler() != null) w.getDimensionHighlightHandler().requestRefresh();
    }

    static final class OhneFlaggen extends AbstractHighlighter {
        private final AbstractHighlighter orig;

        OhneFlaggen(AbstractHighlighter orig) {
            super(orig.isCoveringOutsideDiscovered());
            this.orig = orig;
        }

        @Override
        public boolean regionHasHighlights(ResourceKey<Level> dim, int rx, int rz) {
            return orig.regionHasHighlights(dim, rx, rz);
        }

        @Override
        public boolean chunkIsHighlit(ResourceKey<Level> dim, int cx, int cz) {
            return !ClientMapFlags.hat(Claims.owner(dim, cx, cz)) && orig.chunkIsHighlit(dim, cx, cz);
        }

        @Override
        public int[] getChunkHighlitColor(ResourceKey<Level> dim, int cx, int cz) {
            return ClientMapFlags.hat(Claims.owner(dim, cx, cz)) ? null : orig.getChunkHighlitColor(dim, cx, cz);
        }

        @Override
        public void addBlockHighlightTooltips(InfoDisplayCompiler c, ResourceKey<Level> dim, int x, int z, int w) {
            if (!ClientMapFlags.hat(Claims.owner(dim, x >> 4, z >> 4))) orig.addBlockHighlightTooltips(c, dim, x, z, w);
        }
    }

    static final class Flags extends AbstractHighlighter {
        private final AbstractHighlighter opac;

        Flags(AbstractHighlighter opac) {
            super(true);
            this.opac = opac;
        }

        @Override
        public boolean regionHasHighlights(ResourceKey<Level> dim, int rx, int rz) {
            return dim == Level.OVERWORLD && ClientMapFlags.etwasZuMalen();
        }

        @Override
        public boolean chunkIsHighlit(ResourceKey<Level> dim, int cx, int cz) {
            return dim == Level.OVERWORLD && (ClientMapFlags.hat(Claims.owner(dim, cx, cz)) || ClientMapFlags.stadtImChunk(cx, cz));
        }

        @Override
        public int[] getChunkHighlitColor(ResourceKey<Level> dim, int cx, int cz) {
            if (dim != Level.OVERWORLD) return null;
            UUID o = Claims.owner(dim, cx, cz);
            boolean flagge = false;
            if (ClientMapFlags.hat(o)) {
                boolean n = !Objects.equals(o, Claims.owner(dim, cx, cz - 1));
                boolean e = !Objects.equals(o, Claims.owner(dim, cx + 1, cz));
                boolean s = !Objects.equals(o, Claims.owner(dim, cx, cz + 1));
                boolean w = !Objects.equals(o, Claims.owner(dim, cx - 1, cz));
                flagge = ClientMapFlags.fill(o, cx, cz, n, e, s, w, resultStore);
            }
            if (!flagge) java.util.Arrays.fill(resultStore, 0);
            boolean stadt = ClientMapFlags.staedteMalen(cx, cz, resultStore);
            return flagge || stadt ? resultStore : null;
        }

        @Override
        public void addBlockHighlightTooltips(InfoDisplayCompiler c, ResourceKey<Level> dim, int x, int z, int w) {
            // Der Name der Nation kommt vom eingepackten OPAC-Highlighter nur, wenn er den Chunk selbst markiert.
            if (opac != null && ClientMapFlags.hat(Claims.owner(dim, x >> 4, z >> 4))) opac.addBlockHighlightTooltips(c, dim, x, z, w);
        }
    }
}
