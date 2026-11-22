package de.niklasmetzger.crownscore.client.xaero;

import de.niklasmetzger.crownscore.CrownsCore;
import de.niklasmetzger.crownscore.client.ClientMapFlags;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.map.MapProcessor;
import xaero.map.WorldMapSession;
import xaero.map.highlight.AbstractHighlighter;
import xaero.map.highlight.HighlighterRegistry;
import xaero.map.world.MapDimension;
import xaero.map.world.MapWorld;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Flaggen auf Xaero's World Map. Nur laden, wenn World Map und OPAC installiert sind. */
public final class WorldMapHook {
    private static HighlighterRegistry installedFor;

    private WorldMapHook() {}

    @SuppressWarnings("unchecked")
    public static void install() throws ReflectiveOperationException {
        WorldMapSession s = WorldMapSession.getCurrentSession();
        if (s == null) return;
        MapProcessor mp = s.getMapProcessor();
        if (mp == null) return;
        HighlighterRegistry reg = mp.getHighlighterRegistry();
        if (reg == null || reg == installedFor) return;
        List<AbstractHighlighter> list = reg.getHighlighters();
        if (list == null) return; // Liste noch nicht fertig
        for (AbstractHighlighter h : list) {
            if (h instanceof Flags) {
                installedFor = reg;
                return;
            }
        }
        List<AbstractHighlighter> neu = new ArrayList<>();
        AbstractHighlighter opac = null;
        for (AbstractHighlighter h : list) {
            if (opac == null && h instanceof xaero.map.mods.pac.highlight.ClaimsHighlighter) {
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
        CrownsCore.LOGGER.info("Flaggen auf der World Map eingehaengt ({} Highlighter, OPAC {})", neu.size(), opac == null ? "fehlt" : "eingepackt");
        refresh();
    }

    public static void refresh() {
        WorldMapSession s = WorldMapSession.getCurrentSession();
        if (s == null || s.getMapProcessor() == null) return;
        MapWorld w = s.getMapProcessor().getMapWorld();
        if (w == null) return;
        MapDimension d = w.getCurrentDimension();
        if (d != null && d.getHighlightHandler() != null) d.getHighlightHandler().clearCachedHashes();
    }

    /** Liegt der Block in einem Chunk, fuer den wir die Flagge malen? */
    static boolean ours(ResourceKey<Level> dim, int x, int z) {
        return ClientMapFlags.hat(Claims.owner(dim, x >> 4, z >> 4));
    }

    /** Laesst die Staatsgebiete aus, fuer die eine Flagge gemalt wird. Sonst wie der OPAC-Highlighter. */
    static final class OhneFlaggen extends AbstractHighlighter {
        private final AbstractHighlighter orig;

        OhneFlaggen(AbstractHighlighter orig) {
            super(orig.isCoveringOutsideDiscovered());
            this.orig = orig;
        }

        @Override
        public int calculateRegionHash(ResourceKey<Level> dim, int rx, int rz) {
            return orig.calculateRegionHash(dim, rx, rz) * 31 + ClientMapFlags.settingsHash();
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
        public Component getBlockHighlightSubtleTooltip(ResourceKey<Level> dim, int x, int z) {
            return ours(dim, x, z) ? null : orig.getBlockHighlightSubtleTooltip(dim, x, z);
        }

        @Override
        public Component getBlockHighlightBluntTooltip(ResourceKey<Level> dim, int x, int z) {
            return ours(dim, x, z) ? null : orig.getBlockHighlightBluntTooltip(dim, x, z);
        }

        @Override
        public void addMinimapBlockHighlightTooltips(List<Component> list, ResourceKey<Level> dim, int x, int z, int w) {
            if (!ours(dim, x, z)) orig.addMinimapBlockHighlightTooltips(list, dim, x, z, w);
        }
    }

    /** Malt die Flagge der Nation ueber ihre Chunks. Tooltips (Name der Nation) kommen weiter von OPAC. */
    static final class Flags extends AbstractHighlighter {
        private final AbstractHighlighter opac;

        Flags(AbstractHighlighter opac) {
            super(true);
            this.opac = opac;
        }

        @Override
        public int calculateRegionHash(ResourceKey<Level> dim, int rx, int rz) {
            if (dim != Level.OVERWORLD || !ClientMapFlags.etwasZuMalen()) return 0;
            int h = ClientMapFlags.settingsHash();
            // Eine Region sind 32 x 32 Chunks; der Rand gehoert dazu, weil dort die Grenzlinien haengen
            for (int z = -1; z <= 32; z++) {
                for (int x = -1; x <= 32; x++) {
                    UUID o = Claims.owner(dim, (rx << 5) + x, (rz << 5) + z);
                    h = h * 31 + (o == null ? 0 : o.hashCode() * 7 + ClientMapFlags.hashOf(o));
                }
            }
            return h;
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
        public Component getBlockHighlightSubtleTooltip(ResourceKey<Level> dim, int x, int z) {
            de.niklasmetzger.crownscore.net.CcNetwork.StadtEintrag st = dim == Level.OVERWORLD ? ClientMapFlags.stadtAn(x, z) : null;
            Component nation = opac == null || !ours(dim, x, z) ? null : opac.getBlockHighlightSubtleTooltip(dim, x, z);
            if (st == null) return nation;
            Component c = Component.literal((st.anerkannt() ? "§6Stadt " : "§7Stadtantrag ") + st.name() + " §7(" + st.nation() + ")");
            return nation == null ? c : c.copy().append(Component.literal("\n")).append(nation);
        }

        @Override
        public Component getBlockHighlightBluntTooltip(ResourceKey<Level> dim, int x, int z) {
            return opac == null || !ours(dim, x, z) ? null : opac.getBlockHighlightBluntTooltip(dim, x, z);
        }

        @Override
        public void addMinimapBlockHighlightTooltips(List<Component> list, ResourceKey<Level> dim, int x, int z, int w) {
            if (opac != null && ours(dim, x, z)) opac.addMinimapBlockHighlightTooltips(list, dim, x, z, w);
        }
    }
}
