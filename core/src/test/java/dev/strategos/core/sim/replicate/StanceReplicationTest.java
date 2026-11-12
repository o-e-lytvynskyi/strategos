package dev.strategos.core.sim.replicate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.SetStance;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitDetail;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.protocol.WireCodec;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Stance;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.testkit.SimHarness;
import java.util.List;
import org.junit.jupiter.api.Test;

/** CMB-05: a unit's stance rides {@code unit_detail} to the faction that owns it, and only when a player set it. */
class StanceReplicationTest {
    private static final int MATCH = 1;
    private static final FactionSlot VIEWER = new FactionSlot(0);

    private static int spearman(SimHarness h, int faction, Vec2d at) {
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.INFANTRY, new FactionSlot(faction), at, 1, 0));
        return h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .findFirst()
                .orElseThrow();
    }

    private static UnitDetail.Unit plain(int handle, int stance) {
        return new UnitDetail.Unit(handle, 0, 2, 100f, 100f, 0.2f, List.of(), -1, 0, UnitDetail.NO_TARGET, stance);
    }

    @Test
    void aStanceSetByAnyPathIsSentForOwnUnitsOnly() {
        SimHarness h = SimHarness.builder().factions(2).build();
        int own = spearman(h, 0, new Vec2d(8, 8));
        int other = spearman(h, 0, new Vec2d(9, 8));
        int enemy = spearman(h, 1, new Vec2d(30, 30));
        UnitDetail before = ReplicationService.detail(h.queries(), MATCH, VIEWER, List.of(own, other));
        assertThat(before.units()).extracting(UnitDetail.Unit::stance).containsOnly(UnitDetail.NO_STANCE);

        // not the viewer's own card: another client (here the admin slot) gives the stances
        h.submit(PlayerSlot.ADMIN, new SetStance(UnitSet.of(own), Stance.STAND_GROUND));
        h.submit(PlayerSlot.ADMIN, new SetStance(UnitSet.of(enemy), Stance.PASSIVE));
        h.runTicks(1);
        UnitDetail after = ReplicationService.detail(h.queries(), MATCH, VIEWER, List.of(own, other, enemy));
        assertThat(after.units()).extracting(UnitDetail.Unit::handle).containsExactly(own, other);
        assertThat(after.units())
                .extracting(UnitDetail.Unit::stance)
                .containsExactly(Stance.STAND_GROUND.code(), UnitDetail.NO_STANCE);
        assertThat(ReplicationService.detail(h.queries(), MATCH, VIEWER, List.of(enemy))
                        .units())
                .as("an enemy's stance is never sent")
                .isEmpty();
    }

    @Test
    void theStanceTrailerRoundTripsAndAFrameWithoutOneIsUnchanged() {
        UnitDetail old = new UnitDetail(MATCH, 9, List.of(plain(5, UnitDetail.NO_STANCE)));
        byte[] oldBytes = WireCodec.encode(UnitDetail.WIRE, old);
        UnitDetail oldShape =
                new UnitDetail(MATCH, 9, List.of(new UnitDetail.Unit(5, 0, 2, 100f, 100f, 0.2f, List.of(), -1, 0)));
        assertThat(WireCodec.encode(UnitDetail.WIRE, oldShape))
                .as("no stance, no extra byte")
                .isEqualTo(oldBytes);
        assertThat(WireCodec.decode(UnitDetail.WIRE, oldBytes)).isEqualTo(old);

        UnitDetail stanced = new UnitDetail(
                MATCH, 9, List.of(plain(5, Stance.PASSIVE.code()), plain(6, UnitDetail.NO_STANCE), plain(7, 0)));
        byte[] bytes = WireCodec.encode(UnitDetail.WIRE, stanced);
        assertThat(WireCodec.decode(UnitDetail.WIRE, bytes)).isEqualTo(stanced);
        UnitDetail one = new UnitDetail(MATCH, 9, List.of(plain(5, Stance.DEFENSIVE.code())));
        assertThat(WireCodec.encode(UnitDetail.WIRE, one))
                .as("an empty attack count, then one entry of a place and a code")
                .hasSize(oldBytes.length + 1 + 1 + 1 + 1);

        UnitDetail both = new UnitDetail(
                MATCH,
                9,
                List.of(
                        new UnitDetail.Unit(5, 0, 2, 100f, 100f, 0.2f, List.of(), -1, 0, 300, 4),
                        plain(6, UnitDetail.NO_STANCE),
                        plain(7, 1)));
        byte[] bothBytes = WireCodec.encode(UnitDetail.WIRE, both);
        assertThat(WireCodec.decode(UnitDetail.WIRE, bothBytes)).isEqualTo(both);
        assertThat(bothBytes.length).isLessThanOrEqualTo(UnitDetail.WIRE.maxSize());
    }
}
