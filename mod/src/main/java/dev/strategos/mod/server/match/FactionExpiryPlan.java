package dev.strategos.mod.server.match;

import dev.strategos.core.shared.FactionSlot;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/** Primary ownership only: control grants and observers never extend another faction's human reservation. */
public final class FactionExpiryPlan {
    private FactionExpiryPlan() {}

    public record Member(@Nullable FactionSlot primary, boolean connected, long remainingNanos) {
        public Member {
            if (remainingNanos < 0) throw new IllegalArgumentException("negative remaining reservation duration");
        }
    }

    public static List<FactionSlot> due(List<Member> members) {
        var candidates = new TreeSet<Integer>();
        var retained = new HashSet<Integer>();
        for (Member member : members) {
            FactionSlot primary = member.primary();
            if (primary == null) continue;
            int faction = primary.value();
            if (member.connected() || member.remainingNanos() > 0) retained.add(faction);
            else candidates.add(faction);
        }
        candidates.removeAll(retained);
        return candidates.stream().map(FactionSlot::new).toList();
    }
}
