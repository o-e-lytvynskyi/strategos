package dev.strategos.core.protocol;

import dev.strategos.core.shared.CoreApi;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code faction_state} payload (ECO-01, UI-07), to its own faction only: per resource of match {@code matchId}, in
 * the match's order and in thousandths, the stock and what was gathered and spent so far; population used and its cap;
 * the idle workers and the workers on each resource. Sent when it changes, at most four times a second. Fields that
 * later cards fill (population from ECO-06, workers from ECO-05) are 0 until then.
 */
@CoreApi
public record FactionState(
        int matchId,
        int faction,
        long tick,
        List<Long> stock,
        List<Long> gathered,
        List<Long> spent,
        int populationUsed,
        int populationCap,
        int idleWorkers,
        List<Integer> workers) {
    /** The most resources a match has (EconomyRules.MAX). */
    public static final int MAX_RESOURCES = 16;

    public FactionState {
        stock = List.copyOf(stock);
        gathered = List.copyOf(gathered);
        spent = List.copyOf(spent);
        workers = List.copyOf(workers);
        if (stock.size() > MAX_RESOURCES
                || gathered.size() != stock.size()
                || spent.size() != stock.size()
                || workers.size() != stock.size()) {
            throw new IllegalArgumentException("a faction state of " + stock.size() + " stocks, " + gathered.size()
                    + " gathered, " + spent.size() + " spent and " + workers.size() + " worker counts");
        }
    }

    /** The same state but for its tick, which changes every time. */
    public boolean sameAs(FactionState other) {
        return matchId == other.matchId
                && faction == other.faction
                && stock.equals(other.stock)
                && gathered.equals(other.gathered)
                && spent.equals(other.spent)
                && populationUsed == other.populationUsed
                && populationCap == other.populationCap
                && idleWorkers == other.idleWorkers
                && workers.equals(other.workers);
    }

    public static final WireCodec<FactionState> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, FactionState s) {
            out.writeVarInt(s.matchId()).writeVarInt(s.faction()).writeVarLong(s.tick());
            out.writeVarInt(s.stock().size());
            for (int r = 0; r < s.stock().size(); r++) {
                out.writeVarLong(s.stock().get(r))
                        .writeVarLong(s.gathered().get(r))
                        .writeVarLong(s.spent().get(r))
                        .writeVarInt(s.workers().get(r));
            }
            out.writeVarInt(s.populationUsed()).writeVarInt(s.populationCap()).writeVarInt(s.idleWorkers());
        }

        @Override
        public FactionState read(WireReader in) {
            int matchId = in.readVarInt();
            int faction = in.readVarInt();
            long tick = in.readVarLong();
            int n = in.readVarInt(MAX_RESOURCES);
            List<Long> stock = new ArrayList<>(n);
            List<Long> gathered = new ArrayList<>(n);
            List<Long> spent = new ArrayList<>(n);
            List<Integer> workers = new ArrayList<>(n);
            for (int r = 0; r < n; r++) {
                stock.add(in.readVarLong());
                gathered.add(in.readVarLong());
                spent.add(in.readVarLong());
                workers.add(in.readVarInt());
            }
            return new FactionState(
                    matchId,
                    faction,
                    tick,
                    stock,
                    gathered,
                    spent,
                    in.readVarInt(),
                    in.readVarInt(),
                    in.readVarInt(),
                    workers);
        }

        @Override
        public int maxSize() {
            return 5 + 5 + 10 + 1 + MAX_RESOURCES * (10 + 10 + 10 + 5) + 5 + 5 + 5;
        }
    };
}
