package dev.strategos.core.protocol;

import dev.strategos.core.shared.CoreApi;
import java.util.List;

/**
 * The match results appended to {@code match_state} at {@link MatchStateMsg.Lifecycle#ENDED} (M6 §2.3): the winner,
 * the tick the match ended at, and each faction's statistics.
 */
@CoreApi
public record MatchResults(int winner, long endedAt, List<FactionStats> factions) {
    @CoreApi
    public record FactionStats(
            int unitsTrained,
            int unitsLost,
            int buildingsBuilt,
            int buildingsLost,
            List<Long> resourcesGathered,
            List<Long> phaseTicks) {
        public FactionStats {
            resourcesGathered = List.copyOf(resourcesGathered);
            phaseTicks = List.copyOf(phaseTicks);
        }
    }

    public MatchResults {
        factions = List.copyOf(factions);
    }

    public static final WireCodec<MatchResults> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, MatchResults r) {
            out.writeZigZag(r.winner());
            out.writeVarLong(r.endedAt());
            out.writeVarInt(r.factions().size());
            for (FactionStats f : r.factions()) {
                out.writeVarInt(f.unitsTrained());
                out.writeVarInt(f.unitsLost());
                out.writeVarInt(f.buildingsBuilt());
                out.writeVarInt(f.buildingsLost());
                out.writeVarInt(f.resourcesGathered().size());
                for (long g : f.resourcesGathered()) {
                    out.writeVarLong(g);
                }
                out.writeVarInt(f.phaseTicks().size());
                for (long t : f.phaseTicks()) {
                    out.writeZigZag(t);
                }
            }
        }

        @Override
        public MatchResults read(WireReader in) {
            int winner = in.readZigZagInt();
            long endedAt = in.readVarLong();
            int count = in.readVarInt();
            var factions = new java.util.ArrayList<FactionStats>(count);
            for (int i = 0; i < count; i++) {
                int unitsTrained = in.readVarInt();
                int unitsLost = in.readVarInt();
                int buildingsBuilt = in.readVarInt();
                int buildingsLost = in.readVarInt();
                int rCount = in.readVarInt();
                var gathered = new java.util.ArrayList<Long>(rCount);
                for (int r = 0; r < rCount; r++) {
                    gathered.add(in.readVarLong());
                }
                int pCount = in.readVarInt();
                var phases = new java.util.ArrayList<Long>(pCount);
                for (int p = 0; p < pCount; p++) {
                    phases.add(in.readZigZagLong());
                }
                factions.add(
                        new FactionStats(unitsTrained, unitsLost, buildingsBuilt, buildingsLost, gathered, phases));
            }
            return new MatchResults(winner, endedAt, factions);
        }

        @Override
        public int maxSize() {
            return 1008;
        }
    };
}
