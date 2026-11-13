package de.niklasmetzger.crownscore.evidence;

import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.EvidenceEntry;
import de.niklasmetzger.crownscore.util.TimeUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Schreibt Eintraege in die Beweisakte. */
public final class EvidenceLog {
    private EvidenceLog() {}

    public static EvidenceEntry log(ServerPlayer actor, ServerLevel level, BlockPos pos, String typ, String gebietNationKey, String detail) {
        CrownsData d = CrownsData.get(actor.server);
        EvidenceEntry e = new EvidenceEntry();
        e.zeit = TimeUtil.now();
        e.typ = typ;
        e.taeter = actor.getUUID();
        e.taeterName = actor.getGameProfile().getName();
        String tn = d.nationKeyOf(actor.getUUID());
        e.taeterNation = tn == null ? "" : d.anzeige(tn);
        e.gebietNation = gebietNationKey == null ? "" : d.anzeige(gebietNationKey);
        if (level != null && pos != null) {
            e.dim = level.dimension().location().toString();
            e.x = pos.getX();
            e.y = pos.getY();
            e.z = pos.getZ();
        }
        e.detail = detail == null ? "" : detail;
        d.addEvidence(e);
        return e;
    }
}
