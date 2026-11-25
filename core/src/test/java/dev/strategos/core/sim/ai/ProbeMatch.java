package dev.strategos.core.sim.ai;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.Scenario;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.CivId;
import dev.strategos.core.sim.ai.HeadlessMatch.Result;
import dev.strategos.core.sim.ai.HeadlessMatch.Watcher;
import dev.strategos.core.testkit.ProbeCiv;
import java.util.List;

/**
 * A seeded match of the probe civilisation (faction 0) against the default (faction 1) on the first skirmish map under
 * Conquest and Fog: the skirmish scenario's flagged entities give the probe its own start kit, the probe plays its own
 * strategy script and the default plays the shipped skirmish script, both at Normal.
 */
final class ProbeMatch {
    static final TemplateSet SET = ProbeCiv.set();
    static final List<CivId> CIVS = List.of(ProbeCiv.ID, CivId.DEFAULT);

    private ProbeMatch() {}

    /** The match of {@code seed} for {@code limit} ticks, {@code watcher} shown each tick. */
    static Result run(long seed, long limit, Watcher watcher) {
        Scenario scenario = Scenario.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString(HeadlessMatch.read("scenarios/first_skirmish.json")))
                .getOrThrow();
        String probe = Levels.resolve(ProbeCiv.script(), "normal");
        String standard = Levels.resolve(HeadlessMatch.read("ai/skirmish.json"), "normal");
        return HeadlessMatch.run(
                HeadlessMatch.Ground.firstSkirmish(),
                scenario,
                seed,
                limit,
                List.of(probe, standard),
                watcher,
                false,
                HeadlessMatch.FOGGED,
                SET,
                CIVS);
    }
}
