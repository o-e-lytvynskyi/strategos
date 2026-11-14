package dev.strategos.core.sim.ai;

import dev.strategos.core.shared.Rng;
import dev.strategos.core.sim.Streams;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A computer player (AI-01, 04 §14): its faction, its profile and strategy, its memory, and its modules, which think
 * in order (economy, production, building, military) within one budget. It counts the commands it sends and the tick
 * of the last, which the headless runner's watchdog reads.
 */
public final class ComputerPlayer {
    private final int faction;
    private final String profile;
    private final String script;
    private final StrategyScript strategy;
    private final AiMemory memory = new AiMemory();
    private @Nullable List<AiModule> modules;
    private int lastSpent;

    /** A scripted player of {@code faction}; a malformed {@code script} throws {@link IllegalArgumentException}. */
    public ComputerPlayer(int faction, String profile, String script) {
        this.faction = faction;
        this.profile = profile;
        this.script = script;
        this.strategy = StrategyScript.parse(script);
    }

    /**
     * The modules, made once the opening is known (AI-04): drawn by weight from {@code Rng(seed, OPENING, 0, faction,
     * 0)} at the first think and kept in memory, so a resumed player takes up the same one without drawing.
     */
    private List<AiModule> modules(AiView view) {
        List<AiModule> m = modules;
        if (m == null) {
            if (memory.get(Slots.OPENING) == 0 && !strategy.openings().isEmpty()) {
                int roll = Rng.below(view.seed(), Streams.OPENING, 0, faction, 0, strategy.totalWeight());
                memory.set(Slots.OPENING, strategy.pick(roll) + 1L);
            }
            StrategyScript s = strategy.opened((int) memory.get(Slots.OPENING) - 1);
            m = List.of(
                    new DefenceManager(memory),
                    new EconomyManager(s, memory),
                    new ProductionManager(s, memory),
                    new BuildManager(s, memory),
                    new MilitaryManager(s, memory),
                    new RebuildManager(memory));
            modules = m;
        }
        return m;
    }

    /** The opening it plays, by its index in the script's {@code openings}; -1 before the draw or with none. */
    public int opening() {
        return (int) memory.get(Slots.OPENING) - 1;
    }

    /** The name of the opening it plays, or "" for none. */
    public String openingName() {
        int k = opening();
        return k >= 0 && k < strategy.openings().size()
                ? strategy.openings().get(k).name()
                : "";
    }

    /** Raises the record of a camp at column ({@code x}, {@code z}), ground at block row {@code y}. */
    public void setCamp(int x, int y, int z) {
        memory.set(Slots.CAMP, (((long) x << 32) | (z & 0xFFFFFFFFL)) + 1);
        memory.set(Slots.CAMP_Y, y + 1L);
    }

    /** Forgets the camp. */
    public void clearCamp() {
        memory.set(Slots.CAMP, 0);
        memory.set(Slots.CAMP_Y, 0);
    }

    /** Whether a camp's blocks stand. */
    public boolean campUp() {
        return memory.get(Slots.CAMP) != 0;
    }

    /** The camp's column and ground row: x, y, z. */
    public int[] camp() {
        long at = memory.get(Slots.CAMP) - 1;
        return new int[] {(int) (at >> 32), (int) (memory.get(Slots.CAMP_Y) - 1), (int) at};
    }

    /** Whether a wave is mustering. */
    public boolean mustering() {
        return memory.get(Slots.MUSTER) != 0;
    }

    public int faction() {
        return faction;
    }

    public String profile() {
        return profile;
    }

    public String script() {
        return script;
    }

    public AiMemory memory() {
        return memory;
    }

    /** One think: every module in turn, within {@code budget}, sending through {@code sink}. */
    public void think(AiView view, AiBudget budget, CommandSink sink) {
        CommandSink counted = command -> {
            memory.set(Slots.COMMANDS, memory.get(Slots.COMMANDS) + 1);
            memory.set(Slots.LAST_COMMAND, view.tick());
            sink.send(command);
        };
        for (AiModule m : modules(view)) {
            if (budget.exhausted()) {
                break;
            }
            m.think(view, budget, counted);
        }
        lastSpent = budget.spent();
    }

    /** What its last think spent, in evaluation units: the view's, not the match's, so neither hashed nor saved. */
    public int lastSpent() {
        return lastSpent;
    }

    /** How many commands it has sent. */
    public long commands() {
        return memory.get(Slots.COMMANDS);
    }

    /** The tick of its last command, 0 before any. */
    public long lastCommand() {
        return memory.get(Slots.LAST_COMMAND);
    }

    /** The waves it launched. */
    public long waves() {
        return memory.get(Slots.WAVE);
    }

    /** The retreats it has ordered (AI-03). */
    public long retreats() {
        return memory.get(Slots.RETREATS);
    }

    /** The enemy unit its wave focuses, 0 for none (AI-03). */
    public int focus() {
        return (int) Math.max(0, memory.get(Slots.FOCUS) - 1);
    }

    /** Its wave's state: 0 none out, 1 out, 2 going home (AI-03). */
    public long fight() {
        return memory.get(Slots.FIGHT);
    }

    /** What it wants trained of each role, in permille: spearmen, archers, horsemen (AI-03). */
    public int[] want() {
        return new int[] {
            (int) Composition.want(memory, AiView.ROLE_SPEAR),
            (int) Composition.want(memory, AiView.ROLE_ARCHER),
            (int) Composition.want(memory, AiView.ROLE_HORSE)
        };
    }

    /** The step of its build order it is on. */
    public long step() {
        return memory.get(Slots.STEP);
    }
}
