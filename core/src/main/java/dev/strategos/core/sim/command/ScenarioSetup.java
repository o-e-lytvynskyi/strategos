package dev.strategos.core.sim.command;

import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.CivDef;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.content.Scenario;
import dev.strategos.core.content.StartKit;
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
import dev.strategos.core.shared.CivId;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.econ.Deposits;
import dev.strategos.core.sim.econ.Livestock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;

/** A scenario's setup (MATCH-01): the admin commands that make a match what {@link Scenario} describes. */
@CoreApi
public final class ScenarioSetup {
    /** A slot's player that the computer plays: {@code ai:<profile>}. */
    public static final String AI = "ai:";

    /** The yaw the default civilisation's start kit is written for: slot 0 of {@code first_skirmish} (facing east). */
    public static final float KIT_YAW = 270f;

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
        return commands(scenario, starts, templates, scripts, List.of(), r -> r);
    }

    /**
     * As {@link #commands(Scenario, List, TemplateSet, Function)}, faction {@code k} playing {@code civs.get(k)} (the
     * default for every faction when the list is empty) and each slot's starting resources passed through {@code scale}.
     *
     * <p>A slot whose faction plays the civilisation it was written for emits its entities exactly as written. One that
     * plays another drops its {@code civ}-flagged entities and emits that civilisation's {@link StartKit} in place of
     * the first of them, with the kit's resources when it has any. A slot with {@code kit} and no flagged entity emits
     * its faction's kit first, whatever the civilisation. Kit offsets are in the slot's frame: written for {@link
     * #KIT_YAW}, turned by the start slot's yaw in quarter turns, building rotations with them. A role that the
     * civilisation has no template for, or a civilisation the set lacks, throws.
     */
    public static List<Command> commands(
            Scenario scenario,
            List<StartSlot> starts,
            TemplateSet templates,
            Function<String, @Nullable String> scripts,
            List<CivId> civs,
            UnaryOperator<Map<String, Long>> scale) {
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
            CivId played = k < civs.size() ? civs.get(k) : CivId.DEFAULT;
            Map<String, Long> resources = slot.resources();
            List<Scenario.Entity> entities = slot.entities();
            boolean flagged = slot.hasCivEntities();
            if (flagged ? !played.equals(slot.civ()) : slot.kit()) {
                CivDef civ = templates.civ(played);
                if (civ == null) {
                    throw new IllegalArgumentException("the template set has no civilisation " + played);
                }
                StartKit kit = civ.start();
                if (!kit.resources().isEmpty()) {
                    resources = kit.resources();
                }
                entities = withKit(slot.entities(), flagged, kit(civ, at.yaw()));
            }
            scale.apply(resources).forEach((id, amount) -> { // sorted by id
                if (amount > 0) {
                    out.add(new Grant(faction, id, amount));
                }
            });
            for (Scenario.Entity e : entities) {
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

    /** {@code entities} with the kit in place of the first flagged one (the others dropped), or ahead of them all. */
    private static List<Scenario.Entity> withKit(
            List<Scenario.Entity> entities, boolean flagged, List<Scenario.Entity> kit) {
        List<Scenario.Entity> out = new ArrayList<>();
        if (!flagged) {
            out.addAll(kit);
            out.addAll(entities);
            return out;
        }
        boolean placed = false;
        for (Scenario.Entity e : entities) {
            if (!e.civ()) {
                out.add(e);
            } else if (!placed) {
                out.addAll(kit);
                placed = true;
            }
        }
        return out;
    }

    /** {@code civ}'s start kit as scenario entities for a slot facing {@code yaw}: roles resolved, offsets turned. */
    private static List<Scenario.Entity> kit(CivDef civ, float yaw) {
        int quarters = Math.floorMod(Math.round((yaw - KIT_YAW) / 90f), 4);
        List<Scenario.Entity> out = new ArrayList<>();
        for (StartKit.Entity e : civ.start().entities()) {
            TemplateId template = e.template();
            if (template == null) {
                template = civ.roles().get(java.util.Objects.requireNonNull(e.role()));
                if (template == null) {
                    throw new IllegalArgumentException("the start kit of " + civ.id() + " names role " + e.role()
                            + ", which it has no template for");
                }
            }
            int dx = e.dx();
            int dz = e.dz();
            for (int q = 0; q < quarters; q++) { // a quarter turn from east to south: (dx, dz) -> (-dz, dx)
                int turned = -dz;
                dz = dx;
                dx = turned;
            }
            out.add(new Scenario.Entity(template, dx, dz, e.count(), e.spread(), (e.rotation() + quarters) & 3, false));
        }
        return out;
    }
}
