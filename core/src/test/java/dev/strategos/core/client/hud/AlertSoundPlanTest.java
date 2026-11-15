package dev.strategos.core.client.hud;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.client.hud.AlertSoundPlan.Category;
import dev.strategos.core.client.hud.AlertSoundPlan.Cue;
import dev.strategos.core.client.hud.NotificationFeed.Kind;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** UI-21.2 in L1: each alert maps to one sound under its category, and a sound plays at most once per 3 s. */
class AlertSoundPlanTest {
    @Test
    void eachKindMapsToItsOwnSoundAndCategory() {
        assertThat(AlertSoundPlan.cue(Kind.UNDER_ATTACK))
                .isEqualTo(new Cue("minecraft:block.bell.use", Category.HOSTILE, 1.0F, 0.8F));
        assertThat(AlertSoundPlan.cue(Kind.WAVE))
                .isEqualTo(new Cue("minecraft:event.raid.horn", Category.HOSTILE, 1.0F, 1.0F));
        assertThat(AlertSoundPlan.cue(Kind.PHASE).sound()).isEqualTo("minecraft:ui.toast.challenge_complete");
        assertThat(AlertSoundPlan.cue(Kind.RESEARCH).sound()).isEqualTo("minecraft:entity.player.levelup");
        assertThat(AlertSoundPlan.cue(Kind.TRAINED).sound()).isEqualTo("minecraft:entity.villager.celebrate");
        assertThat(AlertSoundPlan.cue(Kind.BUILT).sound()).isEqualTo("minecraft:block.anvil.use");
        assertThat(AlertSoundPlan.cue(Kind.ECONOMY))
                .as("the economy's notices are silent")
                .isNull();

        List<String> sounds = new ArrayList<>();
        for (Kind k : Kind.values()) {
            Cue c = AlertSoundPlan.cue(k);
            if (c != null) {
                sounds.add(c.sound());
                assertThat(c.category()).as(k.name()).isEqualTo(k.alert() ? Category.HOSTILE : Category.NEUTRAL);
                assertThat(c.volume()).isBetween(0.01F, 1.0F);
            }
        }
        assertThat(sounds).as("one sound each").doesNotHaveDuplicates().hasSize(6);
    }

    @Test
    void aSoundPlaysOncePerThreeSecondsEachOnItsOwnClock() {
        AlertSoundPlan p = new AlertSoundPlan();
        assertThat(p.next(Kind.UNDER_ATTACK, 0)).isNotNull();
        assertThat(p.next(Kind.UNDER_ATTACK, 59)).isNull();
        assertThat(p.next(Kind.WAVE, 10)).as("another sound, its own clock").isNotNull();
        assertThat(p.next(Kind.UNDER_ATTACK, 60)).as("3 s = 60 ticks").isNotNull();
        assertThat(p.next(Kind.UNDER_ATTACK, 61))
                .as("a refused try does not extend the wait")
                .isNull();
        assertThat(p.next(Kind.ECONOMY, 62)).isNull();
        assertThat(p.next(Kind.ECONOMY, 500)).isNull();
    }
}
