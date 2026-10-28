package dev.strategos.core.sim.replicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.protocol.UnitSummary;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.testkit.SimHarness;
import org.junit.jupiter.api.Test;

class TeamVisionTest {
    private static final FactionSlot A = new FactionSlot(0);
    private static final FactionSlot B = new FactionSlot(1);
    private static final FactionSlot C = new FactionSlot(2);

    @Test
    void sharingIsDirectionalDirectAndIncludesSelf() {
        TeamVision vision = new TeamVision(
                3,
                (source, viewer) -> source.value() == viewer.value() + 1,
                (viewer, unit) -> false,
                (viewer, x, z) -> viewer.value() == x,
                (viewer, x, z) -> viewer.value() == z);
        assertThat(vision.visible(A, 0, 9)).isTrue();
        assertThat(vision.visible(A, 1, 9)).isTrue();
        assertThat(vision.visible(A, 2, 9))
                .as("no transitive sharing C -> B -> A")
                .isFalse();
        assertThat(vision.visible(B, 0, 9)).as("sharing is not symmetric").isFalse();
        assertThat(vision.explored(A, 9, 1)).isTrue();
        assertThat(vision.explored(A, 9, 2)).isFalse();
        assertThat(vision.visible(new FactionSlot(3), 0, 9)).isFalse();
        assertThat(vision.explored(new FactionSlot(3), 9, 0)).isFalse();
    }

    @Test
    void replicationUnionsAlliedSightAndHidesUnseenEnemies() {
        SimHarness h = SimHarness.builder().factions(3).build();
        int own = h.spawn(A, new Vec2d(8, 8), 1, 0)[0];
        int ally = h.spawn(B, new Vec2d(16, 16), 1, 0)[0];
        int[] enemies = h.spawn(C, new Vec2d(24, 24), 2, 2);
        h.runTicks(1);
        TeamVision vision = new TeamVision(
                3,
                (source, viewer) -> source.equals(B) && viewer.equals(A),
                (viewer, unit) -> viewer.equals(B) && unit.handle() == enemies[0],
                (viewer, x, z) -> false,
                (viewer, x, z) -> false);
        assertThat(ReplicationService.summary(h.queries(), 7, A, vision).units())
                .extracting(UnitSummary.Entry::index)
                .containsExactly(Handle.index(own), Handle.index(ally), Handle.index(enemies[0]));
        assertThat(ReplicationService.summary(h.queries(), 7, C, vision).units())
                .hasSize(2)
                .allMatch(unit -> unit.faction() == C.value());
        var enemy = h.queries().unit(enemies[0]);
        assertThat(enemy).isNotNull();
        assertThat(vision.sees(new FactionSlot(3), enemy)).isFalse();
    }

    @Test
    void nextTickQueriesTakeEffectWithoutAnotherHysteresisLayer() {
        boolean[] sharing = {true};
        boolean[] visible = {true};
        TeamVision vision = new TeamVision(
                2,
                (source, viewer) -> sharing[0] && source.equals(B),
                (viewer, unit) -> false,
                (viewer, x, z) -> visible[0] && viewer.equals(B),
                (viewer, x, z) -> viewer.equals(B));
        assertThat(vision.visible(A, 1, 1)).isTrue();
        visible[0] = false;
        assertThat(vision.visible(A, 1, 1)).isFalse();
        assertThat(vision.explored(A, 1, 1)).isTrue();
        sharing[0] = false;
        assertThat(vision.explored(A, 1, 1)).isFalse();
    }

    @Test
    void allSixteenParticipantsAreSupportedAndOthersFailClosedWithoutQuerying() {
        FactionSlot last = new FactionSlot(FactionSlot.MAX);
        TeamVision full = new TeamVision(
                16, (s, v) -> false, Visibility.ALL, (v, x, z) -> v.equals(last), (v, x, z) -> v.equals(last));
        assertThat(full.visible(last, 1, 1)).isTrue();
        assertThat(full.explored(last, 1, 1)).isTrue();
        TeamVision one = new TeamVision(
                1,
                (s, v) -> {
                    throw new AssertionError("nonmember sharing");
                },
                (v, u) -> {
                    throw new AssertionError("nonmember unit");
                },
                (v, x, z) -> {
                    throw new AssertionError("nonmember visible");
                },
                (v, x, z) -> {
                    throw new AssertionError("nonmember explored");
                });
        assertThat(one.visible(B, 0, 0)).isFalse();
        assertThat(one.explored(B, 0, 0)).isFalse();
        for (int count : new int[] {0, 17}) {
            assertThatThrownBy(() ->
                            new TeamVision(count, (s, v) -> true, Visibility.ALL, (v, x, z) -> true, (v, x, z) -> true))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
