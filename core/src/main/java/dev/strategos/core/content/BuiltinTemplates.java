package dev.strategos.core.content;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.strategos.core.shared.TemplateId;
import java.util.List;

/**
 * The set the core falls back to before data packs load, and the one L1 tests use: the bundled templates of the mod's
 * {@code data/strategos/strategos/templates/}, here as text, compiled by the same compiler. DATA-03's bundled-template
 * test keeps the two equal.
 */
final class BuiltinTemplates {
    static final String UNIT_BASE = """
            {
              "abstract": true,
              "Identity": { "classes": "Unit" },
              "UnitMotion": { "walkSpeed": 0.1875, "turnRate": 45, "mobility": "infantry" },
              "Body": { "entityType": "strategos:unit_humanoid", "model": "humanoid", "width": 0.6, "height": 1.8, "radius": 0.35 }
            }
            """;
    static final String UNIT_INFANTRY = """
            {
              "parent": "strategos:template/unit_base",
              "abstract": true,
              "Identity": { "classes": "+Infantry +Organic" }
            }
            """;
    static final String DEBUG_WALKER = """
            {
              "parent": "strategos:template/unit_base",
              "Identity": { "name": "strategos.unit.debug_walker", "icon": "minecraft:armor_stand" },
              "UnitMotion": { "walkSpeed": 0.25 },
              "Body": { "skin": "strategos:units/debug_walker" }
            }
            """;
    static final String INFANTRY = """
            {
              "parent": "strategos:template/unit_infantry",
              "Identity": { "name": "strategos.unit.infantry", "icon": "minecraft:trident", "classes": "+Spearman" },
              "Attack": {
                "Melee": {
                  "hack": 3,
                  "pierce": { "$derive": { "from": "self", "path": "Attack/Melee/hack" }, "note": "A spear thrusts as hard as it cuts" },
                  "reach": 1.2,
                  "prepare": 10,
                  "repeat": 20
                },
                "Structure": { "fire": 3, "prepare": 10, "repeat": 20 }
              },
              "Resistance": {
                "hack": 5,
                "pierce": { "$derive": { "from": "self", "path": "Resistance/hack" }, "note": "Shield and padding stop a thrust as well as a cut" },
                "crush": 2
              },
              "Cost": {
                "resources": {
                  "food": { "$derive": { "from": "strategos:unit/worker", "path": "Cost/resources/food" }, "note": "A soldier eats what a worker does" },
                  "wood": 40
                },
                "population": 1,
                "time": { "$derive": { "from": "strategos:unit/worker", "path": "Cost/time", "factor": 1.5 }, "note": "Drilling a spearman takes half again a worker's training" }
              },
              "Body": { "skin": "strategos:units/infantry" }
            }
            """;
    static final String ARCHER = """
            {
              "parent": "strategos:template/unit_infantry",
              "Identity": { "name": "strategos.unit.archer", "icon": "minecraft:bow", "classes": "+Archer" },
              "Health": { "max": 50 },
              "UnitMotion": { "walkSpeed": 0.2 },
              "Attack": { "Ranged": { "pierce": 6, "range": 20, "prepare": 16, "repeat": 25, "speed": 4, "spread": 2.25 } },
              "Vision": { "range": 32 },
              "Resistance": { "hack": 1, "pierce": 1, "crush": 1 },
              "Cost": {
                "resources": { "food": 60, "wood": 40, "iron": 20 },
                "population": 1,
                "time": { "$derive": { "from": "strategos:unit/worker", "path": "Cost/time", "factor": 1.5 }, "note": "Training an archer takes as long as drilling a spearman" }
              },
              "Body": { "skin": "strategos:units/infantry" }
            }
            """;
    static final String WORKER = """
            {
              "parent": "strategos:template/unit_base",
              "Identity": { "name": "strategos.unit.worker", "icon": "minecraft:wooden_axe", "classes": "+Worker +Gatherer +Builder +Organic" },
              "Health": { "max": 50 },
              "Behaviour": { "flee": 8 },
              "Cost": { "resources": { "food": 60 }, "population": 1, "time": 10 },
              "Builder": { "rate": 1 },
              "ResourceGatherer": {
                "rates": { "food": { "fruit": 1, "grain": 0.5, "meat": 1 }, "wood": { "tree": 0.6 }, "stone": { "rock": 0.4 }, "iron": { "ore": 0.35 } },
                "capacities": { "food": 10, "wood": 10, "stone": 10, "iron": 10 },
                "slaughter": 2
              },
              "Body": { "skin": "strategos:units/worker" }
            }
            """;

