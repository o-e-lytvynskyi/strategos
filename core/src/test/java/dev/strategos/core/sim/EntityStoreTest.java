package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.shared.Handle;
import org.junit.jupiter.api.Test;

class EntityStoreTest {

    @Test
    void createsSequentialHandlesAtGenerationOne() {
        EntityStore store = new EntityStore();
        assertThat(store.create()).isEqualTo(Handle.of(0, 1));
        assertThat(store.create()).isEqualTo(Handle.of(1, 1));
        assertThat(store.create()).isEqualTo(Handle.of(2, 1));
        assertThat(store.aliveCount()).isEqualTo(3);
        assertThat(store.highWater()).isEqualTo(3);
    }

    @Test
    void destroyBumpsTheGenerationSoTheStaleHandleIsDead() {
        EntityStore store = new EntityStore();
        int first = store.create();
        store.destroy(first);

        assertThat(store.isAlive(first)).isFalse();
        assertThat(store.aliveAt(0)).isFalse();
        assertThat(store.handleAt(0)).isEqualTo(Handle.NONE);
        int reused = store.create();
        assertThat(Handle.index(reused)).isZero();
        assertThat(Handle.generation(reused)).isEqualTo(2);
        assertThat(store.isAlive(first)).as("the stale handle stays dead").isFalse();
        assertThat(store.isAlive(reused)).isTrue();
        assertThatThrownBy(() -> store.destroy(first)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reusesTheLowestFreeIndexFirst() {
        EntityStore store = new EntityStore();
        int[] handles = new int[6];
        for (int i = 0; i < handles.length; i++) {
            handles[i] = store.create();
        }
        store.destroy(handles[4]);
        store.destroy(handles[1]);
        store.destroy(handles[3]);

        assertThat(Handle.index(store.create())).isEqualTo(1);
        assertThat(Handle.index(store.create())).isEqualTo(3);
        assertThat(Handle.index(store.create())).isEqualTo(4);
        assertThat(Handle.index(store.create())).isEqualTo(6);
    }

    @Test
    void noneAndUnknownHandlesAreNotAlive() {
        EntityStore store = new EntityStore();
        store.create();
        assertThat(store.isAlive(Handle.NONE)).isFalse();
        assertThat(store.isAlive(Handle.of(5, 1))).isFalse();
        assertThat(store.isAlive(Handle.of(0, 2))).isFalse();
    }

    @Test
    void slotsAndRestoreRoundTrip() {
        EntityStore store = new EntityStore();
        int a = store.create();
        int b = store.create();
        int c = store.create();
        store.destroy(b);
        store.destroy(store.create()); // index 1 again, so its generation reaches 3

        EntitySlots slots = store.slots();
        assertThat(slots.generations()).containsExactly(1, 3, 1);
        assertThat(slots.freeIndices()).containsExactly(1);

        EntityStore restored = EntityStore.restore(slots, new int[] {a, c});
        assertThat(restored.slots()).isEqualTo(slots);
        assertThat(restored.isAlive(a)).isTrue();
        assertThat(restored.isAlive(c)).isTrue();
        assertThat(restored.aliveCount()).isEqualTo(2);
        assertThat(restored.create()).isEqualTo(store.create()).isEqualTo(Handle.of(1, 3));
    }

    @Test
    void restoreRejectsHandlesThatDoNotFitTheSlots() {
        EntitySlots slots = new EntitySlots(new int[] {1, 2}, new int[] {1});
        assertThatThrownBy(() -> EntityStore.restore(slots, new int[] {Handle.of(0, 2)}))
                .as("wrong generation")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EntityStore.restore(slots, new int[] {Handle.of(1, 2)}))
                .as("a free index")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EntityStore.restore(slots, new int[0]))
                .as("index 0 neither free nor alive")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EntitySlots(new int[] {1, 1, 1}, new int[] {2, 1}))
                .as("free indices must ascend")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void growsPastItsInitialCapacity() {
        EntityStore store = new EntityStore();
        int last = 0;
        for (int i = 0; i < 1000; i++) {
            last = store.create();
        }
        assertThat(Handle.index(last)).isEqualTo(999);
        assertThat(store.aliveCount()).isEqualTo(1000);
        assertThat(store.handleAt(999)).isEqualTo(last);
    }

    @Test
    void slotsThatAreNoTableAreRefusedNamingWhy() {
        assertThatThrownBy(() -> new EntitySlots(new int[Handle.MAX_INDEX + 2], new int[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("a handle can name");
        assertThatThrownBy(() -> new EntitySlots(new int[] {1, Handle.MAX_GENERATION + 1}, new int[0]))
                .hasMessageContaining("generation 4096");
        assertThatThrownBy(() -> new EntitySlots(new int[] {1, 1}, new int[] {2}))
                .hasMessageContaining("free index 2 outside 0..1");
        assertThatThrownBy(() -> new EntitySlots(new int[] {1, 1, 1}, new int[] {2, 1}))
                .hasMessageContaining("must ascend");
        assertThat(EntitySlots.problem(new int[] {1, 2, 1}, new int[] {1})).isNull();
    }
}
