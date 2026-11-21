package dev.strategos.core.protocol;

import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Winners;
import java.util.List;

/** Existing individual/draw bytes stay fixed; -2 introduces a bounded multi-faction mask. */
final class WinnerWire {
    private WinnerWire() {}

    static void write(WireWriter out, List<Integer> winners) {
        int mask = Winners.mask(winners);
        if (Integer.bitCount(mask) > 1) {
            out.writeZigZag(-2).writeVarInt(mask);
        } else {
            out.writeZigZag(Winners.first(mask));
        }
    }

    static List<Integer> read(WireReader in) {
        int code = in.readZigZagInt();
        if (code == -2) {
            int mask = in.readVarInt(Winners.MAX_MASK);
            if (Integer.bitCount(mask) < 2) throw new WireException("coalition requires multiple winners");
            return Winners.fromMask(mask);
        }
        if (code < -1 || code > FactionSlot.MAX) throw new WireException("invalid winner faction " + code);
        return Winners.legacy(code);
    }
}
