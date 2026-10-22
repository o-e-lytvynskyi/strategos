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
import dev.strategos.core.testkit.VoxelFixtures;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * PATH-06 in L1 (spec §2.1): the tick's agents are journaled as input kind 5, read back from the binary file, and a
 * replay reaches the live hash; they hold for their tick only, so an agent standing in a unit's way changes where it
 * walks and nothing is left of it in the state after it went.
 */
class AgentUpdateTest {
    private static final Agent IN_THE_WAY = new Agent(42, 16.5f, 1, 8.5f, 0, 0, 0.3f);

    @Test
    void journaledAndReadBack() {
        SimHarness h = run(true);

        Journal journal = h.core().journal();
        JournalExport copy =
                JournalFormat.decode(JournalFormat.encode(new JournalExport(journal, h.tick(), h.stateHash())));

        assertThat(updates(journal))
                .hasSize(60)
                .allSatisfy(u -> assertThat(u.agents()).containsExactly(IN_THE_WAY));
        assertThat(updates(copy.journal())).isEqualTo(updates(journal));
        assertThat(ReplayRunner.check(copy.journal(), TemplateSet.builtin(), h.stateHash())
                        .ok())
                .isTrue();
    }

    @Test
    void anAgentChangesTheWalkWhileItIsThere() {
        assertThat(run(true).stateHash()).isNotEqualTo(run(false).stateHash());
    }

    @Test
    void agentsAreBounded() {
        assertThatThrownBy(() -> new Agent(1, Float.NaN, 0, 0, 0, 0, 0.3f))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Agent(1, 0, 0, 0, 0, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Agent(1, 0, 0, 0, 0, 0, Agent.MAX_RADIUS + 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AgentUpdate(Collections.nCopies(AgentUpdate.MAX_AGENTS + 1, IN_THE_WAY)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** A walker crossing a flat field; with {@code agent}, one stands in its way for ticks 5 to 64. */
    private static SimHarness run(boolean agent) {
        SimHarness h = SimHarness.builder().build();
        h.publishAll(VoxelFixtures.flat(32, 32, 8, 1));
        int unit = h.spawn(new FactionSlot(0), new Vec2d(4.5, 8.5), 1, 0)[0];
        if (agent) {
            h.agents(tick -> tick >= 5 && tick < 65 ? List.of(IN_THE_WAY) : List.of());
        }
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(unit), new Vec2d(27.5, 8.5)));
        h.runTicks(150);
        return h;
    }

    private static List<AgentUpdate> updates(Journal journal) {
        List<AgentUpdate> out = new ArrayList<>();
        for (TickInputs tick : journal.nonEmptyTicks()) {
            for (InputEvent event : tick.events()) {
                if (event instanceof AgentUpdate u) {
                    out.add(u);
                }
            }
        }
        return out;
    }
}
