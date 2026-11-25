package dev.strategos.core.sim.rank;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.strategos.core.content.TemplateCompiler;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.protocol.SetStance;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.Target;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.protocol.UseAbility;
import dev.strategos.core.shared.AbilityId;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
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
import java.util.function.BiConsumer;

/**
 * The built-in templates with two damage abilities on the spearman and on the worker (CMB-11's tests): {@code hit}
 * takes 25 hack off a unit within three blocks, {@code kill} takes 500, which is lethal and capped by what is left.
 * The spearman has the built-in ranks (thresholds 200, 500, 1000); the worker has none.
 */
final class RankFixtures {
    static final FactionSlot F0 = new FactionSlot(0);
    static final FactionSlot F1 = new FactionSlot(1);
    static final PlayerSlot P0 = new PlayerSlot(0);
    static final TemplateId HORSEMAN = new TemplateId("strategos:unit/horseman");
    static final TemplateId ARCHER = new TemplateId("strategos:unit/archer");
    static final TemplateId RAM = new TemplateId("strategos:unit/ram");

    private static final String ABILITIES = """
            {
              "hit": { "target": "unit", "range": 3,
                       "effects": [ { "kind": "damage", "params": { "type": "hack", "amount": 25 } } ] },
              "kill": { "target": "unit", "range": 3,
                        "effects": [ { "kind": "damage", "params": { "type": "hack", "amount": 500 } } ] }
            }
            """;

    private RankFixtures() {}

    /** The built-in set whose spearman and worker carry the abilities; {@code edit} may change any template's JSON. */
    static TemplateSet set(BiConsumer<TemplateId, JsonObject> edit) {
        List<TemplateCompiler.Flat> flats = new ArrayList<>();
        for (UnitTemplate t : TemplateSet.builtin().all()) {
            JsonObject json = Objects.requireNonNull(TemplateSet.builtin().flattened(t.id()));
            if (t.id().equals(TemplateSet.INFANTRY) || t.id().equals(TemplateSet.WORKER)) {
                json.add("Abilities", JsonParser.parseString(ABILITIES));
            }
            edit.accept(t.id(), json);
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

    static TemplateSet set() {
        return set((id, json) -> {});
    }

    static SimHarness match() {
        return SimHarness.builder().templates(set()).build();
    }

    static MatchState state(SimHarness h) {
        return KernelAccess.state(h.core());
    }

    /** A unit of {@code template} for {@code faction} at ({@code x}, {@code z}), passive so that it takes no fights of its own. */
    static int spawn(SimHarness h, TemplateId template, FactionSlot faction, double x, double z) {
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(template, faction, new Vec2d(x, z), 1, 0));
        int handle = h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .findFirst()
                .orElseThrow();
        h.submit(new PlayerSlot(faction.value()), new SetStance(UnitSet.of(handle), Stance.PASSIVE));
        h.step();
        return handle;
    }

    /** The unit of {@code faction} that earns: a spearman. */
    static int spearman(SimHarness h, FactionSlot faction, double x, double z) {
        return spawn(h, TemplateSet.INFANTRY, faction, x, z);
    }

    /** {@code unit} uses {@code ability} on {@code target}, and a tick passes. */
    static void use(SimHarness h, int unit, String ability, int target) {
        PlayerSlot owner = new PlayerSlot(state(h).identity().faction(Handle.index(unit)));
        h.submit(owner, UseAbility.replace(UnitSet.of(unit), new AbilityId(ability), Target.unit(target)));
        h.step();
    }

    /** {@code unit} kills a fresh enemy worker (worth 60) beside it, as often as {@code times}. */
    static void killWorkers(SimHarness h, int unit, int times) {
        for (int k = 0; k < times; k++) {
            int worker = spawn(h, TemplateSet.WORKER, F1, 11, 16);
            use(h, unit, "kill", worker);
        }
    }
}
