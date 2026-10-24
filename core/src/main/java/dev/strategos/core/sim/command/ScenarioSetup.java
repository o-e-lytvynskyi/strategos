package dev.strategos.core.sim.command;

import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.content.Scenario;
import dev.strategos.core.content.StartSlot;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.RuleChange;
import dev.strategos.core.protocol.SetAi;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnDeposit;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.econ.Deposits;
import dev.strategos.core.sim.econ.Livestock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** A scenario's setup (MATCH-01): the admin commands that make a match what {@link Scenario} describes. */
@CoreApi
public final class ScenarioSetup {
    /** A slot's player that the computer plays: {@code ai:<profile>}. */
    public static final String AI = "ai:";

    private ScenarioSetup() {}

    /**
     * The commands that set {@code scenario} up, slot {@code k} as faction {@code k} at {@code starts.get(k)}: the
     * victory rule first, then each slot's resources (by id, in order), buildings, deposits (spawned near it, owned by
     * nobody), units and animals (Gaia's, the slot after the scenario's). A template that is not in
     * {@code templates}, a building without a blueprint, or too few start slots throws.
     */
    public static List<Command> commands(Scenario scenario, List<StartSlot> starts, TemplateSet templates) {
        return commands(scenario, starts, templates, profile -> null);
    }

    /**
     * As {@link #commands(Scenario, List, TemplateSet)}, and a slot played by {@code ai:<profile>} handed to the
     * computer with the strategy {@code scripts} gives that profile; a profile it has none for stays a human's slot.
     */
    public static List<Command> commands(
            Scenario scenario,
            List<StartSlot> starts,
            TemplateSet templates,
            Function<String, @Nullable String> scripts) {
        List<Scenario.Slot> slots = scenario.slots();
        if (starts.size() < slots.size()) {
            throw new IllegalArgumentException(slots.size() + " slots but " + starts.size() + " start slots");
        }
        List<Command> out = new ArrayList<>();
        out.add(new RuleChange("victory", scenario.victory()));
        for (int k = 0; k < slots.size(); k++) {
            Scenario.Slot slot = slots.get(k);
            StartSlot at = starts.get(k);
            FactionSlot faction = new FactionSlot(k);
            if (slot.player().startsWith(AI)) {
                String profile = slot.player().substring(AI.length());
                String script = scripts.apply(profile);
                if (script != null) {
                    out.add(new SetAi(faction, profile, script));
                }
            }
            slot.resources().forEach((id, amount) -> { // sorted by id
                if (amount > 0) {
                    out.add(new Grant(faction, id, amount));
                }
            });
            for (Scenario.Entity e : slot.entities()) {
                UnitTemplate t = templates.byId(e.template());
                if (t == null) {
                    throw new IllegalArgumentException(
                            "scenario names no template " + e.template().value());
                }
                if (t.isBuilding()) {
                    Blueprint bp = templates.blueprint(t.netIndex());
                    if (bp == null) {
                        throw new IllegalArgumentException(e.template().value() + " has no blueprint");
                    }
                    Footprint probe = Footprint.of(bp, 0, 0, e.rotation());
                    if (Deposits.isDeposit(templates, t.netIndex())) { // a mine or a quarry, owned by nobody (ECO-08)
                        out.add(new SpawnDeposit(
                                e.template(),
                                at.x() + e.dx() - probe.spanX() / 2,
                                at.z() + e.dz() - probe.spanZ() / 2));
                        continue;
                    }
                    out.add(new SpawnBuilding(
                            e.template(),
                            faction,
                            at.x() + e.dx() - probe.spanX() / 2,
                            at.z() + e.dz() - probe.spanZ() / 2,
                            e.rotation()));
                } else {
                    out.add(new SpawnUnits(
                            e.template(),
                            Livestock.isFauna(templates, t.netIndex()) ? new FactionSlot(slots.size()) : faction,
                            new Vec2d(at.x() + e.dx() + 0.5, at.z() + e.dz() + 0.5),
                            e.count(),
                            e.spread()));
                }
            }
        }
        return out;
    }
}
