package dev.strategos.mod.client.hud;

import dev.strategos.core.client.hud.EconomyNotices;
import dev.strategos.core.client.hud.NotificationFeed;
import dev.strategos.core.content.TechDef;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.protocol.FeedbackMsg;
import dev.strategos.core.protocol.MatchJoin;
import dev.strategos.core.protocol.MatchNews;
import dev.strategos.core.protocol.NoticeMsg;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.mod.client.StrategosClient;
import dev.strategos.mod.client.audio.AlertSounds;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The HUD's news: the server's feedback and the model's counters of alerts, waves and phases turned into the economy
 * notices (UI-10) and the notification feed (UI-21), with their sounds. Split out of {@link RtsHud} in M6 T1. Client
 * thread only; every method takes the HUD's tick.
 */
final class HudNews {
    private final EconomyNotices notices = new EconomyNotices();
    private final NotificationFeed feed = new NotificationFeed();
    private int alertsHeard;
    private int wavesHeard;
    private int phasesHeard;
    private int clearsSeen;
    private long economySeen = -1;
    private long alertAt = -1_000_000;
    private @Nullable AlertSounds alertSounds;

    EconomyNotices notices() {
        return notices;
    }

    NotificationFeed feed() {
        return feed;
    }

    /** The tick the latest alert was heard at; far in the past when there was none. */
    long alertAt() {
        return alertAt;
    }

    /** The model's counters against what was heard: each new wave, alert or phase becomes a feed entry. */
    void poll(long clientTicks) {
        if (StrategosClient.model().clears() != clearsSeen) { // the model forgot a match: its news counts restart
            clearsSeen = StrategosClient.model().clears();
            wavesHeard = 0;
            alertsHeard = 0;
            phasesHeard = 0;
            feed.clear();
        }
        int announced = StrategosClient.model().waves();
        MatchNews wave = StrategosClient.model().wave();
        MatchJoin joined = StrategosClient.match();
        if (announced > wavesHeard) { // AI-17: an enemy's wave, for those it comes for
            wavesHeard = announced;
            if (wave != null
                    && joined != null
                    && wave.faction() != joined.slot().value()) {
                offer(
                        clientTicks,
                        NotificationFeed.Kind.WAVE,
                        "strategos.feed.wave",
                        List.of(),
                        place(joined, wave),
                        "");
            }
        }
        int heard = StrategosClient.model().alerts();
        if (heard > alertsHeard) { // UI-12: a new alert, rate-limited per area by the feed
            alertsHeard = heard;
            alertAt = clientTicks;
            MatchNews alert = StrategosClient.model().alert();
            offer(
                    clientTicks,
                    NotificationFeed.Kind.UNDER_ATTACK,
                    "strategos.feed.under_attack",
                    List.of(),
                    alert == null || joined == null ? null : place(joined, alert),
                    "");
        }
        int phases = StrategosClient.model().phases();
        MatchNews phase = StrategosClient.model().phase();
        if (phases > phasesHeard) { // TECH-02's notice, to every participant
            phasesHeard = phases;
            if (phase != null && joined != null) {
                boolean own = phase.faction() == joined.slot().value();
                offer(
                        clientTicks,
                        NotificationFeed.Kind.PHASE,
                        own ? "strategos.feed.phase.own" : "strategos.feed.phase.enemy",
                        List.of("strategos.phase." + phase.ticks()),
                        null,
                        phase.faction() + ":" + phase.ticks());
            }
        }
    }

    /** A feedback message from the server: the economy's become notices (UI-10). */
    void feedback(FeedbackMsg msg, long clientTicks) {
        notices.accept(msg, clientTicks, StrategosClient.resources());
        List<EconomyNotices.Shown> fired = notices.fired();
        if (!fired.isEmpty() && fired.getLast().last() > economySeen) {
            EconomyNotices.Shown s = fired.getLast();
            economySeen = s.last();
            offer(
                    clientTicks,
                    NotificationFeed.Kind.ECONOMY,
                    economyKey(s),
                    List.of(s.detail()),
                    null,
                    s.cause() + s.detail());
        }
        if (msg instanceof NoticeMsg n) {
            TemplateSet templates = StrategosClient.templates();
            switch (n.kind()) {
                case TRAINED ->
                    offer(
                            clientTicks,
                            NotificationFeed.Kind.TRAINED,
                            "strategos.feed.trained",
                            List.of(templateName(templates, n.detail())),
                            null,
                            n.detail());
                case BUILT ->
                    offer(
                            clientTicks,
                            NotificationFeed.Kind.BUILT,
                            "strategos.feed.built",
                            List.of(templateName(templates, n.detail())),
                            null,
                            n.detail());
                case RESEARCHED -> {
                    TechDef tech = templates.tech(new TechId(n.detail()));
                    offer(
                            clientTicks,
                            NotificationFeed.Kind.RESEARCH,
                            "strategos.feed.researched",
                            List.of(tech == null ? n.detail() : tech.name()),
                            null,
                            n.detail());
                }
                default -> {}
            }
        }
    }

    private void offer(
            long clientTicks,
            NotificationFeed.Kind kind,
            String key,
            List<String> args,
            @Nullable Vec2d at,
            String limitKey) {
        if (feed.offer(kind, key, args, clientTicks, at, limitKey)) {
            if (alertSounds == null) {
                alertSounds = new AlertSounds(StrategosClient.sounds());
            }
            alertSounds.play(kind, clientTicks);
        }
    }

    private static Vec2d place(MatchJoin join, MatchNews news) {
        return new Vec2d(join.originX() + news.x(), join.originZ() + news.z());
    }

    private static String templateName(TemplateSet templates, String id) {
        UnitTemplate t = templates.byId(new TemplateId(id));
        return t == null ? id : t.name();
    }

    private static String economyKey(EconomyNotices.Shown s) {
        return switch (s.cause()) {
            case NOT_ENOUGH_RESOURCES -> "strategos.feed.not_enough";
            case POPULATION_CAPPED -> "strategos.feed.population_capped";
            case DEPOSIT_EXHAUSTED -> "strategos.feed.worker_idle";
        };
    }
}
