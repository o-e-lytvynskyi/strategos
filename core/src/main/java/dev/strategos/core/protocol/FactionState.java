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
        List<Integer> workers,
        List<Integer> researched,
        int phase) {
    /** The most resources a match has (EconomyRules.MAX). */
    public static final int MAX_RESOURCES = 16;

    /** The most techs a faction can have researched on the wire (UI-13). */
    public static final int MAX_RESEARCHED = 256;

    /** A state without research: no tech researched, phase 0 (Village). */
    public FactionState(
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
        this(
                matchId,
                faction,
                tick,
                stock,
                gathered,
                spent,
                populationUsed,
                populationCap,
                idleWorkers,
                workers,
                List.of(),
                0);
    }

    public FactionState {
        stock = List.copyOf(stock);
        gathered = List.copyOf(gathered);
        spent = List.copyOf(spent);
        workers = List.copyOf(workers);
        researched = List.copyOf(researched);
        if (researched.size() > MAX_RESEARCHED || phase < 0 || phase > 255) {
            throw new IllegalArgumentException(
                    "a faction state of " + researched.size() + " researched techs, phase " + phase);
        }
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
                && workers.equals(other.workers)
                && researched.equals(other.researched)
                && phase == other.phase;
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
            out.writeVarInt(s.researched().size());
            for (int tech : s.researched()) {
                out.writeVarInt(tech);
            }
            out.writeByte(s.phase());
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
            int used = in.readVarInt();
            int cap = in.readVarInt();
            int idle = in.readVarInt();
            int t = in.readVarInt(MAX_RESEARCHED);
            List<Integer> researched = new ArrayList<>(t);
            for (int k = 0; k < t; k++) {
                researched.add(in.readVarInt());
            }
            int phase = in.readUnsignedByte();
            return new FactionState(
                    matchId, faction, tick, stock, gathered, spent, used, cap, idle, workers, researched, phase);
        }

        @Override
        public int maxSize() {
            return 5 + 5 + 10 + 1 + MAX_RESOURCES * (10 + 10 + 10 + 5) + 5 + 5 + 5 + 2 + MAX_RESEARCHED * 5 + 1;
        }
    };
}
