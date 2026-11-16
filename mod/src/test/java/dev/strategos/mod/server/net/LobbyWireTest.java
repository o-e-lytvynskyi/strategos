package dev.strategos.mod.server.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.protocol.SkirmishSetup;
import dev.strategos.core.protocol.WireCodec;
import dev.strategos.core.protocol.WireException;
import dev.strategos.core.protocol.WireWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Bounded lobby intents and private views, independent of authenticated connection membership. */
class LobbyWireTest {
    private static LobbyConfiguration configuration() {
        List<LobbyConfiguration.Seat> seats = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            seats.add(new LobbyConfiguration.Seat(
                    i, LobbyConfiguration.Kind.HUMAN, i / 2, i / 2, "strategos:placeholder", i / 2));
        return new LobbyConfiguration(2, seats, SkirmishSetup.DEFAULTS);
    }

    @Test
    void allIntentsRoundTripWithoutUuidsOrAuthoritativeOccupants() {
        for (LobbyAction action : List.of(
                new LobbyAction.Open(configuration()),
                new LobbyAction.Edit(configuration()),
                new LobbyAction.Join(3),
                new LobbyAction.Ready(true, Long.MAX_VALUE),
                new LobbyAction.Ready(false, 0),
                new LobbyAction.Leave(),
                new LobbyAction.Start())) {
            byte[] bytes = WireCodec.encode(LobbyAction.WIRE, action);
            assertThat(bytes).hasSizeLessThanOrEqualTo(LobbyAction.WIRE.maxSize());
            assertThat(WireCodec.decode(LobbyAction.WIRE, bytes)).isEqualTo(action);
        }
        for (Class<?> type : List.of(
                LobbyAction.Open.class,
                LobbyAction.Edit.class,
                LobbyAction.Join.class,
                LobbyAction.Ready.class,
                LobbyConfiguration.Seat.class)) {
            assertThat(type.getRecordComponents())
                    .allSatisfy(field -> assertThat(field.getType()).isNotEqualTo(java.util.UUID.class));
        }
    }

    @Test
    void privateViewRoundTripsAllPhasesAndFourMembers() {
        List<LobbyState.Member> members = List.of(
                new LobbyState.Member(0, "host", true, true),
                new LobbyState.Member(1, "peer1", false, false),
                new LobbyState.Member(2, "peer2", true, false),
                new LobbyState.Member(3, "peer3", true, false));
        for (LobbyState.Phase phase : LobbyState.Phase.values()) {
            var state = new LobbyState(
                    configuration(), members, 3, false, Long.MAX_VALUE, Long.MAX_VALUE, phase, "preparation_failed");
            byte[] bytes = WireCodec.encode(LobbyState.WIRE, state);
            assertThat(bytes).hasSizeLessThanOrEqualTo(LobbyState.WIRE.maxSize());
            assertThat(WireCodec.decode(LobbyState.WIRE, bytes)).isEqualTo(state);
        }
    }

    @Test
    void invalidBoundsAliasesAndMembersAreRejected() {
        var seats = configuration().seats();
        for (List<LobbyConfiguration.Seat> invalid : List.of(
                List.<LobbyConfiguration.Seat>of(),
                List.of(seats.get(0), seats.get(0)),
                List.of(seats.get(0), seats.get(1), seats.get(2), seats.get(3), seats.get(0)))) {
            assertThatThrownBy(() -> new LobbyConfiguration(2, invalid, SkirmishSetup.DEFAULTS))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new LobbyConfiguration.Seat(4, LobbyConfiguration.Kind.HUMAN, 0, 0, "a:b", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        new LobbyConfiguration.Seat(0, LobbyConfiguration.Kind.HUMAN, 0, 0, "a:" + "x".repeat(127), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LobbyAction.Ready(true, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LobbyState.Member(0, "x".repeat(17), false, false))
                .isInstanceOf(IllegalArgumentException.class);
        var host = new LobbyState.Member(0, "host", false, true);
        assertThatThrownBy(() ->
                        new LobbyState(configuration(), List.of(host), 1, false, 0, 0, LobbyState.Phase.EDITING, ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LobbyState(
                        configuration(), List.of(host, host), 0, false, 0, 0, LobbyState.Phase.EDITING, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void maximumFieldsFitDeclaredCaps() {
        var seats = new ArrayList<LobbyConfiguration.Seat>();
        var members = new ArrayList<LobbyState.Member>();
        for (int i = 0; i < 4; i++) {
            seats.add(new LobbyConfiguration.Seat(i, LobbyConfiguration.Kind.HUMAN, i, i, "a:" + "x".repeat(126), i));
            members.add(new LobbyState.Member(i, "\uD801\uDC00".repeat(8), true, i == 0));
        }
        var config = new LobbyConfiguration(4, seats, SkirmishSetup.DEFAULTS);
        var state = new LobbyState(
                config, members, 0, true, Long.MAX_VALUE, Long.MAX_VALUE, LobbyState.Phase.FAILED, "_".repeat(64));
        byte[] bytes = WireCodec.encode(LobbyState.WIRE, state);
        assertThat(bytes).hasSizeLessThanOrEqualTo(LobbyState.WIRE.maxSize());
        assertThat(WireCodec.decode(LobbyState.WIRE, bytes)).isEqualTo(state);
    }

    @Test
    void listsAreDetachedAndImmutable() {
        var seats = new ArrayList<>(configuration().seats());
        var config = new LobbyConfiguration(2, seats, SkirmishSetup.DEFAULTS);
        seats.clear();
        assertThat(config.seats()).hasSize(4);
        assertThatThrownBy(() -> config.seats().clear()).isInstanceOf(UnsupportedOperationException.class);
        var members = new ArrayList<>(List.of(new LobbyState.Member(0, "host", false, true)));
        var state = new LobbyState(config, members, 0, true, 0, 0, LobbyState.Phase.EDITING, "");
        members.clear();
        assertThat(state.members()).hasSize(1);
    }

    @Test
    void unsupportedHeadersExcessCountsAndTruncationAreWireFaults() {
        for (byte[] bytes : List.of(
                new byte[] {2, 0},
                new byte[] {1, 127},
                new WireWriter()
                        .writeByte(1)
                        .writeByte(0)
                        .writeByte(2)
                        .writeVarInt(5)
                        .toByteArray())) {
            assertThatThrownBy(() -> WireCodec.decode(LobbyAction.WIRE, bytes)).isInstanceOf(WireException.class);
        }
        byte[] valid = WireCodec.encode(LobbyAction.WIRE, new LobbyAction.Open(configuration()));
        for (int i = 0; i < valid.length; i++) {
            byte[] truncated = java.util.Arrays.copyOf(valid, i);
            assertThatThrownBy(() -> WireCodec.decode(LobbyAction.WIRE, truncated))
                    .isInstanceOf(WireException.class);
        }
    }

    @Test
    void seededMalformedInputsStayWithinTheWireBoundary() {
        Random random = new Random(0x7B0BB1);
        for (int i = 0; i < 100_000; i++) {
            byte[] bytes = new byte[random.nextInt(1025)];
            random.nextBytes(bytes);
            if (bytes.length > 1 && i % 2 == 0) {
                bytes[0] = 1;
                bytes[1] = (byte) (i % 6);
            }
            try {
                WireCodec.decode(LobbyAction.WIRE, bytes);
            } catch (WireException expected) {
                /* bounded rejection */
            }
            try {
                WireCodec.decode(LobbyState.WIRE, bytes);
            } catch (WireException expected) {
                /* bounded rejection */
            }
        }
    }
}