    static final String UNIT_FAUNA = """
            {
              "abstract": true,
              "parent": "strategos:template/unit_base",
              "Identity": { "classes": "+Fauna +Organic" },
              "UnitMotion": { "walkSpeed": 0.1 },
              "Body": { "entityType": "strategos:unit_fauna" }
            }
            """;
    static final String SHEEP = """
            {
              "parent": "strategos:template/unit_fauna",
              "Identity": { "name": "strategos.unit.sheep", "icon": "minecraft:white_wool" },
              "Health": { "max": 8 },
              "Fauna": { "meat": 100, "slots": 3 },
              "Body": { "model": "sheep", "width": 0.9, "height": 1.3, "radius": 0.45 }
            }
            """;
    static final String PIG = """
            {
              "parent": "strategos:template/unit_fauna",
              "Identity": { "name": "strategos.unit.pig", "icon": "minecraft:porkchop" },
              "Health": { "max": 10 },
              "Fauna": { "meat": 150, "slots": 3 },
              "Body": { "model": "pig", "width": 0.9, "height": 0.9, "radius": 0.45 }
            }
            """;
    static final String CHICKEN = """
            {
              "parent": "strategos:template/unit_fauna",
              "Identity": { "name": "strategos.unit.chicken", "icon": "minecraft:feather" },
              "Health": { "max": 4 },
              "Fauna": { "meat": 40, "slots": 2 },
              "Body": { "model": "chicken", "width": 0.4, "height": 0.7, "radius": 0.25 }
            }
            """;

    static final String BUILDING_BASE = """
            {
              "abstract": true,
              "Identity": { "classes": "Structure" },
              "Building": { "material": "stone" },
              "Placement": { "civicReach": 48 },
              "Health": { "max": 500 },
              "Resistance": { "fire": 7 },
              "DamageStages": { "first": 85, "second": 65, "third": 35 }
            }
            """;
    static final String CIVIC_CENTRE = """
            {
              "parent": "strategos:template/building_base",
              "Identity": { "name": "strategos.building.civic_centre", "icon": "minecraft:bell", "classes": "+CivicCentre" },
              "Building": { "blueprint": "strategos:civic_centre" },
              "Placement": { "civicReach": 0, "spacing": 64 },
              "Health": { "max": 2000 },
              "Cost": { "resources": { "wood": 300, "stone": 200, "iron": 100 }, "time": 150 },
              "Dropsite": { "resources": "food wood stone iron" },
              "Housing": { "capacity": 20 },
              "Trainer": { "units": "strategos:unit/worker", "batchExponent": 0.8 },
              "Limit": { "count": 1 }
            }
            """;
    static final String FIELD = """
            {
              "parent": "strategos:template/building_base",
              "Identity": { "name": "strategos.building.field", "icon": "minecraft:wheat", "classes": "+Field" },
              "Building": { "blueprint": "strategos:field", "material": "wood" },
              "Resistance": { "fire": 0 },
              "Footprint": { "obstructs": 0 },
              "Health": { "max": 200 },
              "Cost": { "resources": { "wood": 60 }, "time": 20 },
              "Field": { "amount": 300, "ripenTime": 30, "reseed": { "wood": 40 } }
            }
            """;
    static final String HOUSE = """
            {
              "parent": "strategos:template/building_base",
              "Identity": { "name": "strategos.building.house", "icon": "minecraft:oak_door", "classes": "+House" },
              "Building": { "blueprint": "strategos:house", "material": "wood" },
              "Resistance": { "fire": 0 },
              "Health": { "max": 400 },
              "Cost": { "resources": { "wood": 80 }, "time": 30 },
              "Housing": { "capacity": 5 }
            }
            """;
    static final String BARRACKS = """
            {
              "parent": "strategos:template/building_base",
              "Identity": { "name": "strategos.building.barracks", "icon": "minecraft:shield", "classes": "+Barracks" },
              "Building": { "blueprint": "strategos:barracks", "material": "wood" },
              "Resistance": { "fire": 0 },
              "Health": { "max": 1200 },
              "Cost": { "resources": { "wood": 200, "stone": 100, "iron": 50 }, "time": 60 },
              "Trainer": { "units": "strategos:unit/infantry", "batchExponent": 0.8 }
            }
            """;

    /**
     * The net indices the built-in set gives, in the order its templates arrived: M2's units first, so their indices
     * stay 0 and 1 whatever ids later templates sort under (M3 spec §9 R10); a new id is appended.
     */
    static final String STOREHOUSE = """
            {
              "parent": "strategos:template/building_base",
              "Identity": { "name": "strategos.building.storehouse", "icon": "minecraft:barrel", "classes": "+Storehouse" },
              "Building": { "blueprint": "strategos:storehouse" },
              "Health": { "max": 800 },
              "Cost": { "resources": { "wood": 100, "stone": 50 }, "time": 40 },
              "Dropsite": { "resources": "wood stone iron" }
            }
            """;

