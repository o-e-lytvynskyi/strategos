package dev.strategos.core.sim.nav;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.MatchId;
import dev.strategos.core.sim.CorePorts;
import dev.strategos.core.sim.InputEvent;
import dev.strategos.core.sim.MatchBounds;
import dev.strategos.core.sim.MatchConfig;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.PageRef;
import dev.strategos.core.sim.TerrainPage;
import dev.strategos.core.sim.TickInputs;
import dev.strategos.core.sim.journal.Journal;
import dev.strategos.core.sim.journal.JournalExport;
import dev.strategos.core.sim.journal.JournalFormat;
import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * PATH-15.3: terrain pages enter a match as journaled inputs, and rebuilding them from the journal's slices gives the
 * published pages: after the binary round trip each page input has its published hash; a replay from the start, and
 * one from the second segment alone, ends with the live grid; the snapshot names every published page's slice.
 */
class TerrainReplayTest {
    private static final int SIZE = 48;
    private static final int LAST = 1_300;

    @Test
    void pagesRebuiltFromJournaledSlicesHaveThePublishedHashes() {
        VoxelWorld world = VoxelFixtures.flat(SIZE, SIZE, 32, 8);
        MatchCore live = core();
        List<TerrainPage> published = new ArrayList<>();
        for (long tick = 1; tick <= LAST; tick++) {
            List<InputEvent> inputs = new ArrayList<>();
            if (tick == 1) {
                for (int pz = 0; pz < 3; pz++) {
                    for (int px = 0; px < 3; px++) {
                        inputs.add(page(world, px, pz));
                    }
                }
            } else if (tick == 50) {
                world.fill(20, 8, 0, 21, 11, 47, VoxelFixtures.SOLID); // a wall across pages (1, 0..2)
                for (int pz = 0; pz < 3; pz++) {
                    inputs.add(page(world, 1, pz));
                }
            } else if (tick == 1_250) {
                world.fill(40, 8, 40, 47, 8, 47, VoxelFixtures.SOLID); // a step in page (2, 2)
                inputs.add(page(world, 2, 2));
            }
            inputs.forEach(e -> published.add((TerrainPage) e));
            live.step(new TickInputs(tick, inputs));
        }
        TreeMap<String, Long> liveGrid = grid(live);

        Journal decoded = JournalFormat.decode(JournalFormat.encode(
                        new JournalExport(live.journal(), live.queries().tick(), live.stateHash())))
                .journal();

        List<TerrainPage> journaled = new ArrayList<>();
        for (TickInputs t : decoded.nonEmptyTicks()) {
            t.events().forEach(e -> journaled.add((TerrainPage) e));
        }
        assertThat(journaled).hasSize(published.size());
        for (int i = 0; i < journaled.size(); i++) {
            assertThat(journaled.get(i).page().hash())
                    .as("page input %d", i)
                    .isEqualTo(published.get(i).page().hash());
            assertThat(journaled.get(i).sliceHash()).isEqualTo(published.get(i).sliceHash());
        }
        assertThat(decoded.segments()).as("the run crosses a segment boundary").hasSize(2);
        assertThat(grid(replay(decoded))).as("replayed from the start").isEqualTo(liveGrid);
        assertThat(grid(replay(decoded.segment(1))))
                .as("replayed from the second segment alone")
                .isEqualTo(liveGrid);
        assertThat(live.snapshot().pages()).extracting(PageRef::slice).containsExactlyElementsOf(sliceHashes(live));
    }

    private static MatchCore core() {
        MatchConfig config =
                new MatchConfig(new MatchId(1), 42, new MatchBounds(SIZE, SIZE, 0, 31, 9.0), 2, MatchRules.DEFAULTS);
        return MatchCore.create(config, TemplateSet.builtin(), CorePorts.of(() -> 0L));
    }

    private static TerrainPage page(VoxelWorld world, int px, int pz) {
        PageCoord coord = new PageCoord(px, pz);
        return TerrainPage.build(coord, world.slice(coord), new PageBuilder(MobilityClass.INFANTRY));
    }

    /** Restores the journal's start with its slices and steps its inputs to its last tick. */
    private static MatchCore replay(Journal journal) {
        MatchCore core = MatchCore.restore(
                journal.start(),
                TemplateSet.builtin(),
                CorePorts.of(() -> 0L),
                journal.segments().get(0)::slice);
        List<TickInputs> inputs = journal.nonEmptyTicks();
        int next = 0;
        for (long tick = journal.startTick() + 1; tick <= journal.lastTick(); tick++) {
            TickInputs in = TickInputs.empty(tick);
            if (next < inputs.size() && inputs.get(next).tick() == tick) {
                in = inputs.get(next++);
            }
            core.step(in);
        }
        return core;
    }

    private static TreeMap<String, Long> grid(MatchCore core) {
        SurfaceGrid grid = core.queries().surfaces();
        TreeMap<String, Long> hashes = new TreeMap<>();
        for (int pz = 0; pz < grid.pagesZ(); pz++) {
            for (int px = 0; px < grid.pagesX(); px++) {
                hashes.put(px + "," + pz, grid.page(px, pz).hash());
            }
        }
        return hashes;
    }

    private static List<Long> sliceHashes(MatchCore core) {
        List<Long> hashes = new ArrayList<>();
        for (PageRef ref : core.queries().pageRefs()) {
            hashes.add(ref.slice());
        }
        return hashes;
    }
}
