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

/** NET-01.2 for the commands, CORE-11 for the journal's envelopes: exact sizes, round trips and bounds. */
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

    private static <T> int size(WireCodec<T> codec, T value) {
        return WireCodec.encode(codec, value).length;
    }

    private static <T> T roundTrip(WireCodec<T> codec, T value) {
        return WireCodec.decode(codec, WireCodec.encode(codec, value));
    }
}
