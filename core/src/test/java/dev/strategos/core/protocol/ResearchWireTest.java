package dev.strategos.core.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.shared.TechId;
import java.util.List;
import org.junit.jupiter.api.Test;

/** TECH-01's wire: the Research command, the REQUIREMENTS reason, and a tech entry in {@code building_state}'s queue. */
class ResearchWireTest {
    private static BuildingState.Entry entry(List<Integer> queue, List<Integer> batches) {
        return new BuildingState.Entry(
                0x80001,
                6,
                0,
                10,
                12,
                1,
                4,
                2,
                0,
                255,
                true,
                queue,
                false,
                0,
                0,
                queue.isEmpty() ? 0 : 300,
                -1,
                0,
                false,
                batches);
    }

    private static byte[] bytes(BuildingState s) {
        return WireCodec.encode(BuildingState.WIRE, s);
    }

    @Test
    void researchRoundTripsOnTheWireWithId31() {
        Research r = new Research(0x80003, new TechId("strategos:gather_wood_1"));
        assertThat(CommandKinds.DEFAULT.of(r).wireId()).isEqualTo(31);
        assertThat(CommandKinds.DEFAULT.of(r).adminOnly()).isFalse();
        byte[] b = WireCodec.encode(Research.WIRE, r);
        assertThat(WireCodec.decode(Research.WIRE, b)).isEqualTo(r);
        assertThat(WireCodec.encode(CommandKinds.DEFAULT.wireCodec(), r))
                .hasSizeLessThanOrEqualTo(CommandKinds.DEFAULT.wireCodec().maxSize());
    }

    @Test
    void requirementsIsTheLastReasonAndCode18() {
        RejectReason[] all = RejectReason.values();
        assertThat(all[all.length - 1]).isEqualTo(RejectReason.REQUIREMENTS);
        for (RejectReason reason : all) {
            WireWriter w = new WireWriter();
            WireCodecs.writeReason(w, reason);
            assertThat(WireCodecs.readReason(new WireReader(w.toByteArray()))).isEqualTo(reason);
        }
        WireWriter w = new WireWriter();
        WireCodecs.writeReason(w, RejectReason.REQUIREMENTS);
        assertThat(w.toByteArray()).containsExactly(18);
    }

    @Test
    void aTechEntryRoundTripsInTheQueue() {
        // the store's encoding: a tech is -1 - its index, so tech 0 is -1 and tech 3 is -4
        BuildingState s = new BuildingState(1, 40, List.of(entry(List.of(-1, 6, -4), List.of())));
        assertThat(WireCodec.decode(BuildingState.WIRE, bytes(s))).isEqualTo(s);
        BuildingState batched = new BuildingState(1, 40, List.of(entry(List.of(6, -2, 6), List.of(3, 1, 1))));
        assertThat(WireCodec.decode(BuildingState.WIRE, bytes(batched))).isEqualTo(batched);
        assertThat(bytes(batched).length).isLessThanOrEqualTo(BuildingState.WIRE.maxSize());
    }

    @Test
    void aQueueWithoutTechsWritesTheOldBytes() {
        // the old layout, written by hand: ..., own = 1, queue size, entries, rally 0, trained, batch counts, field,
        // reseed
        BuildingState s = new BuildingState(1, 40, List.of(entry(List.of(6, 7), List.of())));
        WireWriter w = new WireWriter();
        w.writeVarInt(1).writeVarLong(40).writeVarInt(1);
        w.writeVarInt(0x80001).writeVarInt(6).writeVarInt(0).writeZigZag(10).writeZigZag(12);
        w.writeByte((1 << 4) | 2).writeZigZag(4).writeVarInt(0).writeByte(255).writeByte(1);
        w.writeVarInt(2).writeVarInt(6).writeVarInt(7).writeByte(0);
        w.writeVarInt(300).writeVarInt(1).writeVarInt(1);
        w.writeVarInt(0).writeByte(0);
        assertThat(bytes(s)).isEqualTo(w.toByteArray());
    }

    @Test
    void aTechInTheEighthPlaceRoundTrips() {
        BuildingState s = new BuildingState(1, 40, List.of(entry(List.of(6, 6, 6, 6, 6, 6, 6, -2), List.of())));
        assertThat(WireCodec.decode(BuildingState.WIRE, bytes(s))).isEqualTo(s);
        assertThat(bytes(s).length).isLessThanOrEqualTo(BuildingState.WIRE.maxSize());
    }

    @Test
    void nonCanonicalTechBytesAreRefused() {
        // a queue of [6, tech 0]: the size carries 0x10, the mask marks entry 1; a mask of 0 or one past the queue
        // would decode to a queue the writer writes otherwise
        assertThat(WireCodec.decode(BuildingState.WIRE, queueBytes(0b10)))
                .isEqualTo(new BuildingState(1, 40, List.of(entry(List.of(6, -1), List.of()))));
        for (int mask : new int[] {0, 0b100, 0b110}) {
            assertThatThrownBy(() -> WireCodec.decode(BuildingState.WIRE, queueBytes(mask)))
                    .as("mask " + mask)
                    .isInstanceOf(WireException.class);
        }
    }

    private static byte[] queueBytes(int mask) {
        WireWriter w = new WireWriter();
        w.writeVarInt(1).writeVarLong(40).writeVarInt(1);
        w.writeVarInt(0x80001).writeVarInt(6).writeVarInt(0).writeZigZag(10).writeZigZag(12);
        w.writeByte((1 << 4) | 2).writeZigZag(4).writeVarInt(0).writeByte(255).writeByte(1);
        w.writeVarInt(0x10 | 2).writeByte(mask).writeVarInt(6).writeVarInt(0).writeByte(0);
        w.writeVarInt(300).writeVarInt(1).writeVarInt(1);
        w.writeVarInt(0).writeByte(0);
        return w.toByteArray();
    }
}
