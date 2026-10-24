package dev.strategos.core.sim.ai;

import java.util.List;

/**
 * A computer player (AI-01, 04 §14): its faction, its profile and strategy, its memory, and its modules, which think
 * in order (economy, production, building, military) within one budget. It counts the commands it sends and the tick
 * of the last, which the headless runner's watchdog reads.
 */
public final class ComputerPlayer {
    private final int faction;
    private final String profile;
    private final String script;
    private final AiMemory memory = new AiMemory();
    private final List<AiModule> modules;
    private int lastSpent;

    /** A scripted player of {@code faction}; a malformed {@code script} throws {@link IllegalArgumentException}. */
    public ComputerPlayer(int faction, String profile, String script) {
        this.faction = faction;
        this.profile = profile;
        this.script = script;
        StrategyScript s = StrategyScript.parse(script);
        this.modules = List.of(
                new EconomyManager(s, memory),
                new ProductionManager(s, memory),
                new BuildManager(s, memory),
                new MilitaryManager(s, memory));
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
        for (AiModule m : modules) {
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

    /** The step of its build order it is on. */
    public long step() {
        return memory.get(Slots.STEP);
    }
}
