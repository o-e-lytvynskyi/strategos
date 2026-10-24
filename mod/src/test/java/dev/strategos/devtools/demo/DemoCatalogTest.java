package dev.strategos.devtools.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The demo's catalog: well-formed chapters, and the selection {@code -Pdemo} and {@code -Pdemo.skip} make. */
class DemoCatalogTest {
    private static final List<DemoChapter> ALL = DemoCatalog.CHAPTERS;

    @Test
    void everyChapterIsWellFormed() {
        Set<String> ids = new HashSet<>();
        for (DemoChapter c : ALL) {
            assertThat(c.id()).matches("[a-z][a-z0-9-]*");
            assertThat(ids.add(c.id())).as("a second chapter %s", c.id()).isTrue();
            assertThat(c.version()).isPositive();
            assertThat(c.title()).isNotBlank().hasSizeLessThanOrEqualTo(40);
            assertThat(c.groups()).isNotEmpty();
            assertThat(c.groups().getFirst()).as("%s's milestone", c.id()).matches("m[0-9]+");
            DemoScript script = new DemoScript();
            c.script(script); // builds steps only: nothing runs before the director does
            assertThat(script.steps).as("%s's steps", c.id()).isNotEmpty();
        }
        for (DemoChapter c : ALL) {
            assertThat(c.groups()).as("%s's groups", c.id()).doesNotContainAnyElementsOf(ids);
        }
    }

    @Test
    void aSelectionTakesIdsAndGroupsInTheCatalogsOrder() {
        assertThat(DemoCatalog.pick(ALL, "all", "", Set.of())).isEqualTo(ALL);
        assertThat(ids(DemoCatalog.pick(ALL, "melee, crowd", "", Set.of()))).containsExactly("crowd", "melee");
        assertThat(DemoCatalog.pick(ALL, "m4", "", Set.of()))
                .isNotEmpty()
                .allMatch(c -> c.groups().contains("m4"));
        assertThat(ids(DemoCatalog.pick(ALL, "m3,crowd", "economy", Set.of()))).containsExactly("crowd");
    }

    @Test
    void newLeavesOutWhatWasSeenAtItsVersion() {
        DemoChapter crowd = ALL.getFirst();
        Set<String> seen = Set.of(
                DemoCatalog.key(crowd), ALL.get(1).id() + " " + (ALL.get(1).version() - 1));
        List<DemoChapter> fresh = DemoCatalog.pick(ALL, "new", "", seen);
        assertThat(fresh).doesNotContain(crowd).contains(ALL.get(1)).hasSize(ALL.size() - 1);
    }

    @Test
    void anUnknownNameIsRefused() {
        assertThatThrownBy(() -> DemoCatalog.pick(ALL, "crowds", "", Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("crowds");
        assertThatThrownBy(() -> DemoCatalog.pick(ALL, "all", "nothing", Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theSeenFileKeepsEachChapterOnce(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("sub").resolve("seen");
        DemoCatalog.markSeen(file, ALL.getFirst());
        DemoCatalog.markSeen(file, ALL.getFirst());
        DemoCatalog.markSeen(file, ALL.get(1));
        assertThat(Files.readAllLines(file))
                .containsExactly(DemoCatalog.key(ALL.getFirst()), DemoCatalog.key(ALL.get(1)));
        assertThat(DemoCatalog.seen(file)).hasSize(2);
    }

    private static List<String> ids(List<DemoChapter> chapters) {
        return chapters.stream().map(DemoChapter::id).toList();
    }
}
