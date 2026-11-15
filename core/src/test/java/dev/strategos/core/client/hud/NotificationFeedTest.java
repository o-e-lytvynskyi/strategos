package dev.strategos.core.client.hud;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.client.hud.NotificationFeed.Entry;
import dev.strategos.core.client.hud.NotificationFeed.Kind;
import dev.strategos.core.shared.Vec2d;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * UI-21.1 in L1: the feed's priority order, its rate limits (under attack once per 10 s for each 32-block area, each
 * economy notice once per 15 s), six visible, a fade after 8 s, and J's target, the newest alert.
 */
class NotificationFeedTest {
    private static boolean offer(NotificationFeed f, Kind kind, long now, Vec2d at, String limitKey) {
        return f.offer(kind, "k." + kind, List.of(), now, at, limitKey);
    }

    @Test
    void priorityOrderIsAlertsPhaseResearchCompleteEconomy() {
        NotificationFeed f = new NotificationFeed();
        offer(f, Kind.ECONOMY, 5, null, "a"); // the newest comes last in this list, the least important first
        offer(f, Kind.BUILT, 4, null, "b");
        offer(f, Kind.TRAINED, 3, null, "t");
        offer(f, Kind.RESEARCH, 2, null, "r");
        offer(f, Kind.PHASE, 1, null, "p");
        offer(f, Kind.WAVE, 0, new Vec2d(1, 1), "");
        offer(f, Kind.UNDER_ATTACK, 0, new Vec2d(1, 1), "");
        // the order of arrival is the reverse of the priority; within one priority the newest comes first
        assertThat(f.visible(6)).extracting(Entry::kind).hasSize(6).first().isIn(Kind.WAVE, Kind.UNDER_ATTACK);
        assertThat(f.visible(6))
                .extracting(e -> e.kind().priority())
                .as("by priority")
                .isSorted();
        assertThat(f.visible(6))
                .extracting(Entry::kind)
                .containsSubsequence(Kind.PHASE, Kind.RESEARCH)
                .doesNotContain(Kind.ECONOMY); // the seventh entry, the least important, is cut
        assertThat(f.visible(6).get(2).kind()).isEqualTo(Kind.PHASE);
        assertThat(f.visible(6).get(3).kind()).isEqualTo(Kind.RESEARCH);
        assertThat(f.visible(6).get(4).kind()).isIn(Kind.TRAINED, Kind.BUILT);
        assertThat(f.visible(6).get(4).kind())
                .as("newest first within a priority")
                .isEqualTo(Kind.BUILT);
    }

    @Test
    void underAttackOncePerTenSecondsForEachArea() {
        NotificationFeed f = new NotificationFeed();
        assertThat(offer(f, Kind.UNDER_ATTACK, 0, new Vec2d(1, 1), "")).isTrue();
        assertThat(offer(f, Kind.UNDER_ATTACK, 50, new Vec2d(30, 31), ""))
                .as("the same 32-block area")
                .isFalse();
        assertThat(offer(f, Kind.UNDER_ATTACK, 60, new Vec2d(40, 1), ""))
                .as("another area")
                .isTrue();
        assertThat(offer(f, Kind.UNDER_ATTACK, 61, new Vec2d(1, -1), ""))
                .as("a negative coordinate is another area, not area 0")
                .isTrue();
        assertThat(offer(f, Kind.UNDER_ATTACK, 199, new Vec2d(2, 2), ""))
                .as("tick 199")
                .isFalse();
        assertThat(offer(f, Kind.UNDER_ATTACK, 200, new Vec2d(2, 2), ""))
                .as("tick 200: ten seconds")
                .isTrue();
        assertThat(f.visible(200)).as("the first has faded").hasSize(3);
    }

    @Test
    void eachEconomyNoticeOncePerFifteenSeconds() {
        NotificationFeed f = new NotificationFeed();
        assertThat(offer(f, Kind.ECONOMY, 0, null, "wood")).isTrue();
        assertThat(offer(f, Kind.ECONOMY, 1, null, "food")).as("another notice").isTrue();
        assertThat(offer(f, Kind.ECONOMY, 299, null, "wood")).isFalse();
        assertThat(offer(f, Kind.ECONOMY, 300, null, "wood")).isTrue();
        assertThat(offer(f, Kind.ECONOMY, 300, null, "food"))
                .as("food's own clock")
                .isFalse();
        assertThat(offer(f, Kind.ECONOMY, 301, null, "food")).isTrue();
        assertThat(offer(f, Kind.RESEARCH, 302, null, "wood"))
                .as("the limit is the kind's, not the key's alone")
                .isTrue();
    }

