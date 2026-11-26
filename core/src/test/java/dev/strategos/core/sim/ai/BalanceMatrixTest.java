package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.CivId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** QA-16: the matrix's pairings, its flag rule, its reproduction from seeds and its balance sheet diff. */
final class BalanceMatrixTest {
    private static final CivId LEAN = new CivId("t:lean");

    private static BalanceMatrix.Row row(String pairing, long seed, int winner, int aSlot) {
        return new BalanceMatrix.Row(
                pairing, seed, aSlot, winner, winner == aSlot, 1_000, List.of(-1L, -1L), List.of(-1L, -1L), "");
    }

    /** {@code seeds} rows of {@code p}: start 0 wins {@code zero} of them, start 1 the rest; A sits at start 0 on even seeds. */
    private static List<BalanceMatrix.Row> rows(String p, int seeds, int zero) {
        List<BalanceMatrix.Row> out = new ArrayList<>();
        for (long seed = 1; seed <= seeds; seed++) {
            out.add(row(p, seed, seed <= zero ? 0 : 1, seed % 2 == 0 ? 0 : 1));
        }
        return out;
    }

    @Test
    void oneCivilisationGivesThreeMirrors() {
        assertThat(BalanceMatrix.pairings(List.of(CivId.DEFAULT)))
                .extracting(BalanceMatrix.Pairing::name)
                .containsExactly("default-default-easy", "default-default-normal", "default-default-hard");
        assertThat(BalanceMatrix.pairings(List.of(CivId.DEFAULT))).allMatch(BalanceMatrix.Pairing::mirror);
    }

    @Test
    void twoCivilisationsGiveEveryOrderedPairAtEveryLevel() {
        List<BalanceMatrix.Pairing> p = BalanceMatrix.pairings(List.of(CivId.DEFAULT, LEAN));
        assertThat(p).hasSize(12);
        assertThat(p).extracting(BalanceMatrix.Pairing::name).contains("default-lean-hard", "lean-default-easy");
        assertThat(p.stream().filter(BalanceMatrix.Pairing::mirror)).hasSize(6);
    }

    @Test
    void theBuiltinSetsCivilisationsAreTheMatrixs() {
        assertThat(BalanceMatrix.civsOf(TemplateSet.builtin())).containsExactly(CivId.DEFAULT);
    }

    @Test
    void aMirrorIsFlaggedOutsideTheBandPerStartSlot() {
        BalanceMatrix.Pairing m = BalanceMatrix.pairings(List.of(CivId.DEFAULT)).get(0);
        assertThat(BalanceMatrix.bandLow(200)).isEqualTo(80);
        assertThat(BalanceMatrix.bandHigh(200)).isEqualTo(120);
        assertThat(BalanceMatrix.flags(m, rows(m.name(), 200, 100), 200)).isEmpty();
        assertThat(BalanceMatrix.flags(m, rows(m.name(), 200, 80), 200)).isEmpty(); // 80 and 120
        assertThat(BalanceMatrix.flags(m, rows(m.name(), 200, 120), 200)).isEmpty();
        assertThat(BalanceMatrix.flags(m, rows(m.name(), 200, 121), 200)).hasSize(2); // 121 and 79
        assertThat(BalanceMatrix.flags(m, rows(m.name(), 200, 79), 200)).hasSize(2);
        assertThat(BalanceMatrix.flags(m, rows(m.name(), 200, 121), 200)).anyMatch(f -> f.contains("start 0 won 121"));
    }

    @Test
    void aNonMirrorIsFlaggedOutsideTheBandPerSide() {
        BalanceMatrix.Pairing p =
                BalanceMatrix.pairings(List.of(CivId.DEFAULT, LEAN)).get(3); // default-lean-easy
        assertThat(p.mirror()).isFalse();
        List<BalanceMatrix.Row> rows = new ArrayList<>();
        for (long seed = 1; seed <= 200; seed++) {
            int aSlot = seed % 2 == 0 ? 0 : 1;
            rows.add(row(p.name(), seed, seed <= 130 ? aSlot : 1 - aSlot, aSlot)); // A wins 130
        }
        assertThat(BalanceMatrix.flags(p, rows, 200))
                .anyMatch(f -> f.contains("default as side A won 130"))
                .anyMatch(f -> f.contains("lean as side B won 70"));
    }

    @Test
    void theBandScalesWithTheSeeds() {
        assertThat(BalanceMatrix.bandLow(20)).isEqualTo(8);
        assertThat(BalanceMatrix.bandHigh(20)).isEqualTo(12);
    }

    @Test
    void aFailedMatchIsFlagged() {
        BalanceMatrix.Pairing m = BalanceMatrix.pairings(List.of(CivId.DEFAULT)).get(0);
        List<BalanceMatrix.Row> rows = rows(m.name(), 200, 100);
        rows.set(
                0,
                new BalanceMatrix.Row(m.name(), 1, 1, -1, false, 5, List.of(-1L, -1L), List.of(-1L, -1L), "stalled"));
        assertThat(BalanceMatrix.flags(m, rows, 200)).anyMatch(f -> f.contains("seed 1: stalled"));
    }

    @Test
    void theSameSmallMatrixTwiceGivesIdenticalRows(@TempDir Path dir) throws Exception {
        TemplateSet set = TemplateSet.builtin();
        List<BalanceMatrix.Pairing> p =
                BalanceMatrix.pairings(BalanceMatrix.civsOf(set)).subList(0, 1);
        List<BalanceMatrix.Row> first = BalanceMatrix.playAll(p, 2, 1_800, 2, set, dir);
        List<BalanceMatrix.Row> second = BalanceMatrix.playAll(p, 2, 1_800, 1, set, dir);
        assertThat(first).hasSize(2).isEqualTo(second);
        assertThat(first).allMatch(r -> r.ticks() == 1_800 && !r.ended()); // unfinished, counted, not dropped
        assertThat(BalanceMatrix.markdown(p, first, 2, 1_800, List.of(), null))
                .contains("| 2 |")
                .contains("no previous run");
    }

    @Test
    void theSheetDiffListsChangedRowsOnly() throws Exception {
        String head = "template,path,value,source,provenance\n";
        String before = head + "a,Cost/wood,50,\"set in a\",\"why, with a comma\"\n" + "b,Health/max,100,s,p\n"
                + "c,X/y,1,s,p\n";
        String after = head + "a,Cost/wood,60,\"set in a\",\"why, with a comma\"\n" + "b,Health/max,100,s,p\n"
                + "d,Z/w,2,s,p\n";
        assertThat(BalanceMatrix.sheetDiff(before, after))
                .containsExactly("a | Cost/wood: 50 -> 60", "c | X/y: 1 -> (removed)", "d | Z/w: (new) -> 2");
        assertThat(BalanceMatrix.sheetDiff(before, before)).isEmpty();
        assertThat(BalanceMatrix.markdown(List.of(), List.of(), 200, 1, List.of(), List.of()))
                .contains("no changed rows");
        assertThat(Files.exists(Path.of("../docs/balance/sheet.csv"))).isTrue(); // the file the task diffs
    }
}
