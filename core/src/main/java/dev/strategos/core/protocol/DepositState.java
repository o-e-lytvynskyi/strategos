package dev.strategos.core.protocol;

import dev.strategos.core.shared.CoreApi;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code deposit_state} payload (ECO-08): the mines and quarries of match {@code matchId}, each with its node,
 * template, footprint corner, resource index and the amount it has left of its most, in whole units (what is left
 * rounded up, so 0 is empty). Nobody owns a deposit, so every player gets the same list. Sent when it changes.
 */
@CoreApi
public record DepositState(int matchId, long tick, List<Entry> deposits) {
    public static final int MAX_DEPOSITS = 64;

    /** One deposit. */
    @CoreApi
    public record Entry(int node, int template, int x, int z, int resource, int left, int most) {
        public Entry {
            if (left < 0 || most < left || resource < 0) {
                throw new IllegalArgumentException("a deposit entry out of range: " + node);
            }
        }
    }

    public DepositState {
        deposits = List.copyOf(deposits);
        if (deposits.size() > MAX_DEPOSITS) {
            throw new IllegalArgumentException(deposits.size() + " deposits, more than " + MAX_DEPOSITS);
        }
    }

    /** The same state but for its tick. */
    public boolean sameAs(DepositState other) {
        return matchId == other.matchId && deposits.equals(other.deposits);
    }

    public static final WireCodec<DepositState> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, DepositState s) {
            out.writeVarInt(s.matchId())
                    .writeVarLong(s.tick())
                    .writeVarInt(s.deposits().size());
            for (Entry e : s.deposits()) {
                out.writeVarInt(e.node())
                        .writeVarInt(e.template())
                        .writeZigZag(e.x())
                        .writeZigZag(e.z())
                        .writeVarInt(e.resource())
                        .writeVarInt(e.left())
                        .writeVarInt(e.most());
            }
        }

        @Override
        public DepositState read(WireReader in) {
            int matchId = in.readVarInt();
            long tick = in.readVarLong();
            int n = in.readVarInt(MAX_DEPOSITS);
            List<Entry> out = new ArrayList<>(n);
            for (int k = 0; k < n; k++) {
                out.add(new Entry(
                        in.readVarInt(),
                        in.readVarInt(),
                        in.readZigZagInt(),
                        in.readZigZagInt(),
                        in.readVarInt(),
                        in.readVarInt(),
                        in.readVarInt()));
            }
            return new DepositState(matchId, tick, out);
        }

        @Override
        public int maxSize() {
            return 5 + 10 + 1 + MAX_DEPOSITS * 7 * 5;
        }
    };
}
