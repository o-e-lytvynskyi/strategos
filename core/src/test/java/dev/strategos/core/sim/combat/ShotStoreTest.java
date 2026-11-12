package dev.strategos.core.sim.combat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShotStoreTest {
    @Test
    void dueRowsLeaveOnceInLaunchOrderAndUsedEmptyRestoreKeepsAllocator() {
        ShotStore s = new ShotStore();
        assertThat(s.pristine()).isTrue();
        var first = s.launch(7, 0, ShotAimTest.launch(20, 0, 0, 0, 1, 0));
        var second = s.launch(7, 0, ShotAimTest.launch(2, 0, 0, 0, 1, 0));
        assertThat(s.count()).isEqualTo(2);
        assertThat(s.shot(0)).isEqualTo(first);
        assertThat(s.takeDue(1)).isEmpty();
        assertThat(s.takeDue(20)).containsExactly(first, second);
        assertThat(s.takeDue(20)).isEmpty();
        assertThat(s.pristine()).isFalse();
        ShotStore restored = new ShotStore();
        restored.restore(s.save());
        assertThat(restored.nextId()).isEqualTo(3);
        assertThat(restored.pristine()).isFalse();
        assertThat(restored.launch(7, 21, ShotAimTest.launch(0, 0, 0, 0, 1, 0)).id())
                .isEqualTo(3);
    }

    @Test
    void partialDueCompactionKeepsUndueRowsAndTheLastUsableIdDoesNotWrap() {
        ShotStore s = new ShotStore();
        var late = s.launch(7, 0, ShotAimTest.launch(20, 0, 0, 0, 1, 0));
        var early = s.launch(7, 0, ShotAimTest.launch(2, 0, 0, 0, 1, 0));
        assertThat(s.takeDue(2)).containsExactly(early);
        assertThat(s.count()).isEqualTo(1);
        assertThat(s.shot(0)).isEqualTo(late);
        s.restore(new ShotStore.Snapshot((Long.MAX_VALUE - 1) / 2, List.of()));
        assertThat(s.launch(7, 0, ShotAimTest.launch(0, 0, 0, 0, 1, 0)).id()).isEqualTo((Long.MAX_VALUE - 1) / 2);
        var before = s.save();
        assertThatThrownBy(() -> s.launch(7, 0, ShotAimTest.launch(0, 0, 0, 0, 1, 0)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(s.save()).isEqualTo(before);
    }

    @Test
    void snapshotsAndDueListsAreImmutableAndIndependent() {
        ShotStore s = new ShotStore();
        var shot = s.launch(7, 0, ShotAimTest.launch(2, 0, 0, 0, 1, 0));
        var rows = new ArrayList<>(List.of(shot));
        var snapshot = new ShotStore.Snapshot(2, rows);
        rows.clear();
        s.restore(snapshot);
        var due = s.takeDue(2);
        assertThat(snapshot.rows()).containsExactly(shot);
        assertThatThrownBy(() -> snapshot.rows().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> due.clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(s.save().rows()).isEmpty();
    }

    @Test
    void invalidSnapshotsAndExhaustionFailWithoutMutation() {
        ShotStore s = new ShotStore();
        var shot = s.launch(7, 0, ShotAimTest.launch(2, 0, 0, 0, 1, 0));
        assertThatThrownBy(() -> new ShotStore.Snapshot(0, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ShotStore.Snapshot(1, List.of(shot))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ShotStore.Snapshot(3, List.of(shot, shot)))
                .isInstanceOf(IllegalArgumentException.class);
        var second = s.launch(7, 0, ShotAimTest.launch(2, 0, 0, 0, 1, 0));
        assertThatThrownBy(() -> new ShotStore.Snapshot(3, List.of(second, shot)))
                .isInstanceOf(IllegalArgumentException.class);
        var before = s.save();
        assertThatThrownBy(() -> s.launch(7, Long.MAX_VALUE, ShotAimTest.launch(2, 0, 0, 0, 1, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(s.save()).isEqualTo(before);
        s.restore(new ShotStore.Snapshot(Long.MAX_VALUE, List.of()));
        assertThatThrownBy(() -> s.launch(7, 0, ShotAimTest.launch(0, 0, 0, 0, 1, 0)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(s.nextId()).isEqualTo(Long.MAX_VALUE);
        assertThat(s.count()).isZero();
    }

    @Test
    void savedShotsValidateFiniteCoordinatesTicksIdsAndMatchingFullHandles() {
        Damage d = Damage.of(-1048576, -1048575, DamageType.PIERCE, 1);
        var shot = new ShotStore.Shot(1, d.attacker(), d.target(), 0, 1, 2, 3, d);
        assertThat(shot.shooter()).isEqualTo(-1048575);
        assertThat(shot.target()).isEqualTo(-1048576);
        assertThatThrownBy(() -> new ShotStore.Shot(0, d.attacker(), d.target(), 0, 1, 2, 3, d))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ShotStore.Shot(1, d.attacker(), d.target(), -1, 1, 2, 3, d))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ShotStore.Shot(1, d.attacker(), d.target(), 0, Double.NaN, 2, 3, d))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ShotStore.Shot(1, 0, d.target(), 0, 1, 2, 3, d))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ShotStore.Shot(1, d.attacker(), 0, 0, 1, 2, 3, d))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aLaunchFreezesItsReleaseAndRejectsABadOne() {
        Damage d = Damage.of(2, 1, DamageType.PIERCE, 1);
        ShotStore store = new ShotStore();
        var shot = store.launch(1, 7, new ShotStore.Launch(1, 2, 3, 10, 1, 0, 0, 0, 1, 0, d));
        assertThat(shot.release()).isEqualTo(new ShotStore.Release(1, 2, 3, 7));
        var legacy = store.launch(1, 7, new ShotStore.Launch(1, 3, 10, 1.5, 0, 0, 0, 1, 0, d));
        assertThat(legacy.release().y()).isEqualTo(1.5);
        assertThatThrownBy(() -> new ShotStore.Release(Double.NaN, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ShotStore.Release(0, 0, 0, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
