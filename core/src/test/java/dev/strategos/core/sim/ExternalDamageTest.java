package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.journal.Journal;
import dev.strategos.core.sim.journal.JournalExport;
import dev.strategos.core.sim.journal.JournalFormat;
import dev.strategos.core.testkit.ReplayRunner;
import dev.strategos.core.testkit.SimHarness;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * UNIT-09.2 in L1 (04 §6.7): damage a body took enters the tick's inputs after the commands, in the order it came, is
 * journaled and read back from the binary file, and changes no state until CMB-01 applies it.
 */
class ExternalDamageTest {
    @Test
    void journaledInOrderAndReadBack() {
        SimHarness h = run(true);

        Journal journal = h.core().journal();
        JournalExport export = new JournalExport(journal, h.tick(), h.stateHash());
        JournalExport copy = JournalFormat.decode(JournalFormat.encode(export));

        int first = h.queries().unitHandles()[0];
        assertThat(damage(journal)).containsExactly(arrow(first), blow(first));
        assertThat(damage(copy.journal())).isEqualTo(damage(journal));
        assertThat(ReplayRunner.check(copy.journal(), TemplateSet.builtin(), h.stateHash())
                        .ok())
                .isTrue();
    }

    @Test
    void changesNoStateYet() {
        assertThat(run(true).stateHash()).isEqualTo(run(false).stateHash());
    }

    @Test
    void anAmountMustBeFinite() {
        assertThatThrownBy(() ->
                        new ExternalDamage(1, ExternalDamage.Kind.HACK, Float.NaN, ExternalDamage.SourceKind.NONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExternalDamage(
                        1, ExternalDamage.Kind.HACK, Float.POSITIVE_INFINITY, ExternalDamage.SourceKind.NONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExternalDamage(1, ExternalDamage.Kind.HACK, -1, ExternalDamage.SourceKind.NONE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Three walkers ordered across the field; with {@code hurt}, a blow and an arrow on the first, tick 10. */
    private static SimHarness run(boolean hurt) {
        SimHarness h = SimHarness.builder().build();
        int[] units = h.spawn(new FactionSlot(0), new Vec2d(8, 8), 3, 1.0);
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(units), new Vec2d(24, 20)));
        h.runTicks(8);
        if (hurt) {
            h.damage(arrow(units[0]));
            h.damage(blow(units[0]));
            h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(units[1]), new Vec2d(20, 24)));
        } else {
            h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(units[1]), new Vec2d(20, 24)));
        }
        h.runTicks(40);
        return h;
    }

    private static ExternalDamage arrow(int unit) {
        return new ExternalDamage(unit, ExternalDamage.Kind.PIERCE, 6, ExternalDamage.SourceKind.PLAYER);
    }

    private static ExternalDamage blow(int unit) {
        return new ExternalDamage(unit, ExternalDamage.Kind.HACK, 0.25f, ExternalDamage.SourceKind.PLAYER);
    }

    private static List<ExternalDamage> damage(Journal journal) {
        List<ExternalDamage> out = new ArrayList<>();
        for (TickInputs tick : journal.nonEmptyTicks()) {
            for (InputEvent event : tick.events()) {
                if (event instanceof ExternalDamage d) {
                    out.add(d);
                }
            }
        }
        return out;
    }
}
