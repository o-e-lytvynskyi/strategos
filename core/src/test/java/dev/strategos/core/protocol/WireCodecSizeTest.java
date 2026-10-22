package dev.strategos.core.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandEnvelope;
import java.util.Arrays;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/**
 * NET-01.2 for the commands and the payload records, CORE-11 for the journal's envelopes: exact sizes, round trips,
 * bounds and each payload within its cap (spec §2.7).
 */
class WireCodecSizeTest {
    private static final WireCodec<Command> COMMANDS = CommandKinds.DEFAULT.wireCodec();

    @Test
    void encodesToTheExactSizesOfTheFormat() {
        Move one = Move.replace(UnitSet.of(5), new Vec2d(1, 2));
        assertThat(size(Move.WIRE, one))
                .as("count, handle, two doubles, mode, formation")
                .isEqualTo(20);

        int[] run = new int[UnitSet.MAX_SIZE];
        Arrays.setAll(run, i -> i + 1);
        assertThat(size(Move.WIRE, Move.replace(UnitSet.of(run), new Vec2d(1, 2))))
                .as("300 consecutive handles: a 2-byte count, then one byte each")
                .isEqualTo(320);
        assertThat(size(Stop.WIRE, new Stop(UnitSet.of(5)))).isEqualTo(2);
        assertThat(size(
                        SpawnUnits.WIRE,
                        new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(0), new Vec2d(16, 16), 2, 4.0)))
                .as("a 27-byte id with its length, faction, centre, count, spread")
                .isEqualTo(54);
        assertThat(size(CommandEnvelope.WIRE, new CommandEnvelope(PlayerSlot.ADMIN, 1, 0, one)))
                .as("issuer, sequence, tick, two-byte wire id and the move")
                .isEqualTo(25);
    }

    @Test
    void theLargestCommandsStayWithinTheirBound() {
        int[] spread = new int[UnitSet.MAX_SIZE];
        Arrays.setAll(spread, i -> Integer.MIN_VALUE + i * 14_000_000);
        Move widest = new Move(UnitSet.of(spread), new Vec2d(-1e300, 1e300), QueueMode.PUSH_FRONT, Integer.MIN_VALUE);
        assertThat(size(Move.WIRE, widest)).isLessThanOrEqualTo(Move.WIRE.maxSize());
        assertThat(roundTrip(COMMANDS, widest)).isEqualTo(widest);
        assertThat(COMMANDS.maxSize()).isEqualTo(2 + Move.WIRE.maxSize());
        assertThat(CommandEnvelope.WIRE.maxSize()).isLessThan(Limits.BATCH_BYTES);
    }

    @Test
    void randomCommandsRoundTripWithinTheirBounds() {
        SplittableRandom rng = new SplittableRandom(7);
        for (int i = 0; i < 1_000; i++) {
            int[] handles = new int[rng.nextInt(UnitSet.MAX_SIZE + 1)];
            for (int h = 0; h < handles.length; h++) {
                handles[h] = (int) rng.nextLong();
            }
            UnitSet units = UnitSet.of(handles);
            Command command = switch (i % 3) {
                case 0 ->
                    new Move(
                            units,
                            new Vec2d(rng.nextDouble() * 512, rng.nextDouble() * 512),
                            QueueMode.values()[rng.nextInt(3)],
                            rng.nextInt(16));
                case 1 -> new Stop(units);
                default ->
                    new SpawnUnits(
                            TemplateSet.DEBUG_WALKER,
                            new FactionSlot(rng.nextInt(FactionSlot.MAX + 1)),
                            new Vec2d(rng.nextDouble() * 512, rng.nextDouble() * 512),
                            rng.nextInt(300),
                            rng.nextDouble() * 8);
            };
            CommandEnvelope envelope = new CommandEnvelope(new PlayerSlot(rng.nextInt(17) - 1), i, i * 3L, command);
            byte[] bytes = WireCodec.encode(CommandEnvelope.WIRE, envelope);
            assertThat(bytes.length).isLessThanOrEqualTo(CommandEnvelope.WIRE.maxSize());
            assertThat(WireCodec.decode(CommandEnvelope.WIRE, bytes)).isEqualTo(envelope);
        }
    }

    @Test
    void theCatalogueNumbersItsKindsOnce() {
        assertThat(CommandKinds.DEFAULT.fingerprint())
                .isEqualTo("strategos:move=1,strategos:stop=2,strategos:spawn_units=3");
        assertThat(CommandKinds.DEFAULT.byWireId(2)).isSameAs(CommandKinds.STOP);
        assertThatThrownBy(() -> new CommandKinds(java.util.List.of(
                        CommandKinds.MOVE,
                        new CommandKind<>("strategos:other", 1, Stop.class, Stop.CODEC, Stop.WIRE, false))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("wire id 1 listed twice");
    }

    @Test
    void refusesUnknownIdsTruncationsAndNonFinitePositions() {
        assertThatThrownBy(() -> WireCodec.decode(COMMANDS, new byte[] {0, 9}))
                .isInstanceOf(WireException.class)
                .hasMessageContaining("unknown command wire id 9");
        byte[] move = WireCodec.encode(COMMANDS, Move.replace(UnitSet.of(5), new Vec2d(1, 2)));
        assertThatThrownBy(() -> WireCodec.decode(COMMANDS, Arrays.copyOf(move, move.length - 1)))
                .isInstanceOf(WireException.class);
        byte[] nan = WireCodec.encode(COMMANDS, Move.replace(UnitSet.of(5), new Vec2d(Double.NaN, 2)));
        assertThatThrownBy(() -> WireCodec.decode(COMMANDS, nan))
                .isInstanceOf(WireException.class)
                .hasMessageContaining("non-finite");
        byte[] repeated = new WireWriter()
                .writeShort(2)
                .writeVarInt(2)
                .writeZigZag(5)
                .writeVarLong(0)
                .toByteArray();
        assertThatThrownBy(() -> WireCodec.decode(COMMANDS, repeated))
                .isInstanceOf(WireException.class)
                .hasMessageContaining("out of order or repeated");
    }

    @Test
    void thePayloadRecordsEncodeToTheExactSizesOfTheFormat() {
        Move one = Move.replace(UnitSet.of(5), new Vec2d(1, 2));
        assertThat(size(CommandBatch.WIRE, new CommandBatch(7, java.util.List.of(one))))
                .as("sequence, count, wire id and the move")
                .isEqualTo(1 + 1 + 2 + 20);
        assertThat(size(MatchStateMsg.WIRE, new MatchStateMsg(3, MatchStateMsg.Lifecycle.RUNNING, 1_200)))
                .as("id, lifecycle, the tick as a zigzag varint")
                .isEqualTo(1 + 1 + 2);
        assertThat(size(FeedbackMsg.WIRE, new Feedback(70_000, CommandOutcome.PARTIAL, RejectReason.NOT_OWNER, 3, 5)))
                .as("kind, sequence, outcome, reason, accepted, total")
                .isEqualTo(1 + 3 + 1 + 1 + 1 + 1);
        assertThat(size(FeedbackMsg.WIRE, new NoticeMsg(NoticeKind.STUCK, 0x1234_5678, "boxed in")))
                .as("kind, notice kind, handle, text with its length")
                .isEqualTo(1 + 1 + 4 + 1 + 8);
        assertThat(size(MatchJoin.WIRE, new MatchJoin(1, 100, -60, -200, 48, 48, -64, 319, 2, new PlayerSlot(1), "{}")))
                .as("id, origin, size, band, factions, slot, rules")
                .isEqualTo(1 + 2 + 1 + 2 + 1 + 1 + 1 + 2 + 1 + 1 + 3);
    }

    @Test
    void thePayloadRecordsStayWithinTheirCaps() {
        assertThat(CommandBatch.WIRE.maxSize()).as("commands").isEqualTo(4 * 1024);
        assertThat(MatchJoin.WIRE.maxSize()).as("match_join").isLessThanOrEqualTo(4 * 1024);
        assertThat(MatchStateMsg.WIRE.maxSize()).as("match_state").isLessThanOrEqualTo(256);
        assertThat(FeedbackMsg.WIRE.maxSize()).as("feedback").isLessThanOrEqualTo(1024);

        MatchJoin widest = new MatchJoin(
                Integer.MAX_VALUE,
                Integer.MIN_VALUE,
                Integer.MIN_VALUE,
                Integer.MIN_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MIN_VALUE,
                Integer.MIN_VALUE,
                Integer.MAX_VALUE,
                PlayerSlot.ADMIN,
                "r".repeat(MatchJoin.RULES_BYTES));
        assertThat(size(MatchJoin.WIRE, widest)).isEqualTo(MatchJoin.WIRE.maxSize());
        assertThat(roundTrip(MatchJoin.WIRE, widest)).isEqualTo(widest);
        NoticeMsg longest = new NoticeMsg(NoticeKind.ORDER_FAILED, -1, "d".repeat(NoticeMsg.DETAIL_BYTES));
        assertThat(size(FeedbackMsg.WIRE, longest)).isEqualTo(FeedbackMsg.WIRE.maxSize());
        assertThat(roundTrip(FeedbackMsg.WIRE, longest)).isEqualTo(longest);
        Feedback all =
                new Feedback(Integer.MAX_VALUE, CommandOutcome.REJECTED, null, Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertThat(roundTrip(FeedbackMsg.WIRE, all)).isEqualTo(all);
        MatchStateMsg late = new MatchStateMsg(Integer.MAX_VALUE, MatchStateMsg.Lifecycle.DISPOSED, Long.MAX_VALUE);
        assertThat(size(MatchStateMsg.WIRE, late)).isEqualTo(MatchStateMsg.WIRE.maxSize());
        assertThat(roundTrip(MatchStateMsg.WIRE, late)).isEqualTo(late);
    }

    @Test
    void aBatchHoldsOneToThirtyTwoCommandsInFourKilobytes() {
        Move one = Move.replace(UnitSet.of(5), new Vec2d(1, 2));
        java.util.List<Command> full = java.util.Collections.nCopies(Limits.BATCH_COMMANDS, one);
        assertThat(roundTrip(CommandBatch.WIRE, new CommandBatch(0, full)).commands())
                .hasSize(32);
        assertThatThrownBy(() -> new CommandBatch(0, java.util.List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CommandBatch(-1, java.util.List.of(one)))
                .isInstanceOf(IllegalArgumentException.class);
        byte[] tooMany = new WireWriter().writeVarInt(0).writeVarInt(33).toByteArray();
        assertThatThrownBy(() -> WireCodec.decode(CommandBatch.WIRE, tooMany)).isInstanceOf(WireException.class);
        byte[] none = new WireWriter().writeVarInt(0).writeVarInt(0).toByteArray();
        assertThatThrownBy(() -> WireCodec.decode(CommandBatch.WIRE, none)).isInstanceOf(WireException.class);
        assertThatThrownBy(() -> WireCodec.decode(CommandBatch.WIRE, new byte[Limits.BATCH_BYTES + 1]))
                .isInstanceOf(WireException.class)
                .hasMessageContaining("above the bound");
        int[] run = new int[UnitSet.MAX_SIZE];
        Arrays.setAll(run, i -> i + 1);
        java.util.List<Command> heavy =
                java.util.Collections.nCopies(Limits.BATCH_COMMANDS, Move.replace(UnitSet.of(run), new Vec2d(1, 2)));
        assertThatThrownBy(() -> WireCodec.encode(CommandBatch.WIRE, new CommandBatch(0, heavy)))
                .as("32 moves of 300 units do not fit a batch: the client splits them")
                .isInstanceOf(IllegalStateException.class);
    }

    private static <T> int size(WireCodec<T> codec, T value) {
        return WireCodec.encode(codec, value).length;
    }

    private static <T> T roundTrip(WireCodec<T> codec, T value) {
        return WireCodec.decode(codec, WireCodec.encode(codec, value));
    }
}
