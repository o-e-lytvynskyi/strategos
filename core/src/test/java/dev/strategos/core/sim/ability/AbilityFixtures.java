package dev.strategos.core.sim.ability;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.strategos.core.content.TemplateCompiler;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.protocol.SetStance;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Stance;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.testkit.SimHarness;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The built-in templates with abilities on the spearman (UNIT-10's tests): the abilities the JSON names, a match on
 * them, and the helpers the ability tests share.
 */
final class AbilityFixtures {
    static final FactionSlot F0 = new FactionSlot(0);
    static final FactionSlot F1 = new FactionSlot(1);
    static final PlayerSlot P0 = new PlayerSlot(0);

    /** One ability of every kind, on the spearman. */
    static final String ALL = """
            {
              "strike": { "target": "unit", "range": 3, "cooldown": 50,
                          "Cost": { "resources": { "food": 10 } },
                          "effects": [ { "kind": "damage", "params": { "type": "hack", "amount": 20 } } ] },
              "slam": { "target": "area", "radius": 4, "range": 6, "cooldown": 30,
                        "effects": [ { "kind": "damage", "params": { "type": "crush", "amount": 8 } } ] },
              "mend": { "target": "none", "cooldown": 20,
                        "effects": [ { "kind": "heal", "params": { "amount": 15 } } ] },
              "heal_other": { "target": "unit", "range": 3,
                              "effects": [ { "kind": "heal", "params": { "amount": 25 } } ] },
              "rally": { "target": "none", "cooldown": 100,
                         "effects": [ { "kind": "apply_modifiers", "params": { "ticks": 40, "modifiers": [
                             { "path": "UnitMotion/walkSpeed", "op": "mul", "value": 1.5 },
                             { "path": "Attack/Melee/hack", "op": "add", "value": 2 } ] } } ] },
              "grip": { "target": "none", "cooldown": 100,
                        "effects": [ { "kind": "capture_strength", "params": { "ticks": 60, "amount": 4 } } ] },
              "summon": { "target": "point", "range": 8,
                          "effects": [ { "kind": "spawn", "params": { "template": "strategos:unit/debug_walker", "count": 2 } } ] },
              "conjure": { "target": "point", "range": 8, "Cost": { "resources": { "food": 5 } },
                           "effects": [ { "kind": "spawn", "params": { "template": "strategos:unit/debug_walker", "count": 2 } } ] },
              "bless": { "target": "unit", "range": 3,
                         "effects": [ { "kind": "apply_modifiers", "params": { "ticks": 20, "modifiers": [
                             { "path": "UnitMotion/walkSpeed", "op": "mul", "value": 1.5 } ] } } ] },
              "curse": { "target": "unit", "range": 3,
                         "effects": [ { "kind": "apply_modifiers", "params": { "ticks": 20, "on": "enemy", "modifiers": [
                             { "path": "UnitMotion/walkSpeed", "op": "mul", "value": 0.5 } ] } } ] },
              "bloom": { "target": "area", "radius": 5, "range": 10,
                         "effects": [ { "kind": "heal", "params": { "amount": 10 } } ] },
              "blink": { "target": "point", "range": 10, "cooldown": 40,
                         "effects": [ { "kind": "teleport", "params": {} } ] }
            }
            """;

    /** Two abilities that the spearman uses by itself when idle: a heal of itself, a shot at the nearest enemy. */
    static final String AUTO = """
            {
              "aura": { "target": "none", "autocast": true, "pick": "self", "cooldown": 60,
                        "effects": [ { "kind": "heal", "params": { "amount": 5 } } ] },
              "snipe": { "target": "unit", "range": 6, "autocast": true, "pick": "nearest_enemy_in_range", "cooldown": 40,
                         "effects": [ { "kind": "damage", "params": { "type": "pierce", "amount": 10 } } ] }
            }
            """;

    private AbilityFixtures() {}

    /** The built-in templates, the spearman carrying {@code abilities} (a JSON object by id). */
    static TemplateSet set(String abilities) {
        List<TemplateCompiler.Flat> flats = new ArrayList<>();
        for (UnitTemplate t : TemplateSet.builtin().all()) {
            JsonObject json = Objects.requireNonNull(TemplateSet.builtin().flattened(t.id()));
            if (t.id().equals(TemplateSet.INFANTRY)) {
                json.add("Abilities", JsonParser.parseString(abilities));
            }
            flats.add(new TemplateCompiler.Flat(t.id(), t.netIndex(), json));
        }
        TemplateCompiler.Result result = TemplateCompiler.fromFlattened(
                flats,
                TemplateSet.builtin().techs().stream()
                        .map(TemplateCompiler.TechFlat::of)
                        .toList());
        assertThat(result.problems()).isEmpty();
        return result.set();
    }

    /** A match on {@link #ALL}, two factions. */
    static SimHarness match() {
        return SimHarness.builder().templates(set(ALL)).build();
    }

    static MatchState state(SimHarness h) {
        return KernelAccess.state(h.core());
    }

    /**
     * The spearman (the one with the abilities) of {@code faction} at ({@code x}, {@code z}), passive, so that it does
     * not take up fights of its own between its abilities.
     */
    static int spearman(SimHarness h, FactionSlot faction, double x, double z) {
        int handle = spawn(h, TemplateSet.INFANTRY, faction, x, z);
        h.submit(new PlayerSlot(faction.value()), new SetStance(UnitSet.of(handle), Stance.PASSIVE));
        h.step();
        return handle;
    }

    /** A debug walker, which has no ability and never fights back. */
    static int walker(SimHarness h, FactionSlot faction, double x, double z) {
        return spawn(h, TemplateSet.DEBUG_WALKER, faction, x, z);
    }

    /** A ram: siege, so no flesh to heal. */
    static int ram(SimHarness h, FactionSlot faction, double x, double z) {
        return spawn(h, new TemplateId("strategos:unit/ram"), faction, x, z);
    }

    static int spawn(SimHarness h, TemplateId template, FactionSlot faction, double x, double z) {
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(template, faction, new Vec2d(x, z), 1, 0));
        return h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .findFirst()
                .orElseThrow();
    }

    /** {@link #ALL} with ability {@code id} declared as {@code body} besides. */
    static String with(String id, String body) {
        JsonObject all = JsonParser.parseString(ALL).getAsJsonObject();
        all.add(id, JsonParser.parseString(body));
        return all.toString();
    }

    /** A harmless ability, for a set that has one more of them. */
    static final String NOTHING = "{\"target\":\"none\",\"effects\":[{\"kind\":\"heal\",\"params\":{\"amount\":1}}]}";

    static JsonElement parse(String json) {
        return JsonParser.parseString(json);
    }
}
