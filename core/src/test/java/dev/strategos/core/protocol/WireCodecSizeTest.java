package dev.strategos.core.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandEnvelope;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
                .isEqualTo("strategos:move=1,strategos:stop=2,strategos:spawn_units=3,"
                        + "strategos:attack=4,strategos:attack_move=5,strategos:follow=6,strategos:grant=7,"
                        + "strategos:spawn_building=8,strategos:gather=9");
        assertThat(CommandKinds.DEFAULT.reads("strategos:move=1,strategos:stop=2,strategos:spawn_units=3"))
                .as("M1's journals read")
                .isTrue();
        assertThat(CommandKinds.DEFAULT.reads("strategos:move=1,strategos:attack=2"))
                .isFalse();
        assertThat(CommandKinds.DEFAULT.reads("strategos:move=1,strategos:fly=6"))
                .isFalse();
        assertThat(CommandKinds.DEFAULT.byWireId(2)).isSameAs(CommandKinds.STOP);
        assertThatThrownBy(() -> new CommandKinds(java.util.List.of(
                        CommandKinds.MOVE,
                        new CommandKind<>("strategos:other", 1, Stop.class, Stop.CODEC, Stop.WIRE, false))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("wire id 1 listed twice");
    }

    @Test
    void refusesUnknownIdsTruncationsAndNonFinitePositions() {
        assertThatThrownBy(() -> WireCodec.decode(COMMANDS, new byte[] {0, 10}))
                .isInstanceOf(WireException.class)
                .hasMessageContaining("unknown command wire id 10");
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
                .as("kind, sequence, outcome, reason, accepted, total, no missing amounts")
                .isEqualTo(1 + 3 + 1 + 1 + 1 + 1 + 1);
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

    @Test
    void theDebugRecordsEncodeToTheExactSizesOfTheFormat() {
        assertThat(size(DebugSubscribe.WIRE, new DebugSubscribe(DebugLayer.ALL, 32)))
                .as("layers, radius")
                .isEqualTo(2);
        DebugFeedMsg.Origin origin = new DebugFeedMsg.Origin(100, -60, -200);
        DebugFeedMsg.Point a = new DebugFeedMsg.Point(160, 16, 160);
        DebugFeedMsg.Point b = new DebugFeedMsg.Point(320, 16, 160);
        DebugFeedMsg.Cells page = new DebugFeedMsg.Cells(origin, 3, 4, List.of(new DebugFeedMsg.Cell(17, 32, 0)));
        assertThat(size(DebugFeedMsg.WIRE, new DebugFeedMsg(DebugLayer.SURFACES, 1_200, page)))
                .as("layer, stamp, origin, page, count, one surface")
                .isEqualTo(1 + 2 + 5 + 2 + 1 + 3);
        DebugFeedMsg.Paths paths = new DebugFeedMsg.Paths(
                origin, List.of(new DebugFeedMsg.PathLine(0x0100_0005, 0, false, List.of(a, b))));
        assertThat(size(DebugFeedMsg.WIRE, new DebugFeedMsg(DebugLayer.PATHS, 1_200, paths)))
                .as("layer, stamp, origin, count, handle, status, flag, two points")
                .isEqualTo(1 + 2 + 5 + 1 + 4 + 1 + 1 + 1 + 5 + 5);
        DebugFeedMsg.Marks marks =
                new DebugFeedMsg.Marks(origin, List.of(new DebugFeedMsg.Mark(0x0100_0005, a, 1, 0, 0)));
        assertThat(size(DebugFeedMsg.WIRE, new DebugFeedMsg(DebugLayer.UNITS, 1_200, marks)))
                .as("layer, stamp, origin, count, handle, point, tier, stuck, rung")
                .isEqualTo(1 + 2 + 5 + 1 + 4 + 5 + 1 + 1 + 1);
        DebugFeedMsg.Panel panel = new DebugFeedMsg.Panel(
                1, 1_200, 2, 0, 3, 1, 2, 0, 0, 0, 40, 30, 2, 5_000, List.of(new DebugFeedMsg.Timing("step", 120_000)));
        assertThat(size(DebugFeedMsg.WIRE, new DebugFeedMsg(DebugLayer.PANEL, 1_200, panel)))
                .as("layer, stamp, fourteen numbers, a count and one timing")
                .isEqualTo(1 + 2 + (1 + 2 + 11 + 2) + 1 + (1 + 4 + 3));
    }

    @Test
    void theDebugRecordsStayWithinTheirCaps() {
        assertThat(DebugSubscribe.WIRE.maxSize()).as("debug_subscribe").isLessThanOrEqualTo(64);
        assertThat(DebugFeedMsg.WIRE.maxSize()).as("debug_feed").isLessThanOrEqualTo(64 * 1024);
        DebugSubscribe all = new DebugSubscribe(DebugLayer.ALL, DebugSubscribe.MAX_RADIUS);
        assertThat(size(DebugSubscribe.WIRE, all)).isEqualTo(DebugSubscribe.WIRE.maxSize());
        assertThat(roundTrip(DebugSubscribe.WIRE, all)).isEqualTo(all);

        DebugFeedMsg.Origin far = new DebugFeedMsg.Origin(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
        int edge = -DebugFeedMsg.COORD_LIMIT;
        DebugFeedMsg.Point corner = new DebugFeedMsg.Point(edge, edge, edge);
        DebugFeedMsg.Cells cells = new DebugFeedMsg.Cells(
                far,
                DebugFeedMsg.MAX_PAGE,
                DebugFeedMsg.MAX_PAGE,
                java.util.Collections.nCopies(
                        DebugFeedMsg.MAX_CELLS, new DebugFeedMsg.Cell(255, DebugFeedMsg.MAX_FLOOR, Integer.MAX_VALUE)));
        DebugFeedMsg.Paths paths = new DebugFeedMsg.Paths(
                far,
                java.util.Collections.nCopies(
                        DebugFeedMsg.MAX_LINES,
                        new DebugFeedMsg.PathLine(
                                -1, 3, true, java.util.Collections.nCopies(DebugFeedMsg.MAX_POINTS, corner))));
        DebugFeedMsg.Marks marks = new DebugFeedMsg.Marks(
                far,
                java.util.Collections.nCopies(
                        DebugFeedMsg.MAX_MARKS, new DebugFeedMsg.Mark(-1, corner, 255, Integer.MAX_VALUE, 255)));
        long most = Long.MAX_VALUE;
        int many = Integer.MAX_VALUE;
        DebugFeedMsg.Panel panel = new DebugFeedMsg.Panel(
                many,
                most,
                many,
                many,
                most,
                most,
                most,
                most,
                most,
                most,
                most,
                many,
                most,
                most,
                java.util.Collections.nCopies(
                        DebugFeedMsg.MAX_TIMINGS, new DebugFeedMsg.Timing("t".repeat(DebugFeedMsg.NAME_BYTES), most)));

        assertWidest(new DebugFeedMsg(DebugLayer.COMPONENTS, most, cells), DebugFeedMsg.CELLS);
        assertWidest(new DebugFeedMsg(DebugLayer.PATHS, most, paths), DebugFeedMsg.PATHS);
        assertWidest(new DebugFeedMsg(DebugLayer.UNITS, most, marks), DebugFeedMsg.MARKS);
        assertWidest(new DebugFeedMsg(DebugLayer.PANEL, most, panel), DebugFeedMsg.PANEL);
        assertThat(DebugFeedMsg.WIRE.maxSize()).isEqualTo(1 + 9 + DebugFeedMsg.PATHS.maxSize());
    }

    @Test
    void theDebugRecordsRefuseWhatTheyCannotHold() {
        assertThatThrownBy(() -> new DebugSubscribe(DebugLayer.ALL + 1, 32))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DebugSubscribe(DebugLayer.ALL, DebugSubscribe.MAX_RADIUS + 1))
                .isInstanceOf(IllegalArgumentException.class);
        byte[] narrow = new WireWriter().writeVarInt(1).writeVarInt(2).toByteArray();
        assertThatThrownBy(() -> WireCodec.decode(DebugSubscribe.WIRE, narrow))
                .isInstanceOf(WireException.class)
                .hasMessageContaining("radius 2");
        byte[] unknown = new WireWriter().writeByte(9).writeVarLong(0).toByteArray();
        assertThatThrownBy(() -> WireCodec.decode(DebugFeedMsg.WIRE, unknown))
                .isInstanceOf(WireException.class)
                .hasMessageContaining("unknown debug layer 9");
        byte[] beyond = new WireWriter()
                .writeByte(DebugLayer.PATHS.code())
                .writeVarLong(0)
                .writeZigZag(0)
                .writeZigZag(0)
                .writeZigZag(0)
                .writeVarInt(1)
                .writeInt(5)
                .writeByte(0)
                .writeBoolean(false)
                .writeVarInt(1)
                .writeZigZag(DebugFeedMsg.COORD_LIMIT)
                .writeZigZag(0)
                .writeZigZag(0)
                .toByteArray();
        assertThatThrownBy(() -> WireCodec.decode(DebugFeedMsg.WIRE, beyond))
                .isInstanceOf(WireException.class)
                .hasMessageContaining("too far");
        DebugFeedMsg.Marks none = new DebugFeedMsg.Marks(new DebugFeedMsg.Origin(0, 0, 0), List.of());
        assertThatThrownBy(() -> new DebugFeedMsg(DebugLayer.PANEL, 0, none))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot carry");
    }

    @Test
    void aFactionStateRoundTripsWithinItsCap() {
        List<Long> amounts = new ArrayList<>();
        List<Integer> workers = new ArrayList<>();
        for (int r = 0; r < FactionState.MAX_RESOURCES; r++) {
            amounts.add(Long.MAX_VALUE - r);
            workers.add(Integer.MAX_VALUE - r);
        }
        FactionState widest = new FactionState(
                Integer.MAX_VALUE, 7, Long.MAX_VALUE, amounts, amounts, amounts, 150, 200, 99, workers);
        assertThat(size(FactionState.WIRE, widest)).isLessThanOrEqualTo(FactionState.WIRE.maxSize());
        assertThat(roundTrip(FactionState.WIRE, widest)).isEqualTo(widest);

        FactionState opening = new FactionState(
                1,
                0,
                400,
                List.of(300_000L, 200_000L, 0L, 0L),
                List.of(0L, 0L, 0L, 0L),
                List.of(0L, 0L, 0L, 0L),
                5,
                20,
                0,
                List.of(0, 0, 0, 0));
        assertThat(size(FactionState.WIRE, opening))
                .as("a usual state is a few dozen bytes")
                .isLessThan(40);
        assertThat(roundTrip(FactionState.WIRE, opening)).isEqualTo(opening);
    }

    @Test
    void aRefusalForWantOfResourcesCarriesTheMissingAmounts() {
        List<Long> missing = new ArrayList<>();
        for (int r = 0; r < Feedback.MAX_MISSING; r++) {
            missing.add(Long.MAX_VALUE);
        }
        Feedback widest = new Feedback(
                Integer.MAX_VALUE, CommandOutcome.REJECTED, RejectReason.NOT_ENOUGH_RESOURCES, 0, 300, missing);
        assertThat(size(FeedbackMsg.WIRE, widest)).isLessThanOrEqualTo(FeedbackMsg.WIRE.maxSize());
        assertThat(roundTrip(FeedbackMsg.WIRE, widest)).isEqualTo(widest);
        Feedback some = new Feedback(
                4, CommandOutcome.REJECTED, RejectReason.NOT_ENOUGH_RESOURCES, 0, 1, List.of(0L, 35_000L, 0L, 0L));
        assertThat(roundTrip(FeedbackMsg.WIRE, some)).isEqualTo(some);
    }

    @Test
    void aGatherRoundTripsWithinItsSize() {
        Gather widest = new Gather(
                UnitSet.of(java.util.stream.IntStream.range(0, UnitSet.MAX_SIZE)
                        .map(k -> Integer.MAX_VALUE - k)
                        .toArray()),
                Integer.MIN_VALUE,
                Integer.MAX_VALUE,
                Integer.MIN_VALUE,
                QueueMode.PUSH_FRONT);
        assertThat(roundTrip(COMMANDS, widest)).isEqualTo(widest);
        assertThat(size(Gather.WIRE, widest)).isLessThanOrEqualTo(Gather.WIRE.maxSize());
    }

    @Test
    void aGrantRoundTrips() {
        Grant grant = new Grant(new FactionSlot(3), "stone", Grant.MAX);
        assertThat(roundTrip(COMMANDS, grant)).isEqualTo(grant);
        assertThat(size(Grant.WIRE, grant)).isLessThanOrEqualTo(Grant.WIRE.maxSize());
    }

    private static <P extends DebugFeedMsg.Payload> void assertWidest(DebugFeedMsg widest, WireCodec<P> payload) {
        assertThat(size(DebugFeedMsg.WIRE, widest))
                .as("the widest %s", widest.layer())
                .isEqualTo(1 + 9 + payload.maxSize());
        assertThat(roundTrip(DebugFeedMsg.WIRE, widest)).isEqualTo(widest);
    }

    /** CAM-05: a mode switch round-trips at its fixed size. */
    @Test
    void aModeSwitchRoundTripsAtItsSize() {
        ModeSwitch down = new ModeSwitch(true, -120.5, 3_000.25);
        assertThat(size(ModeSwitch.WIRE, down)).isEqualTo(ModeSwitch.WIRE.maxSize());
        assertThat(roundTrip(ModeSwitch.WIRE, down)).isEqualTo(down);
        assertThat(roundTrip(ModeSwitch.WIRE, ModeSwitch.BACK)).isEqualTo(ModeSwitch.BACK);
    }

    @Test
    void anFxBatchRoundTripsWithinItsCap() {
        Fx one = new Fx(7, List.of(Fx.Event.at(FxKind.UNIT_HIT, 12.5, -3.25, 200)));
        assertThat(size(Fx.WIRE, one)).isEqualTo(1 + 1 + 1 + 2 + 1 + 2);
        assertThat(roundTrip(Fx.WIRE, one)).isEqualTo(one);
        Fx.Event widest =
                new Fx.Event(FxKind.BLOCK_BREAK, Fx.MAX_EIGHTHS, Integer.MIN_VALUE, Fx.MAX_EIGHTHS, Integer.MIN_VALUE);
        Fx full = new Fx(Integer.MAX_VALUE, java.util.Collections.nCopies(Fx.MAX_EVENTS, widest));
        assertThat(size(Fx.WIRE, full)).isEqualTo(Fx.WIRE.maxSize());
        assertThat(roundTrip(Fx.WIRE, full)).isEqualTo(full);
        assertThatThrownBy(() -> new Fx(1, java.util.Collections.nCopies(Fx.MAX_EVENTS + 1, widest)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Fx.Event(FxKind.UNIT_HIT, -1, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Fx.Event(FxKind.UNIT_HIT, 0, 0, 0, 3))
                .as("a unit's event carries no datum")
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * AUD-04.2: the writer's whole budget in one tick, 64 placements of states with ids past the vanilla registry's, at
     * the far edge of a 512-block space, is one payload of at most {@value #FX_TICK_CAP} bytes.
     */
    @Test
    void aSixtyFourPlacementTickFitsTheFxCap() {
        List<Fx.Event> placed = new java.util.ArrayList<>();
        for (int k = 0; k < Fx.MAX_EVENTS; k++) {
            placed.add(Fx.Event.block(FxKind.BLOCK_PLACE, 511 - k % 8, 319, 511 - k / 8, 90_000 + k));
        }
        Fx tick = new Fx(1_000, placed);
        assertThat(size(Fx.WIRE, tick)).isLessThanOrEqualTo(FX_TICK_CAP);
        assertThat(roundTrip(Fx.WIRE, tick)).isEqualTo(tick);
        Fx crack = new Fx(1, List.of(Fx.Event.block(FxKind.BLOCK_CRACK, 3, 70, 4, -1)));
        assertThat(roundTrip(Fx.WIRE, crack)).isEqualTo(crack);
        assertThat(size(Fx.WIRE, crack)).isEqualTo(1 + 1 + 1 + 2 + 2 + 2 + 1);
    }

    /** AUD-04.2's cap for one tick's world fx to one viewer: 64 placements in under a kilobyte. */
    private static final int FX_TICK_CAP = 1_000;

    private static <T> int size(WireCodec<T> codec, T value) {
        return WireCodec.encode(codec, value).length;
    }

    private static <T> T roundTrip(WireCodec<T> codec, T value) {
        return WireCodec.decode(codec, WireCodec.encode(codec, value));
    }
}
