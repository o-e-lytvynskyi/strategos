package dev.strategos.core.sim.ai;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.TemplateId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A scripted computer player's strategy (AI-17, {@code data/<ns>/strategos/ai/<profile>.json}): how many workers it
 * keeps and in what ratio per resource; a build order, each step started once its population is reached; a house
 * whenever the free population falls to the margin; spearmen trained continuously, a few queued; and an attack
 * schedule: the first wave at {@code first} seconds, the next every {@code every}, each of at least {@code min}
 * spearmen, mustered {@code muster} blocks toward the enemy and announced {@code announce} seconds before it leaves.
 * {@code sites} are building places as offsets from its civic centre, mirrored for the second start slot; with none, the
 * player finds its own ({@link SiteFinder}, AI-02). AI-02's options, each off by default so M3's script plays as it did:
 * {@code livestock} sends gatherers to animals too, {@code balance} moves gatherers from a resource above its share of
 * the ratio to one below it, {@code reserve} keeps the stock for the build order's next building before soldiers,
 * {@code dropsite} names the building put up by a resource's nearest node once every own dropsite of it lies far from
 * there; a step's {@code near} puts its building by the nearest node of a resource or by an own building of a template.
 */
public record StrategyScript(
        String worker,
        int maxWorkers,
        Map<String, Integer> ratio,
        List<Step> build,
        int housingMargin,
        String soldier,
        int soldierQueue,
        Attack attack,
        List<List<Integer>> sites,
        boolean livestock,
        boolean balance,
        boolean reserve,
        String dropsite) {
    /**
     * A build order step: the building, once the population reaches {@code population}, by {@code near} when it names
     * one: a resource's nearest node, or an own building of that template id; "" for no preference.
     */
    public record Step(TemplateId building, int population, String near) {
        static final Codec<Step> CODEC = RecordCodecBuilder.create(i -> i.group(
                        TemplateId.CODEC.fieldOf("building").forGetter(Step::building),
                        Codec.INT.optionalFieldOf("population", 0).forGetter(Step::population),
                        Codec.STRING.optionalFieldOf("near", "").forGetter(Step::near))
                .apply(i, Step::new));

        public Step(TemplateId building, int population) {
            this(building, population, "");
        }
    }

    /** The attack schedule, in seconds and blocks. */
    public record Attack(int first, int every, int min, int muster, int announce) {
        static final Codec<Attack> CODEC = RecordCodecBuilder.create(i -> i.group(
                        Codec.INT.fieldOf("first").forGetter(Attack::first),
                        Codec.INT.fieldOf("every").forGetter(Attack::every),
                        Codec.INT.optionalFieldOf("min", 4).forGetter(Attack::min),
                        Codec.INT.optionalFieldOf("muster", 24).forGetter(Attack::muster),
                        Codec.intRange(30, 600).optionalFieldOf("announce", 30).forGetter(Attack::announce))
                .apply(i, Attack::new));
    }

    public StrategyScript {
        ratio = Collections.unmodifiableSortedMap(new TreeMap<>(ratio));
        build = List.copyOf(build);
        sites = sites.stream().map(List::copyOf).toList();
    }

    public static final Codec<StrategyScript> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.STRING
                            .optionalFieldOf("worker", "strategos:unit/worker")
                            .forGetter(StrategyScript::worker),
                    Codec.INT.optionalFieldOf("maxWorkers", 20).forGetter(StrategyScript::maxWorkers),
                    Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("ratio").forGetter(StrategyScript::ratio),
                    Step.CODEC.listOf().fieldOf("build").forGetter(StrategyScript::build),
                    Codec.INT.optionalFieldOf("housingMargin", 2).forGetter(StrategyScript::housingMargin),
                    Codec.STRING
                            .optionalFieldOf("soldier", "strategos:unit/infantry")
                            .forGetter(StrategyScript::soldier),
                    Codec.INT.optionalFieldOf("soldierQueue", 2).forGetter(StrategyScript::soldierQueue),
                    Attack.CODEC.fieldOf("attack").forGetter(StrategyScript::attack),
                    Codec.INT
                            .listOf()
                            .listOf()
                            .optionalFieldOf("sites", List.of())
                            .forGetter(StrategyScript::sites),
                    Codec.BOOL.optionalFieldOf("livestock", false).forGetter(StrategyScript::livestock),
                    Codec.BOOL.optionalFieldOf("balance", false).forGetter(StrategyScript::balance),
                    Codec.BOOL.optionalFieldOf("reserve", false).forGetter(StrategyScript::reserve),
                    Codec.STRING.optionalFieldOf("dropsite", "").forGetter(StrategyScript::dropsite))
            .apply(i, StrategyScript::new));

    /** The script in {@code json}; a malformed one throws {@link IllegalArgumentException}. */
    public static StrategyScript parse(String json) {
        return CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .getOrThrow(message -> new IllegalArgumentException("a strategy script: " + message));
    }
}
