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
 * AI-03's {@code battle} block holds the tactics' knobs, all off by default ({@link Battle}); a difficulty is a script
 * with other knobs. AI-04's {@code openings} are drawn from at the first think ({@link Opening}) unless {@code opening}
 * names one (QA-12's pairings). AI-05's
 * {@code level} (resolved into a script by {@link Levels}) holds the pace of a difficulty ({@link Level}); a build
 * step may research a tech or a phase instead of placing a building.
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
        String dropsite,
        Battle battle,
        List<Opening> openings,
        Camp camp,
        Level level,
        String opening) {
    /**
     * A build order step: the building, once the population reaches {@code population}, by {@code near} when it names
     * one: a resource's nearest node, or an own building of that template id; "" for no preference. A research step
     * ({@code {"research": <tech>}}, or {@code {"phase": "town"}} for {@code strategos:phase_town}) names the tech in
     * {@code research} instead and has {@link #NONE} for its building; it is done once the faction has the tech or
     * queues it.
     */
    public record Step(TemplateId building, int population, String near, String research) {
        /** The building of a research step. */
        public static final TemplateId NONE = new TemplateId("strategos:none");

        static final Codec<Step> CODEC = RecordCodecBuilder.create(i -> i.group(
                        TemplateId.CODEC.optionalFieldOf("building", NONE).forGetter(Step::building),
                        Codec.INT.optionalFieldOf("population", 0).forGetter(Step::population),
                        Codec.STRING.optionalFieldOf("near", "").forGetter(Step::near),
                        Codec.STRING.optionalFieldOf("research", "").forGetter(Step::research),
                        Codec.STRING.optionalFieldOf("phase", "").forGetter(step -> ""))
                .apply(
                        i,
                        (building, population, near, research, phase) -> new Step(
                                building, population, near, phase.isEmpty() ? research : "strategos:phase_" + phase)));

        public Step(TemplateId building, int population) {
            this(building, population, "", "");
        }

        public Step(TemplateId building, int population, String near) {
            this(building, population, near, "");
        }

        public Step {
            if (research.isEmpty() == building.equals(NONE)) {
                throw new IllegalArgumentException(
                        "a step places a building or researches a tech, not both or neither");
            }
        }

        /** Whether this step researches a tech. */
        public boolean researches() {
            return !research.isEmpty();
        }
    }

    /**
     * What a difficulty changes of the pace (AI-05, §2.20), the rest of it being in the script itself ({@link Levels}
     * resolves it): the ticks between thinks, the ticks a defence waits after the attack is seen, the most commands one
     * think sends (0 for no limit) and the open handicap. {@link #NONE} is the pace every script had before.
     */
    public record Level(String name, int think, int reaction, int commands, Handicap handicap) {
        public static final Level NONE = new Level("", 20, 0, 0, Handicap.NONE);

        static final Codec<Level> CODEC = RecordCodecBuilder.create(i -> i.group(
                        Codec.STRING.optionalFieldOf("name", "").forGetter(Level::name),
                        Codec.intRange(1, 1_200).optionalFieldOf("think", 20).forGetter(Level::think),
                        Codec.intRange(0, 1_200).optionalFieldOf("reaction", 0).forGetter(Level::reaction),
                        Codec.intRange(0, 1_000).optionalFieldOf("commands", 0).forGetter(Level::commands),
                        Handicap.CODEC
                                .optionalFieldOf("handicap", Handicap.NONE)
                                .forGetter(Level::handicap))
                .apply(i, Level::new));
    }

    /**
     * The open handicap of a level (D-19, the faction modifier source {@code ai_handicap}): gathering rates and unit
     * training times are multiplied by {@code gather} and {@code train}; 1 for none. Never above 1 for gathering nor
     * below 1 for training: a handicap, not a bonus (row 9).
     */
    public record Handicap(double gather, double train) {
        public static final Handicap NONE = new Handicap(1, 1);

        static final Codec<Handicap> CODEC = RecordCodecBuilder.create(i -> i.group(
                        Codec.doubleRange(0.1, 1).optionalFieldOf("gather", 1.0).forGetter(Handicap::gather),
                        Codec.doubleRange(1, 10).optionalFieldOf("train", 1.0).forGetter(Handicap::train))
                .apply(i, Handicap::new));

        /** Whether it changes anything. */
        public boolean any() {
            return gather != 1 || train != 1;
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

    /**
     * The battle tactics' knobs (AI-03, M5 §2.11); each is off at its zero. {@code focus}: whom its soldiers pick
     * ("off", "wounded": the lowest health fraction, or "value": the dearest to train); {@code behind}: how many blocks
     * behind the melee line the archers hold; {@code retreat}: the wave goes home when its strength falls below this
     * fraction of the enemy's near it; {@code replan}: ticks between re-plans of what it trains from what the enemy
     * fields; {@code reach}: blocks round the wave that count as the fight; {@code defence}: the strength (in cost units)
     * credited to an enemy civic centre's arrows when the wave is within reach of it; {@code launch}: a wave leaves only
     * when its strength is at least this fraction of the enemy's army and defence together; {@code spears}: spearmen
     * (the only soldiers that burn a building) kept home out of the field fight until the enemy fields no army, then
     * sent at its civic centre; {@code line}: the share of spearmen, in permille, the training plan never falls below
     * (a spearman short of it is waited for, not replaced by another kind); {@code rams}: how many rams the player keeps
     * (QA-12: an arsenal trains them; once a wave is out they strike the enemy's buildings while the soldiers screen).
     */
    public record Battle(
            String focus,
            int behind,
            double retreat,
            int replan,
            int reach,
            int defence,
            double launch,
            int spears,
            int line,
            int rams) {
        public static final String OFF = "off";
        public static final String WOUNDED = "wounded";
        public static final String VALUE = "value";

        /** Every knob off: M4's military manager. */
        public static final Battle NONE = new Battle(OFF, 0, 0, 0, 28, 0, 0, 0, 250, 0);

        private static final Codec<String> FOCUS =
                Codec.STRING.validate(f -> f.equals(OFF) || f.equals(WOUNDED) || f.equals(VALUE)
                        ? com.mojang.serialization.DataResult.success(f)
                        : com.mojang.serialization.DataResult.error(() -> "no focus '" + f + "'"));

        static final Codec<Battle> CODEC = RecordCodecBuilder.create(i -> i.group(
                        FOCUS.optionalFieldOf("focus", OFF).forGetter(Battle::focus),
                        Codec.intRange(0, 16).optionalFieldOf("behind", 0).forGetter(Battle::behind),
                        Codec.doubleRange(0, 1).optionalFieldOf("retreat", 0.0).forGetter(Battle::retreat),
                        Codec.intRange(0, 72_000).optionalFieldOf("replan", 0).forGetter(Battle::replan),
                        Codec.intRange(8, 64).optionalFieldOf("reach", 28).forGetter(Battle::reach),
                        Codec.intRange(0, 100_000).optionalFieldOf("defence", 0).forGetter(Battle::defence),
                        Codec.doubleRange(0, 10).optionalFieldOf("launch", 0.0).forGetter(Battle::launch),
                        Codec.intRange(0, 64).optionalFieldOf("spears", 0).forGetter(Battle::spears),
                        Codec.intRange(0, 1000).optionalFieldOf("line", 250).forGetter(Battle::line),
                        Codec.intRange(0, 16).optionalFieldOf("rams", 0).forGetter(Battle::rams))
                .apply(i, Battle::new));

        /** Whether any knob of the fight itself is on. */
        public boolean fights() {
            return !focus.equals(OFF) || behind > 0 || retreat > 0;
        }

        /** Whether any knob is on. */
        public boolean enabled() {
            return fights() || replan > 0 || launch > 0 || spears > 0;
        }
    }

    /**
     * An opening (AI-04): a weight it is drawn by, and what it changes of the script: a build order (empty keeps the
     * script's), a worker target (-1 keeps it), the soldier queue (-1 keeps it) and the attack schedule (none keeps it).
     * Rush attacks early with a short build order and few workers, boom grows a large economy first, balanced sits
     * between; the data holds the numbers.
     */
    public record Opening(
            String name,
            int weight,
            List<Step> build,
            int maxWorkers,
            int soldierQueue,
            java.util.Optional<Attack> attack) {
        static final Codec<Opening> CODEC = RecordCodecBuilder.create(i -> i.group(
                        Codec.STRING.fieldOf("name").forGetter(Opening::name),
                        Codec.intRange(1, 1_000).optionalFieldOf("weight", 1).forGetter(Opening::weight),
                        Step.CODEC.listOf().optionalFieldOf("build", List.of()).forGetter(Opening::build),
                        Codec.intRange(0, 200).optionalFieldOf("maxWorkers", -1).forGetter(Opening::maxWorkers),
                        Codec.intRange(0, 20)
                                .optionalFieldOf("soldierQueue", -1)
                                .forGetter(Opening::soldierQueue),
                        Attack.CODEC.optionalFieldOf("attack").forGetter(Opening::attack))
                .apply(i, Opening::new));

        public Opening {
            build = List.copyOf(build);
        }
    }

    /**
     * Where a wave musters (AI-04): at least {@code minDistance} blocks from the base's centre; 0 keeps the attack's own
     * {@code muster} distance on the line to the enemy, as M3 to M5 scripts played.
     */
    public record Camp(int minDistance) {
        public static final Camp DEFAULT = new Camp(40);

        static final Codec<Camp> CODEC = RecordCodecBuilder.create(i -> i.group(Codec.intRange(0, 200)
                        .optionalFieldOf("minDistance", 40)
                        .forGetter(Camp::minDistance))
                .apply(i, Camp::new));
    }

    /** The opening drawn for {@code roll} in {@code 0 .. totalWeight() - 1} by weight, in order; -1 with none. */
    public int pick(int roll) {
        int left = roll;
        for (int k = 0; k < openings.size(); k++) {
            left -= openings.get(k).weight();
            if (left < 0) {
                return k;
            }
        }
        return openings.isEmpty() ? -1 : openings.size() - 1;
    }

    /** The openings' weights together. */
    public int totalWeight() {
        int sum = 0;
        for (Opening o : openings) {
            sum += o.weight();
        }
        return sum;
    }

    /** This script as opening {@code index} changes it; the script itself for -1 or none such. */
    public StrategyScript opened(int index) {
        if (index < 0 || index >= openings.size()) {
            return this;
        }
        Opening o = openings.get(index);
        return new StrategyScript(
                worker,
                o.maxWorkers() >= 0 ? o.maxWorkers() : maxWorkers,
                ratio,
                o.build().isEmpty() ? build : o.build(),
                housingMargin,
                soldier,
                o.soldierQueue() >= 0 ? o.soldierQueue() : soldierQueue,
                o.attack().orElse(attack),
                sites,
                livestock,
                balance,
                reserve,
                dropsite,
                battle,
                openings,
                camp,
                level,
                opening);
    }

    /**
     * The index in {@code openings} of the opening {@link #opening} pins, -1 when the script draws (QA-12 pins each side's
     * opening so a pairing is the same two openings in every seed).
     */
    public int pinned() {
        for (int k = 0; k < openings.size(); k++) {
            if (openings.get(k).name().equals(opening)) {
                return k;
            }
        }
        return -1;
    }

    public StrategyScript {
        if (!opening.isEmpty() && openings.stream().noneMatch(o -> o.name().equals(opening))) {
            throw new IllegalArgumentException("a strategy script pins the opening '" + opening + "', which it lacks");
        }
        openings = List.copyOf(openings);
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
                    Codec.STRING.optionalFieldOf("dropsite", "").forGetter(StrategyScript::dropsite),
                    Battle.CODEC.optionalFieldOf("battle", Battle.NONE).forGetter(StrategyScript::battle),
                    Opening.CODEC
                            .listOf()
                            .optionalFieldOf("openings", List.of())
                            .forGetter(StrategyScript::openings),
                    Tail.CODEC.forGetter(script -> new Tail(script.camp(), script.level(), script.opening())))
            .apply(
                    i,
                    (worker,
                            maxWorkers,
                            ratio,
                            build,
                            margin,
                            soldier,
                            queue,
                            attack,
                            sites,
                            livestock,
                            balance,
                            reserve,
                            dropsite,
                            battle,
                            openings,
                            tail) -> new StrategyScript(
                            worker,
                            maxWorkers,
                            ratio,
                            build,
                            margin,
                            soldier,
                            queue,
                            attack,
                            sites,
                            livestock,
                            balance,
                            reserve,
                            dropsite,
                            battle,
                            openings,
                            tail.camp(),
                            tail.level(),
                            tail.opening())));

    /** The last three fields, grouped: a record codec takes at most 16 fields. */
    private record Tail(Camp camp, Level level, String opening) {
        static final com.mojang.serialization.MapCodec<Tail> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                        Camp.CODEC.optionalFieldOf("camp", Camp.DEFAULT).forGetter(Tail::camp),
                        Level.CODEC.optionalFieldOf("level", Level.NONE).forGetter(Tail::level),
                        Codec.STRING.optionalFieldOf("opening", "").forGetter(Tail::opening))
                .apply(i, Tail::new));
    }

    /** The script in {@code json}; a malformed one throws {@link IllegalArgumentException}. */
    public static StrategyScript parse(String json) {
        return CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .getOrThrow(message -> new IllegalArgumentException("a strategy script: " + message));
    }
}
