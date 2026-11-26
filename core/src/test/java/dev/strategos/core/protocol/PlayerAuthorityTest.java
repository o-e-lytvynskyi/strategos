package dev.strategos.core.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.shared.PlayerSlot;
import org.junit.jupiter.api.Test;

/** T8.3b: recipient identity, primary perspective and control rights never share a numeric domain. */
class PlayerAuthorityTest {
    private static final PlayerSlot P1 = new PlayerSlot(1);
    private static final PlayerSlot P2 = new PlayerSlot(2);

    private static MatchJoin joined(PlayerAuthority authority, int factions) {
        return new MatchJoin(7, 100, -60, -200, 48, 48, -64, 31, factions, authority.player(), "{}", authority);
    }

    @Test
    void coopAndHighNumberedSeatsUseTheirPrimaryAndSeparateAlliesFromControl() {
        var coop = joined(new PlayerAuthority(7, P1, 10, 0, 1, 3), 2);
        assertThat(coop.slot()).isEqualTo(P1);
        assertThat(coop.primaryFaction()).isZero();
        assertThat(coop.controlsFaction(0)).isTrue();
        assertThat(coop.controlsFaction(1)).isFalse();
        assertThat(coop.alliedFaction(1)).isTrue();
        var granted = joined(new PlayerAuthority(7, new PlayerSlot(3), 10, 2, 5, 7), 3);
        assertThat(granted.primaryFaction()).isEqualTo(2);
        assertThat(granted.controlsFaction(0)).isTrue();
        assertThat(granted.controlsFaction(2)).isTrue();
        assertThat(granted.controlsFaction(1)).isFalse();
        assertThat(granted.controlsFaction(-1)).isFalse();
        assertThat(granted.controlsFaction(16)).isFalse();
        assertThat(WireCodec.decode(MatchJoin.WIRE, WireCodec.encode(MatchJoin.WIRE, granted)))
                .isEqualTo(granted);
    }

    @Test
    void anObserverHasNoPlayerCommandAuthorityAndCannotBeAnUnboundPlayerFallback() {
        var observer = joined(new PlayerAuthority(7, PlayerSlot.ADMIN, 10, -1, 0, 0), 2);
        assertThat(observer.primaryFaction()).isEqualTo(-1);
        assertThat(observer.controlsFaction(0)).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> new PlayerAuthority(7, P1, 10, -1, 0, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new PlayerAuthority(7, PlayerSlot.ADMIN, 10, 0, 1, 1));
    }

    @Test
    void revocationChangesOnlyAuthorityAndStaleOrForeignUpdatesCannotRestoreIt() {
        var original = joined(new PlayerAuthority(7, P2, 10, 1, 3, 3), 2);
        var revoked = original.withAuthority(new PlayerAuthority(7, P2, 12, 1, 2, 3));
        assertThat(revoked.controlsFaction(0)).isFalse();
        assertThat(revoked.controlsFaction(1)).isTrue();
        assertThat(revoked.slot()).isEqualTo(P2);
        assertThat(revoked.originX()).isEqualTo(original.originX());
        assertThat(revoked.rules()).isEqualTo(original.rules());
        assertThat(revoked.withAuthority(new PlayerAuthority(7, P2, 11, 1, 3, 3)))
                .isSameAs(revoked);
        assertThat(revoked.withAuthority(new PlayerAuthority(7, P2, 12, 1, 3, 3)))
                .isSameAs(revoked);
        assertThat(revoked.withAuthority(new PlayerAuthority(8, P2, 13, 1, 3, 3)))
                .isSameAs(revoked);
        assertThat(revoked.withAuthority(new PlayerAuthority(7, P1, 13, 1, 3, 3)))
                .isSameAs(revoked);
        assertThat(revoked.withAuthority(new PlayerAuthority(7, P2, 13, 0, 1, 3)))
                .isSameAs(revoked);
        assertThat(original.controlsFaction(0)).isTrue();
    }

    @Test
    void malformedAndOutOfMatchMasksCannotGrantAHiddenExtraFaction() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PlayerAuthority(7, P1, 10, 0, 0, 3));
        assertThatIllegalArgumentException().isThrownBy(() -> new PlayerAuthority(7, P1, 10, 0, 3, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new PlayerAuthority(7, P1, 10, 0, 65537, 65537));
        var foreign = new PlayerAuthority(7, P2, 11, 1, 32770, 32770);
        var valid = joined(new PlayerAuthority(7, P2, 10, 1, 2, 3), 2);
        assertThat(valid.withAuthority(foreign)).isSameAs(valid);
        assertThatIllegalArgumentException().isThrownBy(() -> joined(foreign, 2));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MatchJoin(8, 0, 0, 0, 48, 48, 0, 31, 2, P2, "{}", valid.authority()));
    }

    @Test
    void bothWireFormsRoundTripAtTheActualMaximumWithoutRelaxingCaps() {
        var widest = new PlayerAuthority(Integer.MAX_VALUE, new PlayerSlot(15), Long.MAX_VALUE, 15, 65535, 65535);
        byte[] bytes = WireCodec.encode(PlayerAuthority.WIRE, widest);
        assertThat(bytes).hasSize(PlayerAuthority.WIRE.maxSize());
        assertThat(WireCodec.decode(PlayerAuthority.WIRE, bytes)).isEqualTo(widest);
        assertThat(MatchJoin.WIRE.maxSize()).isLessThanOrEqualTo(4096);
        assertThatThrownBy(
                        () -> WireCodec.decode(PlayerAuthority.WIRE, java.util.Arrays.copyOf(bytes, bytes.length - 1)))
                .isInstanceOf(WireException.class);
        WireWriter bad = new WireWriter().writeVarInt(7);
        WireCodecs.PLAYER_SLOT.write(bad, P1);
        bad.writeVarLong(1).writeZigZag(-1).writeVarInt(0).writeVarInt(0);
        assertThatThrownBy(() -> WireCodec.decode(PlayerAuthority.WIRE, bad.toByteArray()))
                .isInstanceOf(WireException.class);
    }
}
