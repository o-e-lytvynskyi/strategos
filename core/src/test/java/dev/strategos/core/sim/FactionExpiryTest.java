package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.sim.journal.ReplayDriver;
import dev.strategos.core.testkit.SimHarness;
import org.junit.jupiter.api.Test;

class FactionExpiryTest {
    @Test
    void sandboxExpiryIsDurableIdempotentAndReplaysWithoutEndingTheMatch() {
        var h = SimHarness.builder().build();
        h.schedule(1, new FactionExpiry(new FactionSlot(0)));
        h.step();
        assertThat(h.queries().factionDefeated(new FactionSlot(0))).isTrue();
        assertThat(h.queries().factionDefeated(new FactionSlot(1))).isFalse();
        assertThat(h.queries().matchResults()).isNull();
        h.schedule(2, new FactionExpiry(new FactionSlot(0)));
        h.step();
        assertThat(h.events(FactionDefeated.class)).hasSize(1);
        var back = SimHarness.restore(h.core().snapshot(), h.queries().templates());
        assertThat(back.queries().factionDefeated(new FactionSlot(0))).isTrue();
        back.schedule(back.tick() + 1, new FactionExpiry(new FactionSlot(0)));
        back.step();
        assertThat(back.events(FactionDefeated.class)).isEmpty();
        assertThat(back.queries().matchResults()).isNull();
        var replay = ReplayDriver.replay(h.core().journal(), h.queries().templates());
        assertThat(replay.matches()).isTrue();
        assertThat(replay.finalHash()).isEqualTo(h.stateHash());
        assertThat(replay.finalTick()).isEqualTo(h.tick());
    }

    @Test
    void multipleExpiredFactionsHaveCanonicalInputAndOutputOrder() {
        var a = SimHarness.builder().build();
        var b = SimHarness.builder().build();
        a.schedule(1, new FactionExpiry(new FactionSlot(1)));
        a.schedule(1, new FactionExpiry(new FactionSlot(0)));
        b.schedule(1, new FactionExpiry(new FactionSlot(0)));
        b.schedule(1, new FactionExpiry(new FactionSlot(1)));
        a.step();
        b.step();
        assertThat(a.core().journal().nonEmptyTicks())
                .isEqualTo(b.core().journal().nonEmptyTicks());
        assertThat(a.events(FactionDefeated.class))
                .extracting(e -> e.faction().value())
                .containsExactly(0, 1);
        assertThat(a.stateHash()).isEqualTo(b.stateHash());
    }

    @Test
    void anAbsentMatchFactionIsNotSilentlyMaskedIntoAnotherFaction() {
        var h = SimHarness.builder().build();
        h.schedule(1, new FactionExpiry(new FactionSlot(15)));
        assertThatThrownBy(h::step).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("expiry faction");
    }
}