    @Test
    void sixAreVisibleAndAnAlertDisplacesTheLeastImportant() {
        NotificationFeed f = new NotificationFeed();
        for (int k = 0; k < 8; k++) {
            offer(f, Kind.ECONOMY, k, null, "n" + k);
        }
        assertThat(f.visible(8)).hasSize(NotificationFeed.VISIBLE).hasSize(6);
        assertThat(f.visible(8).get(0).tick()).as("newest first").isEqualTo(7);
        offer(f, Kind.WAVE, 9, new Vec2d(0, 0), "");
        assertThat(f.visible(9)).hasSize(6);
        assertThat(f.visible(9).get(0).kind()).isEqualTo(Kind.WAVE);
    }

    @Test
    void anEntryFadesAfterEightSeconds() {
        NotificationFeed f = new NotificationFeed();
        offer(f, Kind.PHASE, 100, null, "");
        assertThat(f.visible(259)).hasSize(1);
        assertThat(f.visible(260)).as("8 s = 160 ticks").isEmpty();
        Entry e = f.visible(100).get(0);
        assertThat(NotificationFeed.alpha(e, 100)).isEqualTo(1f);
        assertThat(NotificationFeed.alpha(e, 100 + 119)).isEqualTo(1f);
        assertThat(NotificationFeed.alpha(e, 100 + 140)).isEqualTo(0.5f);
        assertThat(NotificationFeed.alpha(e, 100 + 160)).isZero();
        assertThat(NotificationFeed.alpha(e, 100 + 500)).isZero();
    }

    @Test
    void jumpGoesToTheNewestAlertWithAPlace() {
        NotificationFeed f = new NotificationFeed();
        assertThat(f.newestAlert()).isNull();
        offer(f, Kind.RESEARCH, 0, new Vec2d(9, 9), "r");
        assertThat(f.newestAlert()).as("research is no alert").isNull();
        offer(f, Kind.UNDER_ATTACK, 10, new Vec2d(5, 6), "");
        offer(f, Kind.WAVE, 20, new Vec2d(70, 80), "");
        offer(f, Kind.UNDER_ATTACK, 30, null, "");
        offer(f, Kind.PHASE, 40, new Vec2d(1, 1), "p");
        assertThat(f.newestAlert())
                .as("the wave: the newest alert with a place")
                .isEqualTo(new Vec2d(70, 80));
        offer(f, Kind.UNDER_ATTACK, 50, new Vec2d(300, 300), "");
        assertThat(f.newestAlert()).isEqualTo(new Vec2d(300, 300));
    }

    @Test
    void jumpStillWorksAfterTheAlertFaded() {
        NotificationFeed f = new NotificationFeed();
        offer(f, Kind.WAVE, 0, new Vec2d(70, 80), "");
        offer(f, Kind.ECONOMY, 1_000, null, "wood"); // a later entry; the wave is long gone from the list
        assertThat(f.visible(1_000)).extracting(Entry::kind).containsExactly(Kind.ECONOMY);
        assertThat(f.newestAlert()).as("faded or not").isEqualTo(new Vec2d(70, 80));
    }

    @Test
    void clearForgetsEntriesLimitsAndTheAlertPlace() {
        NotificationFeed f = new NotificationFeed();
        offer(f, Kind.UNDER_ATTACK, 0, new Vec2d(1, 1), "");
        offer(f, Kind.ECONOMY, 0, null, "a");
        f.clear();
        assertThat(f.visible(1)).isEmpty();
        assertThat(f.newestAlert()).as("J has no target in a new match").isNull();
        assertThat(offer(f, Kind.UNDER_ATTACK, 1, new Vec2d(1, 1), ""))
                .as("the area's limit is forgotten too")
                .isTrue();
        assertThat(offer(f, Kind.ECONOMY, 1, null, "a")).isTrue();
    }
}
