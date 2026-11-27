package dev.strategos.mod.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * MATCH-16: a match shows its night as the level's time only when it is alone in its level (D-20); with others there the
 * level's time stays and the client tints; with the rule off nothing is shown.
 */
class MatchSkyTest {
    @Test
    void aLoneMatchOwnsTheWorldTime() {
        assertThat(MatchSky.mode(true, 1)).isEqualTo(MatchSky.Mode.WORLD_TIME);
        assertThat(MatchSky.mode(true, 0)).as("not yet counted").isEqualTo(MatchSky.Mode.WORLD_TIME);
    }

    @Test
    void severalMatchesInOneLevelTintInstead() {
        assertThat(MatchSky.mode(true, 2)).isEqualTo(MatchSky.Mode.TINT);
        assertThat(MatchSky.mode(true, 9)).isEqualTo(MatchSky.Mode.TINT);
    }

    @Test
    void theRuleOffShowsNothingWhateverTheCount() {
        assertThat(MatchSky.mode(false, 1)).isEqualTo(MatchSky.Mode.NONE);
        assertThat(MatchSky.mode(false, 5)).isEqualTo(MatchSky.Mode.NONE);
    }
}
