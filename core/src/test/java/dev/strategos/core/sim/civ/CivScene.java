package dev.strategos.core.sim.civ;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.strategos.core.content.RawCiv;
import dev.strategos.core.content.TemplateCompiler;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.shared.CivId;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.PlayRules;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import java.util.ArrayList;
import java.util.List;

/** Template sets with a test civilisation beside the default, over the built-in templates and techs. */
final class CivScene {
    static final CivId LEAN = new CivId("t:lean");
    static final TemplateId CENTRE = new TemplateId("strategos:building/civic_centre");
    static final TemplateId HOUSE = new TemplateId("strategos:building/house");
    static final TemplateId STOREHOUSE = new TemplateId("strategos:building/storehouse");
    static final TemplateId WORKER = new TemplateId("strategos:unit/worker");
    static final TemplateId FARMSTEAD = new TemplateId("strategos:building/farmstead");
    static final TechId WOOD_1 = new TechId("strategos:gather_wood_1");
    static final String RATE = "ResourceGatherer/rates/wood/tree";

    private CivScene() {}

    /** The bonus {@link #lean(String)} takes: wood gathering by workers, times 1.5. */
    static final String WOOD_BONUS = "[{\"path\":\"" + RATE + "\",\"op\":\"mul\",\"value\":1.5,\"filter\":\"Worker\"}]";

    /** The default beside {@link #LEAN}, whose roster lacks the worker, the farmstead and the first wood tech. */
    static TemplateSet lean(String bonuses) {
        TemplateSet builtin = TemplateSet.builtin();
        JsonObject roster = new JsonObject();
        JsonArray templates = new JsonArray();
        for (UnitTemplate t : builtin.all()) {
            if (!t.id().equals(WORKER) && !t.id().equals(FARMSTEAD)) {
                templates.add(t.id().value());
            }
        }
        JsonArray techs = new JsonArray();
        builtin.techs().stream().filter(t -> !t.id().equals(WOOD_1)).forEach(t -> techs.add(t.id().value()));
        roster.add("templates", templates);
        roster.add("techs", techs);
        JsonObject civ = JsonParser.parseString("{\"roles\":{\"house\":\"strategos:building/house\"}}")
                .getAsJsonObject();
        civ.add("roster", roster);
        civ.add("bonuses", JsonParser.parseString(bonuses));
        return with(civ);
    }

    /** The built-in templates with the default civilisation written as {@code json} (its rules, say), no other. */
    static TemplateSet withDefault(JsonObject json) {
        TemplateSet builtin = TemplateSet.builtin();
        List<TemplateCompiler.Flat> flats = new ArrayList<>();
        for (UnitTemplate t : builtin.all()) {
            flats.add(new TemplateCompiler.Flat(t.id(), t.netIndex(), builtin.flattened(t.id())));
        }
        List<TemplateCompiler.TechFlat> techs =
                builtin.techs().stream().map(TemplateCompiler.TechFlat::of).toList();
        TemplateCompiler.Result r =
                TemplateCompiler.fromFlattened(flats, techs, List.of(new RawCiv(CivId.DEFAULT, "default.json", json)));
        if (!r.problems().isEmpty()) {
            throw new AssertionError(r.problems().toString());
        }
        return r.set();
    }

    /** The default beside a civilisation {@link #LEAN} of {@code json}. */
    static TemplateSet with(JsonObject json) {
        TemplateSet builtin = TemplateSet.builtin();
        List<TemplateCompiler.Flat> flats = new ArrayList<>();
        for (UnitTemplate t : builtin.all()) {
            flats.add(new TemplateCompiler.Flat(t.id(), t.netIndex(), builtin.flattened(t.id())));
        }
        List<TemplateCompiler.TechFlat> techs =
                builtin.techs().stream().map(TemplateCompiler.TechFlat::of).toList();
        RawCiv def = new RawCiv(
                CivId.DEFAULT, "default.json", builtin.civ(CivId.DEFAULT).toJson());
        RawCiv lean = new RawCiv(LEAN, "lean.json", json);
        TemplateCompiler.Result r = TemplateCompiler.fromFlattened(flats, techs, List.of(def, lean));
        if (!r.problems().isEmpty()) {
            throw new AssertionError(r.problems().toString());
        }
        return r.set();
    }

    /** The built-in templates and a civilisation {@link #LEAN} only: a set with no default. */
    static TemplateSet withoutDefault() {
        TemplateSet builtin = TemplateSet.builtin();
        List<TemplateCompiler.Flat> flats = new ArrayList<>();
        for (UnitTemplate t : builtin.all()) {
            flats.add(new TemplateCompiler.Flat(t.id(), t.netIndex(), builtin.flattened(t.id())));
        }
        List<TemplateCompiler.TechFlat> techs =
                builtin.techs().stream().map(TemplateCompiler.TechFlat::of).toList();
        RawCiv lean = new RawCiv(
                LEAN,
                "lean.json",
                JsonParser.parseString("{\"roles\":{\"house\":\"strategos:building/house\"}}")
                        .getAsJsonObject());
        return TemplateCompiler.fromFlattened(flats, techs, List.of(lean)).set();
    }

    /** A 64 x 64 match of two factions with a civic centre each, at (2, 2) and (50, 50), and stock to spend. */
    static SimHarness match(TemplateSet set, List<CivId> civs) {
        return match(
                set,
                civs,
                RuleCatalogue.DEFAULT,
                MatchRules.DEFAULTS.withPlay(new PlayRules(PlayRules.Victory.NONE, false, true, false)));
    }

    /** As {@link #match(TemplateSet, List)}, the rules drawn from {@code catalogue} and the match run by {@code rules}. */
    static SimHarness match(TemplateSet set, List<CivId> civs, RuleCatalogue catalogue, MatchRules rules) {
        return match(set, civs, catalogue, rules, VoxelFixtures.flat(64, 64, 32, 1));
    }

    /** As above, over {@code world} (64 x 64, 32 high). */
    static SimHarness match(
            TemplateSet set, List<CivId> civs, RuleCatalogue catalogue, MatchRules rules, VoxelWorld world) {
        SimHarness h = SimHarness.builder()
                .templates(set)
                .catalogue(catalogue)
                .rules(rules)
                .size(64, 64)
                .yBand(0, 31, 1)
                .factions(2)
                .civs(civs)
                .build();
        h.publishAll(world);
        for (int f = 0; f < 2; f++) {
            h.submit(
                    PlayerSlot.ADMIN,
                    new SpawnBuilding(CENTRE, new FactionSlot(f), f == 0 ? 2 : 50, f == 0 ? 2 : 50, 0));
            for (String r : List.of("food", "wood", "stone")) {
                h.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(f), r, 1_000));
            }
        }
        h.step();
        return h;
    }

    /** Faction {@code faction}'s only building of template {@code id}, spawned if it has none. */
    static int building(SimHarness h, int faction, TemplateId id, int x, int z) {
        MatchState s = KernelAccess.state(h.core());
        for (int handle : s.buildings().handles()) {
            if (s.buildings().faction(handle) == faction
                    && s.templates()
                            .byIndex(s.buildings().template(handle))
                            .id()
                            .equals(id)) {
                return handle;
            }
        }
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(id, new FactionSlot(faction), x, z, 0));
        h.step();
        return building(h, faction, id, x, z);
    }
}
