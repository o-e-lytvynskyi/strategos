package dev.strategos.core.client;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.client.HintBook.Hint;
import dev.strategos.core.protocol.FactionState;
import java.util.List;
import org.junit.jupiter.api.Test;

/** UI-14.1 in L1: each hint fires once per profile under its condition, one at a time, and a dismissal is kept. */
class HintBookTest {
    private static FactionState state(int idle, int used, int cap) {
        return new FactionState(1, 0, 10, List.of(0L), List.of(0L), List.of(0L), used, cap, idle, List.of(0));
    }

    @Test
    void nothingShowsWithoutItsCondition() {
        HintBook b = new HintBook(List.of());
        assertThat(b.current(false, null)).isNull();
        assertThat(b.current(false, state(0, 5, 20))).isNull();
        assertThat(b.current(false, state(0, 17, 20))).as("three below the cap").isNull();
    }

    @Test
    void theFirstSelectionHintsHowToOrder() {
        HintBook b = new HintBook(List.of());
        assertThat(b.current(true, null)).isEqualTo(Hint.FIRST_ORDER);
    }

    @Test
    void theFirstIdleWorkerHintsTheIdleButton() {
        HintBook b = new HintBook(List.of());
        assertThat(b.current(false, state(1, 5, 20))).isEqualTo(Hint.IDLE_WORKER);
    }

    @Test
    void twoBelowTheCapHintsAHouse() {
        HintBook b = new HintBook(List.of());
        assertThat(b.current(false, state(0, 18, 20))).isEqualTo(Hint.BUILD_HOUSE);
        assertThat(new HintBook(List.of()).current(false, state(0, 19, 20))).isEqualTo(Hint.BUILD_HOUSE);
        assertThat(new HintBook(List.of()).current(false, state(0, 0, 0)))
                .as("no cap known yet")
                .isNull();
    }

    @Test
    void oneAtATimeAndItStaysUntilDismissed() {
        HintBook b = new HintBook(List.of());
        FactionState all = state(2, 19, 20);
        assertThat(b.current(true, all)).isEqualTo(Hint.FIRST_ORDER);
        assertThat(b.current(false, all))
                .as("still showing, its condition gone")
                .isEqualTo(Hint.FIRST_ORDER);
        assertThat(b.dismiss()).isTrue();
        assertThat(b.current(false, all)).isEqualTo(Hint.IDLE_WORKER);
        assertThat(b.dismiss()).isTrue();
        assertThat(b.current(false, all)).isEqualTo(Hint.BUILD_HOUSE);
        assertThat(b.dismiss()).isTrue();
        assertThat(b.current(true, all)).as("all seen").isNull();
        assertThat(b.dismiss()).as("nothing showing").isFalse();
    }

    @Test
    void aDismissedHintNeverFiresAgainNotEvenAfterAReload() {
        HintBook b = new HintBook(List.of());
        assertThat(b.current(true, null)).isEqualTo(Hint.FIRST_ORDER);
        b.dismiss();
        assertThat(b.seen()).containsExactly("first_order");
        assertThat(b.current(true, null)).isNull();

        HintBook reloaded = new HintBook(b.seen()); // what strategos-client.toml keeps
        assertThat(reloaded.current(true, null))
                .as("a new session, the same profile")
                .isNull();
        assertThat(reloaded.current(false, state(1, 5, 20))).isEqualTo(Hint.IDLE_WORKER);
        reloaded.dismiss();
        assertThat(reloaded.seen()).containsExactly("first_order", "idle_worker");
        HintBook third = new HintBook(reloaded.seen());
        assertThat(third.current(true, state(1, 19, 20))).isEqualTo(Hint.BUILD_HOUSE);
    }

    @Test
    void anUnknownSeenIdIsKeptAndIgnored() {
        HintBook b = new HintBook(List.of("from_a_newer_version"));
        assertThat(b.current(true, null)).isEqualTo(Hint.FIRST_ORDER);
        b.dismiss();
        assertThat(b.seen()).containsExactly("first_order", "from_a_newer_version");
    }

    @Test
    void idsAndKeysAreStable() {
        assertThat(Hint.values()).extracting(Hint::id).containsExactly("first_order", "idle_worker", "build_house");
        assertThat(Hint.BUILD_HOUSE.key()).isEqualTo("strategos.hint.build_house");
    }
}