    static final String FARMSTEAD = """
            {
              "parent": "strategos:template/building_base",
              "Identity": { "name": "strategos.building.farmstead", "icon": "minecraft:hay_block", "classes": "+Farmstead" },
              "Building": { "blueprint": "strategos:farmstead" },
              "Health": { "max": 600 },
              "Cost": { "resources": { "wood": 100 }, "time": 35 },
              "Dropsite": { "resources": "food" },
              "Reseeder": { "reach": 24 }
            }
            """;
    static final String QUARRY = """
            {
              "parent": "strategos:template/building_base",
              "Identity": { "name": "strategos.building.quarry", "icon": "minecraft:stone", "classes": "+Deposit" },
              "Building": { "blueprint": "strategos:quarry" },
              "Deposit": { "resource": "stone", "subtype": "rock", "amount": 1000, "slots": 3, "depleted": "minecraft:gravel" }
            }
            """;

    static final String QUARRY_LARGE = """
            {
              "parent": "strategos:template/building_base",
              "Identity": { "name": "strategos.building.quarry_large", "icon": "minecraft:stone", "classes": "+Deposit" },
              "Building": { "blueprint": "strategos:quarry_large" },
              "Deposit": { "resource": "stone", "subtype": "rock", "amount": 5000, "slots": 6, "depleted": "minecraft:gravel" }
            }
            """;
    static final String IRON_MINE = """
            {
              "parent": "strategos:template/building_base",
              "Identity": { "name": "strategos.building.iron_mine", "icon": "minecraft:iron_ore", "classes": "+Deposit" },
              "Building": { "blueprint": "strategos:iron_mine" },
              "Deposit": { "resource": "iron", "subtype": "ore", "amount": 1000, "slots": 3, "depleted": "minecraft:stone" }
            }
            """;
    static final String IRON_MINE_LARGE = """
            {
              "parent": "strategos:template/building_base",
              "Identity": { "name": "strategos.building.iron_mine_large", "icon": "minecraft:iron_ore", "classes": "+Deposit" },
              "Building": { "blueprint": "strategos:iron_mine_large" },
              "Deposit": { "resource": "iron", "subtype": "ore", "amount": 5000, "slots": 6, "depleted": "minecraft:stone" }
            }
            """;

    /**
     * The indices the built-in set gave before, kept (ADR-0012's append-only table, across versions as within a
     * session): a template added later takes the next index, in id order, so a saved or hashed index never moves.
     */
    private static final TemplateIdTable PINNED = new TemplateIdTable(List.of(
            new TemplateId("strategos:unit/debug_walker"),
            new TemplateId("strategos:unit/infantry"),
            new TemplateId("strategos:unit/worker"),
            new TemplateId("strategos:building/barracks"), // M3
            new TemplateId("strategos:building/civic_centre"),
            new TemplateId("strategos:building/field"),
            new TemplateId("strategos:building/house"),
            new TemplateId("strategos:building/iron_mine"), // M4 T6
            new TemplateId("strategos:building/iron_mine_large"),
            new TemplateId("strategos:building/quarry"),
            new TemplateId("strategos:building/quarry_large"),
            new TemplateId("strategos:unit/chicken"), // M4 T7
            new TemplateId("strategos:unit/pig"),
            new TemplateId("strategos:unit/sheep"),
            new TemplateId("strategos:building/farmstead"), // M4, previously appended implicitly
            new TemplateId("strategos:building/storehouse"),
            new TemplateId("strategos:unit/archer"))); // M5

    private BuiltinTemplates() {}

    static TemplateSet compile() {
        TemplateCompiler.Result result = TemplateCompiler.compile(
                List.of(
                        raw("strategos:template/unit_base", UNIT_BASE),
                        raw("strategos:template/unit_infantry", UNIT_INFANTRY),
                        raw("strategos:template/unit_fauna", UNIT_FAUNA),
                        raw("strategos:template/building_base", BUILDING_BASE),
                        raw("strategos:unit/debug_walker", DEBUG_WALKER),
                        raw("strategos:unit/infantry", INFANTRY),
                        raw("strategos:unit/archer", ARCHER),
                        raw("strategos:unit/worker", WORKER),
                        raw("strategos:unit/sheep", SHEEP),
                        raw("strategos:unit/pig", PIG),
                        raw("strategos:unit/chicken", CHICKEN),
                        raw("strategos:building/civic_centre", CIVIC_CENTRE),
                        raw("strategos:building/field", FIELD),
                        raw("strategos:building/house", HOUSE),
                        raw("strategos:building/barracks", BARRACKS),
                        raw("strategos:building/storehouse", STOREHOUSE),
                        raw("strategos:building/farmstead", FARMSTEAD),
                        raw("strategos:building/quarry", QUARRY),
                        raw("strategos:building/quarry_large", QUARRY_LARGE),
                        raw("strategos:building/iron_mine", IRON_MINE),
                        raw("strategos:building/iron_mine_large", IRON_MINE_LARGE)),
                PINNED);
        if (!result.ok()) {
            throw new IllegalStateException("the builtin templates do not compile: " + result.problems());
        }
        return result.set();
    }

    private static RawTemplate raw(String id, String json) {
        JsonObject object = JsonParser.parseString(json).getAsJsonObject();
        String path = id.substring(id.indexOf(':') + 1);
        return new RawTemplate(new TemplateId(id), "builtin/" + path + ".json", object);
    }
}
