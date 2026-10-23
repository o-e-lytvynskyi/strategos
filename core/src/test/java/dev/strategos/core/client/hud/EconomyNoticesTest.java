package dev.strategos.core.client.hud;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.Feedback;
import dev.strategos.core.protocol.NoticeKind;
import dev.strategos.core.protocol.NoticeMsg;
import dev.strategos.core.protocol.RejectReason;
import java.util.List;
import org.junit.jupiter.api.Test;

/** UI-10.2: each economy notification fires once per cause, however many messages, or ticks, the cause lasts. */
class EconomyNoticesTest {
    static final List<String> IDS = List.of("food", "wood", "stone", "iron");

    static Feedback refused(long food, long wood) {
        return new Feedback(
                1, CommandOutcome.REJECTED, RejectReason.NOT_ENOUGH_RESOURCES, 0, 1, List.of(food, wood, 0L, 0L));
    }

    @Test
    void oncePerCause() {
        EconomyNotices n = new EconomyNotices();
        for (long t = 0; t < 300; t++) { // a player clicks "train" every tick for 15 s, without the food
            n.accept(refused(20_000, 0), t, IDS);
            n.accept(new NoticeMsg(NoticeKind.HOUSING_NEEDED, 1 << 20, ""), t, IDS);
            if (t % 7 == 0) {
                n.accept(new NoticeMsg(NoticeKind.IDLE_WORKER, 5, "nothing to gather"), t, IDS);
            }
            n.population(20, 20);
        }
        assertThat(n.fired())
                .extracting(EconomyNotices.Shown::cause)
                .containsExactly(
                        EconomyNotices.Cause.NOT_ENOUGH_RESOURCES,
                        EconomyNotices.Cause.POPULATION_CAPPED,
                        EconomyNotices.Cause.DEPOSIT_EXHAUSTED);
        assertThat(n.fired().get(0).detail()).isEqualTo("food");
        assertThat(n.shown(299)).hasSize(3);
        assertThat(n.shown(299).get(0).count()).as("renewed, not fired again").isGreaterThan(1);

        n.accept(refused(0, 5_000), 300, IDS);
        assertThat(n.fired()).as("another lack is another cause").hasSize(4);
        assertThat(n.fired().get(3).detail()).isEqualTo("wood");

        assertThat(n.shown(300 + EconomyNotices.SHOW_TICKS))
                .extracting(EconomyNotices.Shown::cause)
                .as("the others lapse; the cap stays while it lasts")
                .containsExactly(EconomyNotices.Cause.POPULATION_CAPPED);
        n.accept(refused(20_000, 0), 500, IDS);
        assertThat(n.fired()).as("lapsed, the same lack fires again").hasSize(5);

        n.population(19, 25);
        assertThat(n.shown(501))
                .extracting(EconomyNotices.Shown::cause)
                .doesNotContain(EconomyNotices.Cause.POPULATION_CAPPED);
        n.accept(new NoticeMsg(NoticeKind.HOUSING_NEEDED, 1 << 20, ""), 502, IDS);
        assertThat(n.fired()).as("capped again, it fires again").hasSize(6);
        n.accept(new Feedback(2, CommandOutcome.REJECTED, RejectReason.QUEUE_FULL, 0, 1, List.of()), 503, IDS);
        n.accept(new NoticeMsg(NoticeKind.UNIT_LOST, 3, ""), 503, IDS);
        assertThat(n.fired()).as("not the economy's").hasSize(6);
    }
}
