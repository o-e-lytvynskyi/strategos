package dev.strategos.core.content;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.strategos.core.shared.CivId;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.shared.TemplateId;
import java.util.ArrayList;
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
              "Vision": { "night": 0.6 },
              "UnitMotion": { "walkSpeed": 0.1875, "turnRate": 45, "mobility": "infantry" },
              "Body": { "entityType": "strategos:unit_humanoid", "model": "humanoid", "width": 0.6, "height": 1.8, "radius": 0.35 }
            }
            """;
    static final String UNIT_SOLDIER = """
            {
              "parent": "strategos:template/unit_base",
              "abstract": true,
              "Promotion": {
                "thresholds": [200, 500, 1000],
                "ranks": [
                  [
                    { "path": "Health/max", "op": "mul", "value": 1.05 },
                    { "path": "Attack/Melee/hack", "op": "mul", "value": 1.05 },
                    { "path": "Attack/Melee/pierce", "op": "mul", "value": 1.05 },
                    { "path": "Attack/Ranged/pierce", "op": "mul", "value": 1.05 }
                  ],
                  [
                    { "path": "Health/max", "op": "mul", "value": 1.10 },
                    { "path": "Attack/Melee/hack", "op": "mul", "value": 1.10 },
                    { "path": "Attack/Melee/pierce", "op": "mul", "value": 1.10 },
                    { "path": "Attack/Ranged/pierce", "op": "mul", "value": 1.10 }
                  ],
                  [
                    { "path": "Health/max", "op": "mul", "value": 1.15 },
                    { "path": "Attack/Melee/hack", "op": "mul", "value": 1.15 },
                    { "path": "Attack/Melee/pierce", "op": "mul", "value": 1.15 },
                    { "path": "Attack/Ranged/pierce", "op": "mul", "value": 1.15 }
                  ]
                ]
              }
            }
            """;
    static final String UNIT_INFANTRY = """
            {
              "parent": "strategos:template/unit_soldier",
              "abstract": true,
              "Identity": { "classes": "+Infantry +Organic" },
              "Look": { "weapon": 0, "armour": 0 }
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
              "Promotion": { "value": 100 },
              "Loot": { "resources": { "food": 11, "wood": 4 } },
              "Capture": { "strength": 2.5 },
              "Attack": {
                "Melee": {
                  "hack": 3,
                  "pierce": { "$derive": { "from": "self", "path": "Attack/Melee/hack" }, "note": "A spear thrusts as hard as it cuts" },
                  "reach": 1.2,
                  "prepare": 10,
                  "repeat": 20,
                  "bonus": { "Cavalry": 2.5 }
                },
                "Structure": { "fire": 6, "prepare": 10, "repeat": 20 }
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
              "Promotion": { "value": 120 },
              "Loot": { "resources": { "food": 11, "wood": 4, "iron": 2 } },
              "Capture": { "strength": 2.5 },
              "Health": { "max": 70 },
              "UnitMotion": { "walkSpeed": 0.2 },
              "Attack": { "Ranged": { "pierce": 16, "range": 20, "prepare": 16, "repeat": 20, "speed": 4, "spread": 2.25 } },
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
    static final String HORSEMAN = """
            {
              "parent": "strategos:template/unit_soldier",
              "Identity": { "name": "strategos.unit.horseman", "icon": "minecraft:saddle", "classes": "+Cavalry +Melee +Organic +Hunter" },
              "Promotion": { "value": 150 },
              "Loot": { "resources": { "food": 13, "wood": 4, "iron": 3 } },
              "Capture": { "strength": 1.75 },
              "Health": { "max": 200 },
              "ResourceGatherer": { "rates": { "food": { "hunt": 5 } }, "capacities": { "food": 20 } },
              "UnitMotion": { "walkSpeed": 0.34, "mobility": "large" },
              "Attack": { "Melee": { "hack": 10, "reach": 1.5, "prepare": 8, "repeat": 18 } },
              "Resistance": { "hack": 3, "pierce": 2, "crush": 2 },
              "Look": { "weapon": 0, "armour": 0 },
              "Abilities": {
                "charge": {
                  "target": "none",
                  "cooldown": 600,
                  "icon": "minecraft:iron_horse_armor",
                  "effects": [
                    { "kind": "apply_modifiers", "params": { "ticks": 100, "modifiers": [
                      { "path": "UnitMotion/walkSpeed", "op": "mul", "value": 1.5 },
                      { "path": "Attack/Melee/hack", "op": "mul", "value": 1.2 }
                    ] } }
                  ]
                }
              },
              "Cost": {
                "resources": { "food": 80, "wood": 40, "iron": 30 },
                "population": 1,
                "time": { "$derive": { "from": "strategos:unit/worker", "path": "Cost/time", "factor": 1.5 }, "note": "Training a horseman takes as long as drilling a spearman" }
              },
              "Body": { "entityType": "strategos:unit_mount", "width": 1.4, "height": 2.0, "radius": 0.7, "skin": "strategos:units/infantry" }
            }
            """;
    static final String WORKER = """
            {
              "parent": "strategos:template/unit_base",
              "Identity": { "name": "strategos.unit.worker", "icon": "minecraft:wooden_axe", "classes": "+Worker +Gatherer +Builder +Organic" },
              "Promotion": { "value": 60 },
              "Loot": { "resources": { "food": 3 } },
              "Capture": { "strength": 1.0 },
              "Health": { "max": 50 },
              "Behaviour": { "flee": 8, "stance": "passive" },
              "Cost": { "resources": { "food": 60 }, "population": 1, "time": 10 },
              "Builder": { "rate": 1, "buildings": "strategos:building/civic_centre strategos:building/house strategos:building/barracks strategos:building/storehouse strategos:building/farmstead strategos:building/field strategos:building/sentry_tower strategos:building/palisade strategos:building/palisade_gate" },
              "ResourceGatherer": {
                "rates": { "food": { "fruit": 1, "grain": 0.5, "meat": 1, "hunt": 1 }, "wood": { "tree": 0.6 }, "stone": { "rock": 0.4 }, "iron": { "ore": 0.35 } },
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

    static final String WILD_BOAR = """
            {
              "parent": "strategos:template/unit_fauna",
              "Identity": { "name": "strategos.unit.wild_boar", "icon": "minecraft:porkchop" },
              "Health": { "max": 30 },
              "Fauna": { "meat": 150, "slots": 3, "wild": 1 },
              "Behaviour": { "flee": 16, "stance": "passive" },
              "UnitMotion": { "walkSpeed": 0.25 },
              "Body": { "model": "pig", "width": 0.9, "height": 0.9, "radius": 0.45 }
            }
            """;

    static final String BUILDING_BASE = """
            {
              "abstract": true,
              "Identity": { "classes": "Structure" },
              "Vision": { "night": 0.6 },
              "Building": { "material": "stone" },
              "Placement": { "territory": 1 },
              "GarrisonHolder": { "ejectBelow": 0.1 },
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
              "Capturable": { "points": 2000, "regen": 30, "garrisonRegen": 1 },
              "TerritoryInfluence": { "weight": 65535, "radius": 56, "root": 1 },
              "Placement": { "territory": 2, "spacing": 64 },
              "Health": { "max": 2000 },
              "Vision": { "light": 12 },
              "Attack": { "Building": { "arrows": 3, "perGarrisoned": 1, "interval": 80, "pierce": 8, "range": 24 } },
              "Loot": { "resources": { "wood": 60, "stone": 60, "iron": 50 } },
              "Cost": { "resources": { "wood": 300, "stone": 200, "iron": 100 }, "time": 150 },
              "Dropsite": { "resources": "food wood stone iron" },
              "Housing": { "capacity": 20 },
              "GarrisonHolder": { "capacity": 20, "classes": "Worker Infantry", "heal": 1 },
              "Trainer": { "units": "strategos:unit/worker", "batchExponent": 0.8 },
              "Limit": { "count": 1 },
              "Researcher": { "techs": "strategos:phase_town" }
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
              "Loot": { "resources": { "wood": 12 } },
              "Cost": { "resources": { "wood": 60 }, "time": 20 },
              "Field": { "amount": 300, "ripenTime": 30, "reseed": { "wood": 40 } }
            }
            """;
    static final String HOUSE = """
            {
              "parent": "strategos:template/building_base",
              "Identity": { "name": "strategos.building.house", "icon": "minecraft:oak_door", "classes": "+House +Village" },
              "Building": { "blueprint": "strategos:house", "material": "wood" },
              "Capturable": { "points": 400, "regen": 5, "garrisonRegen": 1 },
              "TerritoryInfluence": { "weight": 400, "radius": 16, "root": 0 },
              "Resistance": { "fire": 0 },
              "Health": { "max": 400 },
              "Loot": { "resources": { "wood": 16 } },
              "Cost": { "resources": { "wood": 80 }, "time": 30 },
              "Vision": { "light": 8 },
              "Housing": { "capacity": 5 },
              "GarrisonHolder": { "capacity": 5, "classes": "Worker" }
            }
            """;
    static final String BARRACKS = """
            {
              "parent": "strategos:template/building_base",
              "Identity": { "name": "strategos.building.barracks", "icon": "minecraft:shield", "classes": "+Barracks +Village" },
              "Building": { "blueprint": "strategos:barracks", "material": "wood" },
              "Capturable": { "points": 800, "regen": 10, "garrisonRegen": 1 },
              "TerritoryInfluence": { "weight": 400, "radius": 24, "root": 0 },
              "Resistance": { "fire": 0 },
              "Health": { "max": 1200 },
              "Loot": { "resources": { "wood": 40, "stone": 20, "iron": 10 } },
              "Cost": { "resources": { "wood": 200, "stone": 100, "iron": 50 }, "time": 60 },
              "Trainer": { "units": "strategos:unit/infantry strategos:unit/archer strategos:unit/horseman", "batchExponent": 0.8 },
              "Researcher": { "techs": "strategos:phalanx strategos:skirmish_drill" }
            }
            """;

    /**
     * The net indices the built-in set gives, in the order its templates arrived: M2's units first, so their indices
     * stay 0 and 1 whatever ids later templates sort under (M3 spec §9 R10); a new id is appended.
     */
    static final String TOWER = """
            {
              "parent": "strategos:template/building_base",
              "Identity": { "name": "strategos.building.tower", "icon": "minecraft:crossbow", "classes": "+Tower +Town" },
              "Building": { "blueprint": "strategos:tower" },
              "Capturable": { "points": 800, "regen": 10, "garrisonRegen": 1 },
              "TerritoryInfluence": { "weight": 400, "radius": 24, "root": 0 },
              "Health": { "max": 1000 },
              "Vision": { "range": 24, "light": 24 },
              "Attack": { "Building": { "arrows": 1, "perGarrisoned": 1, "interval": 40, "pierce": 10, "range": 28 } },
              "GarrisonHolder": { "capacity": 5, "classes": "Infantry" },
              "Loot": { "resources": { "wood": 20, "stone": 30 } },
              "Cost": { "resources": { "wood": 100, "stone": 150 }, "time": 75 }
            }
            """;

    static final String SENTRY_TOWER = """
            {
              "parent": "strategos:template/building_base",
              "Identity": { "name": "strategos.building.sentry_tower", "icon": "minecraft:bow", "classes": "+Tower" },
              "Building": { "blueprint": "strategos:sentry_tower", "material": "wood" },
              "Capturable": { "points": 400, "regen": 5, "garrisonRegen": 1 },
              "TerritoryInfluence": { "weight": 400, "radius": 20, "root": 0 },
              "Resistance": { "fire": 0 },
              "Health": { "max": 400 },
              "Vision": { "range": 20, "light": 20 },
              "Attack": { "Building": { "arrows": 1, "perGarrisoned": 1, "interval": 40, "pierce": 8, "range": 24 } },
              "GarrisonHolder": { "capacity": 3, "classes": "Infantry" },
              "Loot": { "resources": { "wood": 20 } },
              "Cost": { "resources": { "wood": 100 }, "time": 40 },
              "Upgrade": {
                "to": "strategos:building/tower",
                "time": 50,
                "requires": "strategos:phase_town",
                "Cost": { "resources": { "wood": 50, "stone": 100 } }
              }
            }
            """;

    static final String PALISADE = """
            {
              "parent": "strategos:template/building_base",
              "Identity": { "name": "strategos.building.palisade", "icon": "minecraft:crimson_fence", "classes": "+Wall +Decoration" },
              "Building": { "blueprint": "strategos:palisade", "material": "wood" },
              "Resistance": { "fire": 0 },
              "Health": { "max": 300 },
              "Loot": { "resources": { "wood": 3 } },
              "Cost": { "resources": { "wood": 14 }, "time": 8 },
              "Wall": { "segment": 4 }
            }
            """;

    static final String PALISADE_GATE = """
            {
              "parent": "strategos:template/building_base",
              "Identity": { "name": "strategos.building.palisade_gate", "icon": "minecraft:crimson_fence_gate", "classes": "+Wall +Decoration" },
              "Building": { "blueprint": "strategos:palisade_gate", "material": "wood" },
              "Resistance": { "fire": 0 },
              "Health": { "max": 500 },
              "Loot": { "resources": { "wood": 4 } },
              "Cost": { "resources": { "wood": 20 }, "time": 10 },
              "Wall": { "segment": 4, "gate": 1 }
            }
            """;

    static final String ARSENAL = """
            {
              "parent": "strategos:template/building_base",
              "Identity": { "name": "strategos.building.arsenal", "icon": "minecraft:anvil", "classes": "+Arsenal +Town" },
              "Building": { "blueprint": "strategos:arsenal" },
              "Capturable": { "points": 800, "regen": 10, "garrisonRegen": 1 },
              "TerritoryInfluence": { "weight": 400, "radius": 24, "root": 0 },
              "Health": { "max": 1500 },
              "Loot": { "resources": { "wood": 40, "stone": 30 } },
              "Cost": { "resources": { "wood": 200, "stone": 150 }, "time": 90 },
              "Trainer": { "units": "strategos:unit/ram", "batchExponent": 0.8 },
              "Researcher": { "techs": "" }
            }
            """;
    static final String TEMPLE = """
            {
              "parent": "strategos:template/building_base",
              "Identity": { "name": "strategos.building.temple", "icon": "minecraft:golden_apple", "classes": "+Temple +Town" },
              "Building": { "blueprint": "strategos:temple" },
              "Capturable": { "points": 800, "regen": 10, "garrisonRegen": 1 },
              "TerritoryInfluence": { "weight": 400, "radius": 24, "root": 0 },
              "Health": { "max": 1200 },
              "GarrisonHolder": { "capacity": 10, "classes": "Worker Infantry Healer", "heal": 3 },
              "Loot": { "resources": { "wood": 20, "stone": 40 } },
              "Cost": { "resources": { "wood": 100, "stone": 200 }, "time": 80 },
              "Trainer": { "units": "strategos:unit/healer", "batchExponent": 0.8 }
            }
            """;
    static final String HEALER = """
            {
              "parent": "strategos:template/unit_base",
              "Identity": { "name": "strategos.unit.healer", "icon": "minecraft:glistering_melon_slice", "classes": "+Healer +Organic" },
              "Promotion": { "value": 130 },
              "Loot": { "resources": { "food": 15, "iron": 3 } },
              "Capture": { "strength": 1.0 },
              "Health": { "max": 60 },
              "Behaviour": { "flee": 8, "stance": "passive" },
              "Attack": { "Melee": { "hack": 1, "reach": 1.2, "prepare": 10, "repeat": 20 } },
              "Abilities": {
                "heal": {
                  "target": "unit",
                  "range": 12,
                  "cooldown": 40,
                  "autocast": true,
                  "pick": "most_wounded_ally",
                  "icon": "minecraft:glistering_melon_slice",
                  "effects": [
                    { "kind": "heal", "params": { "amount": 5 } }
                  ]
                }
              },
              "Cost": {
                "resources": { "food": 100, "iron": 30 },
                "population": 1,
                "time": { "$derive": { "from": "strategos:unit/worker", "path": "Cost/time", "factor": 1.5 }, "note": "Training a healer takes as long as drilling a spearman" }
              },
              "Body": { "skin": "strategos:units/worker" }
            }
            """;
    static final String RAM = """
            {
              "parent": "strategos:template/unit_base",
              "Identity": { "name": "strategos.unit.ram", "icon": "minecraft:oak_log", "classes": "+Siege +Ram" },
              "Promotion": { "value": 220 },
              "Loot": { "resources": { "wood": 30, "iron": 14 } },
              "Health": { "max": 500 },
              "UnitMotion": { "walkSpeed": 0.1, "mobility": "large" },
              "Attack": { "Melee": { "crush": 40, "reach": 1.5, "prepare": 20, "repeat": 40, "restrictedClasses": "Unit Field" } },
              "Resistance": { "hack": 1, "pierce": 8, "crush": 3 },
              "Cost": {
                "resources": { "wood": 150, "iron": 70 },
                "population": 3,
                "time": { "$derive": { "from": "strategos:unit/worker", "path": "Cost/time", "factor": 3 }, "note": "A ram is framed and hung in three times a worker's training" }
              },
              "Body": { "entityType": "strategos:unit_large", "model": "ram", "width": 2.0, "height": 2.0, "radius": 1.0 }
            }
            """;

    static final String STOREHOUSE = """
            {
              "parent": "strategos:template/building_base",
              "Identity": { "name": "strategos.building.storehouse", "icon": "minecraft:barrel", "classes": "+Storehouse +Village" },
              "Building": { "blueprint": "strategos:storehouse" },
              "Capturable": { "points": 400, "regen": 5, "garrisonRegen": 1 },
              "TerritoryInfluence": { "weight": 400, "radius": 20, "root": 0 },
              "Health": { "max": 800 },
              "Loot": { "resources": { "wood": 20, "stone": 10 } },
              "Cost": { "resources": { "wood": 100, "stone": 50 }, "time": 40 },
              "Dropsite": { "resources": "wood stone iron" },
              "Researcher": { "techs": "strategos:gather_wood_1 strategos:gather_stone_1 strategos:gather_iron_1 strategos:carry_1 strategos:guild_lumber strategos:guild_mining" }
            }
            """;

    static final String GATHER_WOOD_1 = """
            {
              "name": "strategos.tech.gather_wood_1",
              "icon": "minecraft:iron_axe",
              "cost": { "food": 100, "wood": 50 },
              "time": 30,
              "effects": [
                { "path": "ResourceGatherer/rates/wood/tree", "op": "mul", "value": 1.2, "filter": "Worker" }
              ]
            }
            """;

    static final String GATHER_FARM_1 = """
            {
              "name": "strategos.tech.gather_farm_1",
              "icon": "minecraft:iron_hoe",
              "cost": { "food": 50, "wood": 100 },
              "time": 30,
              "effects": [
                { "path": "ResourceGatherer/rates/food/grain", "op": "mul", "value": 1.2, "filter": "Worker" }
              ]
            }
            """;

    static final String GATHER_STONE_1 = """
            {
              "name": "strategos.tech.gather_stone_1",
              "icon": "minecraft:iron_pickaxe",
              "cost": { "food": 100, "wood": 100 },
              "time": 35,
              "effects": [
                { "path": "ResourceGatherer/rates/stone/rock", "op": "mul", "value": 1.2, "filter": "Worker" }
              ]
            }
            """;

    static final String GATHER_IRON_1 = """
            {
              "name": "strategos.tech.gather_iron_1",
              "icon": "minecraft:raw_iron",
              "cost": { "food": 100, "wood": 100, "stone": 50 },
              "time": 40,
              "effects": [
                { "path": "ResourceGatherer/rates/iron/ore", "op": "mul", "value": 1.2, "filter": "Worker" }
              ]
            }
            """;

    static final String CARRY_1 = """
            {
              "name": "strategos.tech.carry_1",
              "icon": "minecraft:bundle",
              "cost": { "food": 150, "wood": 100 },
              "time": 40,
              "effects": [
                { "path": "ResourceGatherer/capacities/food", "op": "add", "value": 5, "filter": "Worker" },
                { "path": "ResourceGatherer/capacities/wood", "op": "add", "value": 5, "filter": "Worker" },
                { "path": "ResourceGatherer/capacities/stone", "op": "add", "value": 5, "filter": "Worker" },
                { "path": "ResourceGatherer/capacities/iron", "op": "add", "value": 5, "filter": "Worker" }
              ]
            }
            """;

    static final String IRON_WEAPONS = """
            {
              "name": "strategos.tech.iron_weapons",
              "icon": "minecraft:iron_sword",
              "cost": { "food": 200, "wood": 100, "iron": 150 },
              "time": 60,
              "requires": { "phase": "town" },
              "effects": [
                { "path": "Attack/Melee/hack", "op": "add", "value": 1, "filter": "Spearman Cavalry" },
                { "path": "Attack/Melee/pierce", "op": "add", "value": 1, "filter": "Spearman Cavalry" },
                { "path": "Attack/Ranged/pierce", "op": "add", "value": 1, "filter": "Archer" },
                { "path": "Look/weapon", "op": "add", "value": 1, "filter": "Infantry Cavalry" }
              ]
            }
            """;

    static final String CHAINMAIL = """
            {
              "name": "strategos.tech.chainmail",
              "icon": "minecraft:chainmail_chestplate",
              "cost": { "food": 200, "wood": 100, "iron": 150 },
              "time": 60,
              "requires": { "phase": "town" },
              "effects": [
                { "path": "Resistance/hack", "op": "add", "value": 1, "filter": "Infantry Cavalry" },
                { "path": "Resistance/pierce", "op": "add", "value": 1, "filter": "Infantry Cavalry" },
                { "path": "Look/armour", "op": "add", "value": 1, "filter": "Infantry Cavalry" }
              ]
            }
            """;

    static final String GATHER_WOOD_2 = """
            {
              "name": "strategos.tech.gather_wood_2",
              "icon": "minecraft:diamond_axe",
              "cost": { "food": 200, "wood": 100 },
              "time": 45,
              "requires": { "all": [{ "phase": "town" }, { "tech": "strategos:gather_wood_1" }] },
              "supersedes": "strategos:gather_wood_1",
              "effects": [
                { "path": "ResourceGatherer/rates/wood/tree", "op": "mul", "value": 1.4, "filter": "Worker" }
              ]
            }
            """;

    static final String GATHER_WOOD_3 = """
            {
              "name": "strategos.tech.gather_wood_3",
              "icon": "minecraft:netherite_axe",
              "cost": { "food": 300, "wood": 150 },
              "time": 60,
              "requires": { "all": [{ "phase": "city" }, { "tech": "strategos:gather_wood_2" }] },
              "supersedes": "strategos:gather_wood_2",
              "effects": [
                { "path": "ResourceGatherer/rates/wood/tree", "op": "mul", "value": 1.6, "filter": "Worker" }
              ]
            }
            """;

    static final String GATHER_FARM_2 = """
            {
              "name": "strategos.tech.gather_farm_2",
              "icon": "minecraft:diamond_hoe",
              "cost": { "food": 100, "wood": 200 },
              "time": 45,
              "requires": { "all": [{ "phase": "town" }, { "tech": "strategos:gather_farm_1" }] },
              "supersedes": "strategos:gather_farm_1",
              "effects": [
                { "path": "ResourceGatherer/rates/food/grain", "op": "mul", "value": 1.4, "filter": "Worker" }
              ]
            }
            """;

    static final String GATHER_FARM_3 = """
            {
              "name": "strategos.tech.gather_farm_3",
              "icon": "minecraft:netherite_hoe",
              "cost": { "food": 150, "wood": 300 },
              "time": 60,
              "requires": { "all": [{ "phase": "city" }, { "tech": "strategos:gather_farm_2" }] },
              "supersedes": "strategos:gather_farm_2",
              "effects": [
                { "path": "ResourceGatherer/rates/food/grain", "op": "mul", "value": 1.6, "filter": "Worker" }
              ]
            }
            """;

    static final String GATHER_STONE_2 = """
            {
              "name": "strategos.tech.gather_stone_2",
              "icon": "minecraft:diamond_pickaxe",
              "cost": { "food": 200, "wood": 200 },
              "time": 50,
              "requires": { "all": [{ "phase": "town" }, { "tech": "strategos:gather_stone_1" }] },
              "supersedes": "strategos:gather_stone_1",
              "effects": [
                { "path": "ResourceGatherer/rates/stone/rock", "op": "mul", "value": 1.4, "filter": "Worker" }
              ]
            }
            """;

    static final String GATHER_STONE_3 = """
            {
              "name": "strategos.tech.gather_stone_3",
              "icon": "minecraft:netherite_pickaxe",
              "cost": { "food": 300, "wood": 300 },
              "time": 65,
              "requires": { "all": [{ "phase": "city" }, { "tech": "strategos:gather_stone_2" }] },
              "supersedes": "strategos:gather_stone_2",
              "effects": [
                { "path": "ResourceGatherer/rates/stone/rock", "op": "mul", "value": 1.6, "filter": "Worker" }
              ]
            }
            """;

    static final String GATHER_IRON_2 = """
            {
              "name": "strategos.tech.gather_iron_2",
              "icon": "minecraft:iron_ingot",
              "cost": { "food": 200, "wood": 200, "stone": 100 },
              "time": 55,
              "requires": { "all": [{ "phase": "town" }, { "tech": "strategos:gather_iron_1" }] },
              "supersedes": "strategos:gather_iron_1",
              "effects": [
                { "path": "ResourceGatherer/rates/iron/ore", "op": "mul", "value": 1.4, "filter": "Worker" }
              ]
            }
            """;

    static final String GATHER_IRON_3 = """
            {
              "name": "strategos.tech.gather_iron_3",
              "icon": "minecraft:iron_block",
              "cost": { "food": 300, "wood": 300, "stone": 150 },
              "time": 70,
              "requires": { "all": [{ "phase": "city" }, { "tech": "strategos:gather_iron_2" }] },
              "supersedes": "strategos:gather_iron_2",
              "effects": [
                { "path": "ResourceGatherer/rates/iron/ore", "op": "mul", "value": 1.6, "filter": "Worker" }
              ]
            }
            """;

    static final String CARRY_2 = """
            {
              "name": "strategos.tech.carry_2",
              "icon": "minecraft:chest",
              "cost": { "food": 300, "wood": 200 },
              "time": 55,
              "requires": { "all": [{ "phase": "town" }, { "tech": "strategos:carry_1" }] },
              "supersedes": "strategos:carry_1",
              "effects": [
                { "path": "ResourceGatherer/capacities/food", "op": "add", "value": 10, "filter": "Worker" },
                { "path": "ResourceGatherer/capacities/wood", "op": "add", "value": 10, "filter": "Worker" },
                { "path": "ResourceGatherer/capacities/stone", "op": "add", "value": 10, "filter": "Worker" },
                { "path": "ResourceGatherer/capacities/iron", "op": "add", "value": 10, "filter": "Worker" }
              ]
            }
            """;

    static final String CARRY_3 = """
            {
              "name": "strategos.tech.carry_3",
              "icon": "minecraft:ender_chest",
              "cost": { "food": 450, "wood": 300 },
              "time": 70,
              "requires": { "all": [{ "phase": "city" }, { "tech": "strategos:carry_2" }] },
              "supersedes": "strategos:carry_2",
              "effects": [
                { "path": "ResourceGatherer/capacities/food", "op": "add", "value": 15, "filter": "Worker" },
                { "path": "ResourceGatherer/capacities/wood", "op": "add", "value": 15, "filter": "Worker" },
                { "path": "ResourceGatherer/capacities/stone", "op": "add", "value": 15, "filter": "Worker" },
                { "path": "ResourceGatherer/capacities/iron", "op": "add", "value": 15, "filter": "Worker" }
              ]
            }
            """;

    static final String DIAMOND_WEAPONS = """
            {
              "name": "strategos.tech.diamond_weapons",
              "icon": "minecraft:diamond_sword",
              "cost": { "food": 300, "wood": 150, "iron": 250 },
              "time": 80,
              "requires": { "all": [{ "phase": "town" }, { "tech": "strategos:iron_weapons" }] },
              "supersedes": "strategos:iron_weapons",
              "effects": [
                { "path": "Attack/Melee/hack", "op": "add", "value": 2, "filter": "Spearman Cavalry" },
                { "path": "Attack/Melee/pierce", "op": "add", "value": 2, "filter": "Spearman Cavalry" },
                { "path": "Attack/Ranged/pierce", "op": "add", "value": 2, "filter": "Archer" },
                { "path": "Look/weapon", "op": "add", "value": 2, "filter": "Infantry Cavalry" }
              ]
            }
            """;

    static final String NETHERITE_WEAPONS = """
            {
              "name": "strategos.tech.netherite_weapons",
              "icon": "minecraft:netherite_sword",
              "cost": { "food": 450, "wood": 250, "iron": 350 },
              "time": 100,
              "requires": { "all": [{ "phase": "city" }, { "tech": "strategos:diamond_weapons" }] },
              "supersedes": "strategos:diamond_weapons",
              "effects": [
                { "path": "Attack/Melee/hack", "op": "add", "value": 3, "filter": "Spearman Cavalry" },
                { "path": "Attack/Melee/pierce", "op": "add", "value": 3, "filter": "Spearman Cavalry" },
                { "path": "Attack/Ranged/pierce", "op": "add", "value": 3, "filter": "Archer" },
                { "path": "Look/weapon", "op": "add", "value": 3, "filter": "Infantry Cavalry" }
              ]
            }
            """;

    static final String IRON_ARMOUR = """
            {
              "name": "strategos.tech.iron_armour",
              "icon": "minecraft:iron_chestplate",
              "cost": { "food": 300, "wood": 150, "iron": 250 },
              "time": 80,
              "requires": { "all": [{ "phase": "town" }, { "tech": "strategos:chainmail" }] },
              "supersedes": "strategos:chainmail",
              "effects": [
                { "path": "Resistance/hack", "op": "add", "value": 2, "filter": "Infantry Cavalry" },
                { "path": "Resistance/pierce", "op": "add", "value": 2, "filter": "Infantry Cavalry" },
                { "path": "Look/armour", "op": "add", "value": 2, "filter": "Infantry Cavalry" }
              ]
            }
            """;

    static final String DIAMOND_ARMOUR = """
            {
              "name": "strategos.tech.diamond_armour",
              "icon": "minecraft:diamond_chestplate",
              "cost": { "food": 450, "wood": 250, "iron": 350 },
              "time": 100,
              "requires": { "all": [{ "phase": "city" }, { "tech": "strategos:iron_armour" }] },
              "supersedes": "strategos:iron_armour",
              "effects": [
                { "path": "Resistance/hack", "op": "add", "value": 3, "filter": "Infantry Cavalry" },
                { "path": "Resistance/pierce", "op": "add", "value": 3, "filter": "Infantry Cavalry" },
                { "path": "Look/armour", "op": "add", "value": 3, "filter": "Infantry Cavalry" }
              ]
            }
            """;

    static final String MASONRY_1 = """
            {
              "name": "strategos.tech.masonry_1",
              "icon": "minecraft:stone_bricks",
              "cost": { "wood": 200, "stone": 200 },
              "time": 60,
              "requires": { "phase": "town" },
              "effects": [
                { "path": "Health/max", "op": "mul", "value": 1.2, "filter": "Structure+!Field" }
              ]
            }
            """;

    static final String MASONRY_2 = """
            {
              "name": "strategos.tech.masonry_2",
              "icon": "minecraft:deepslate_bricks",
              "cost": { "wood": 300, "stone": 400, "iron": 100 },
              "time": 90,
              "requires": { "all": [{ "phase": "city" }, { "tech": "strategos:masonry_1" }] },
              "supersedes": "strategos:masonry_1",
              "effects": [
                { "path": "Health/max", "op": "mul", "value": 1.5, "filter": "Structure+!Field" }
              ]
            }
            """;

    static final String ARROW_SLITS = """
            {
              "name": "strategos.tech.arrow_slits",
              "icon": "minecraft:arrow",
              "cost": { "wood": 150, "stone": 150 },
              "time": 60,
              "requires": { "phase": "town" },
              "effects": [
                { "path": "Attack/Building/arrows", "op": "add", "value": 1, "filter": "CivicCentre Tower" }
              ]
            }
            """;

    static final String CARPENTRY = """
            {
              "name": "strategos.tech.carpentry",
              "icon": "minecraft:crafting_table",
              "cost": { "food": 150, "wood": 200 },
              "time": 50,
              "requires": { "phase": "town" },
              "effects": [
                { "path": "Builder/rate", "op": "mul", "value": 1.25, "filter": "Worker" }
              ]
            }
            """;

    static final String GUILD_LUMBER = """
            {
              "name": "strategos.tech.guild_lumber",
              "icon": "minecraft:oak_log",
              "cost": { "food": 250, "wood": 250, "stone": 100 },
              "time": 60,
              "requires": { "phase": "town" },
              "excludes": ["strategos:guild_mining"],
              "effects": [
                { "path": "ResourceGatherer/rates/wood/tree", "op": "mul", "value": 1.3, "filter": "Worker" }
              ]
            }
            """;

    static final String GUILD_MINING = """
            {
              "name": "strategos.tech.guild_mining",
              "icon": "minecraft:stone_pickaxe",
              "cost": { "food": 250, "wood": 250, "stone": 100 },
              "time": 60,
              "requires": { "phase": "town" },
              "excludes": ["strategos:guild_lumber"],
              "effects": [
                { "path": "ResourceGatherer/rates/stone/rock", "op": "mul", "value": 1.25, "filter": "Worker" },
                { "path": "ResourceGatherer/rates/iron/ore", "op": "mul", "value": 1.25, "filter": "Worker" }
              ]
            }
            """;

    static final String PHALANX = """
            {
              "name": "strategos.tech.phalanx",
              "icon": "minecraft:shield",
              "cost": { "food": 250, "wood": 150, "iron": 100 },
              "time": 60,
              "requires": { "phase": "town" },
              "excludes": ["strategos:skirmish_drill"],
              "effects": [
                { "path": "Resistance/hack", "op": "add", "value": 2, "filter": "Spearman" },
                { "path": "Resistance/pierce", "op": "add", "value": 2, "filter": "Spearman" }
              ]
            }
            """;

    static final String SKIRMISH_DRILL = """
            {
              "name": "strategos.tech.skirmish_drill",
              "icon": "minecraft:leather_boots",
              "cost": { "food": 250, "wood": 150, "iron": 100 },
              "time": 60,
              "requires": { "phase": "town" },
              "excludes": ["strategos:phalanx"],
              "effects": [
                { "path": "UnitMotion/walkSpeed", "op": "mul", "value": 1.1, "filter": "Infantry Cavalry" },
                { "path": "Attack/Ranged/range", "op": "add", "value": 3, "filter": "Archer" }
              ]
            }
            """;

    static final String PHASE_VILLAGE = """
            {
              "name": "strategos.tech.phase_village",
              "icon": "minecraft:hay_block",
              "classes": ["Phase"],
              "ordinal": 0,
              "time": 1
            }
            """;

    static final String PHASE_TOWN = """
            {
              "name": "strategos.tech.phase_town",
              "icon": "minecraft:bell",
              "classes": ["Phase"],
              "ordinal": 1,
              "cost": { "food": 500, "wood": 600, "stone": 300, "iron": 200 },
              "time": 60,
              "requires": { "buildings": { "filter": "Village", "count": 5 } },
              "effects": [
                { "path": "Health/max", "op": "mul", "value": 1.1, "filter": "Structure+!Field" },
                { "path": "Vision/range", "op": "add", "value": 2, "filter": "Structure+!Field" },
                { "path": "Researcher/techs", "op": "tokens", "tokens": "+strategos:iron_weapons +strategos:chainmail +strategos:diamond_weapons +strategos:iron_armour", "filter": "Barracks" },
                { "path": "Builder/buildings", "op": "tokens", "tokens": "+strategos:building/tower +strategos:building/arsenal +strategos:building/temple", "filter": "Builder" },
                { "path": "Limit/count", "op": "add", "value": 1, "filter": "CivicCentre" },
                { "path": "Capturable/garrisonRegen", "op": "add", "value": 0.5, "filter": "Structure" },
                { "path": "Researcher/techs", "op": "tokens", "tokens": "+strategos:phase_city", "filter": "CivicCentre" },
                { "path": "Researcher/techs", "op": "tokens", "tokens": "+strategos:gather_wood_2 +strategos:gather_stone_2 +strategos:gather_iron_2 +strategos:carry_2", "filter": "Storehouse" },
                { "path": "Researcher/techs", "op": "tokens", "tokens": "+strategos:gather_farm_2", "filter": "Farmstead" },
                { "path": "Researcher/techs", "op": "tokens", "tokens": "+strategos:masonry_1 +strategos:arrow_slits +strategos:carpentry", "filter": "Arsenal" }
              ],
              "unlocks": ["strategos:iron_weapons", "strategos:chainmail", "strategos:building/tower", "strategos:building/arsenal", "strategos:building/temple", "strategos:phase_city", "strategos:diamond_weapons", "strategos:iron_armour", "strategos:gather_wood_2", "strategos:gather_stone_2", "strategos:gather_iron_2", "strategos:carry_2", "strategos:gather_farm_2", "strategos:masonry_1", "strategos:arrow_slits", "strategos:carpentry"]
            }
            """;

    static final String PHASE_CITY = """
            {
              "name": "strategos.tech.phase_city",
              "icon": "minecraft:beacon",
              "classes": ["Phase"],
              "ordinal": 2,
              "cost": { "food": 800, "wood": 900, "stone": 600, "iron": 400 },
              "time": 90,
              "requires": { "all": [{ "phase": "town" }, { "buildings": { "filter": "Town", "count": 3 } }] },
              "effects": [
                { "path": "TerritoryInfluence/radius", "op": "mul", "value": 1.3, "filter": "CivicCentre" },
                { "path": "Attack/Building/arrows", "op": "add", "value": 2, "filter": "CivicCentre Tower" },
                { "path": "Researcher/techs", "op": "tokens", "tokens": "+strategos:gather_wood_3 +strategos:gather_stone_3 +strategos:gather_iron_3 +strategos:carry_3", "filter": "Storehouse" },
                { "path": "Researcher/techs", "op": "tokens", "tokens": "+strategos:gather_farm_3", "filter": "Farmstead" },
                { "path": "Researcher/techs", "op": "tokens", "tokens": "+strategos:netherite_weapons +strategos:diamond_armour", "filter": "Barracks" },
                { "path": "Researcher/techs", "op": "tokens", "tokens": "+strategos:masonry_2", "filter": "Arsenal" }
              ],
              "unlocks": ["strategos:gather_wood_3", "strategos:gather_stone_3", "strategos:gather_iron_3", "strategos:carry_3", "strategos:gather_farm_3", "strategos:netherite_weapons", "strategos:diamond_armour", "strategos:masonry_2"]
            }
            """;

    static final String FARMSTEAD = """
            {
              "parent": "strategos:template/building_base",
              "Identity": { "name": "strategos.building.farmstead", "icon": "minecraft:hay_block", "classes": "+Farmstead +Village" },
              "Building": { "blueprint": "strategos:farmstead" },
              "Capturable": { "points": 300, "regen": 5, "garrisonRegen": 1 },
              "TerritoryInfluence": { "weight": 400, "radius": 20, "root": 0 },
              "Health": { "max": 600 },
              "Loot": { "resources": { "wood": 20 } },
              "Cost": { "resources": { "wood": 100 }, "time": 35 },
              "Dropsite": { "resources": "food" },
              "Reseeder": { "reach": 24 },
              "Researcher": { "techs": "strategos:gather_farm_1" }
            }
            """;
    static final String QUARRY = """
            {
              "parent": "strategos:template/building_base",
              "Identity": { "name": "strategos.building.quarry", "icon": "minecraft:stone", "classes": "+Deposit" },
              "Building": { "blueprint": "strategos:quarry" },
              "TerritoryInfluence": { "weight": 200, "radius": 12, "root": 0 },
              "Deposit": { "resource": "stone", "subtype": "rock", "amount": 1000, "slots": 3, "depleted": "minecraft:gravel" }
            }
            """;

    static final String QUARRY_LARGE = """
            {
              "parent": "strategos:template/building_base",
              "Identity": { "name": "strategos.building.quarry_large", "icon": "minecraft:stone", "classes": "+Deposit" },
              "Building": { "blueprint": "strategos:quarry_large" },
              "TerritoryInfluence": { "weight": 200, "radius": 12, "root": 0 },
              "Deposit": { "resource": "stone", "subtype": "rock", "amount": 5000, "slots": 6, "depleted": "minecraft:gravel" }
            }
            """;
    static final String IRON_MINE = """
            {
              "parent": "strategos:template/building_base",
              "Identity": { "name": "strategos.building.iron_mine", "icon": "minecraft:iron_ore", "classes": "+Deposit" },
              "Building": { "blueprint": "strategos:iron_mine" },
              "TerritoryInfluence": { "weight": 200, "radius": 12, "root": 0 },
              "Deposit": { "resource": "iron", "subtype": "ore", "amount": 1000, "slots": 3, "depleted": "minecraft:stone" }
            }
            """;
    static final String IRON_MINE_LARGE = """
            {
              "parent": "strategos:template/building_base",
              "Identity": { "name": "strategos.building.iron_mine_large", "icon": "minecraft:iron_ore", "classes": "+Deposit" },
              "Building": { "blueprint": "strategos:iron_mine_large" },
              "TerritoryInfluence": { "weight": 200, "radius": 12, "root": 0 },
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
            new TemplateId("strategos:unit/archer"), // M5
            new TemplateId("strategos:unit/horseman"),
            new TemplateId("strategos:building/tower"), // M6 T20
            new TemplateId("strategos:building/arsenal"), // M6 T19
            new TemplateId("strategos:unit/ram"),
            new TemplateId("strategos:building/sentry_tower"), // M9 T11
            new TemplateId("strategos:building/palisade"), // M9 T13
            new TemplateId("strategos:building/palisade_gate"),
            new TemplateId("strategos:building/temple"), // M10 T13
            new TemplateId("strategos:unit/healer"),
            new TemplateId("strategos:unit/wild_boar"))); // M10 T21

    /** The indices the built-in techs were given, kept as {@link #PINNED} keeps the templates' (row 20). */
    private static final TechIdTable PINNED_TECHS = new TechIdTable(List.of(
            new TechId("strategos:gather_wood_1"),
            new TechId("strategos:phase_town"), // M6 T3
            new TechId("strategos:phase_village"),
            new TechId("strategos:carry_1"), // M6 T18
            new TechId("strategos:chainmail"),
            new TechId("strategos:gather_farm_1"),
            new TechId("strategos:gather_iron_1"),
            new TechId("strategos:gather_stone_1"),
            new TechId("strategos:iron_weapons"),
            new TechId("strategos:phase_city"), // M10 T15
            new TechId("strategos:gather_wood_2"),
            new TechId("strategos:gather_wood_3"),
            new TechId("strategos:gather_farm_2"),
            new TechId("strategos:gather_farm_3"),
            new TechId("strategos:gather_stone_2"),
            new TechId("strategos:gather_stone_3"),
            new TechId("strategos:gather_iron_2"),
            new TechId("strategos:gather_iron_3"),
            new TechId("strategos:carry_2"),
            new TechId("strategos:carry_3"),
            new TechId("strategos:diamond_weapons"),
            new TechId("strategos:netherite_weapons"),
            new TechId("strategos:iron_armour"),
            new TechId("strategos:diamond_armour"),
            new TechId("strategos:masonry_1"),
            new TechId("strategos:masonry_2"),
            new TechId("strategos:arrow_slits"),
            new TechId("strategos:carpentry"),
            new TechId("strategos:guild_lumber"),
            new TechId("strategos:guild_mining"),
            new TechId("strategos:phalanx"),
            new TechId("strategos:skirmish_drill"))); // M10 T16

    /**
     * The default civilisation (CIV-01, ADR-0028): today's content and behaviour. The mod ships the same definition as
     * {@code civs/default.json}; a set whose civilisations are exactly this one has no {@code $civs} in its hash.
     */
    static final String DEFAULT_CIV_JSON = """
            {
              "name": "strategos.civ.default",
              "phases": ["strategos.phase.0", "strategos.phase.1", "strategos.phase.2"],
              "roster": { "templates": "*", "techs": "*" },
              "centreClass": "CivicCentre",
              "roles": {
                "centre": "strategos:building/civic_centre",
                "house": "strategos:building/house",
                "worker": "strategos:unit/worker",
                "infantry": "strategos:unit/infantry",
                "ranged": "strategos:unit/archer",
                "cavalry": "strategos:unit/horseman",
                "siege": "strategos:unit/ram",
                "barracks": "strategos:building/barracks",
                "tower": "strategos:building/tower",
                "farmstead": "strategos:building/farmstead",
                "field": "strategos:building/field",
                "storehouse": "strategos:building/storehouse",
                "wall": "strategos:building/palisade",
                "gate": "strategos:building/palisade_gate",
                "temple": "strategos:building/temple",
                "healer": "strategos:unit/healer"
              },
              "start": {
                "resources": { "food": 300, "stone": 100, "iron": 100 },
                "entities": [
                  { "role": "centre" },
                  { "role": "worker", "dz": 9, "count": 5, "spread": 2 },
                  { "role": "cavalry", "dx": -6, "dz": 10 },
                  { "template": "strategos:unit/sheep", "dx": 9, "dz": 3, "count": 4, "spread": 2 },
                  { "template": "strategos:unit/pig", "dx": -9, "dz": 3, "count": 2, "spread": 1.5 },
                  { "template": "strategos:unit/chicken", "dx": 5, "dz": 11, "count": 3, "spread": 1.5 }
                ]
              },
              "bonuses": [],
              "rules": {},
              "ai": { "profile": "skirmish" }
            }
            """;

    static final CivId DEFAULT_CIV_ID = new CivId("strategos:default");

    /** {@link #DEFAULT_CIV_JSON} decoded. */
    static final CivDef DEFAULT_CIV = defaultCiv();

    /** The canonical {@code $civs} of a set that has only the default: what a set's civilisations are compared to. */
    static final String DEFAULT_CIVS_CANONICAL = canonicalCivs(DEFAULT_CIV);

    private static CivDef defaultCiv() {
        List<TemplateProblem> problems = new ArrayList<>();
        CivDef civ = CivDef.fromJson(
                DEFAULT_CIV_ID,
                JsonParser.parseString(DEFAULT_CIV_JSON).getAsJsonObject(),
                "builtin/civs/default.json",
                problems);
        if (civ == null) {
            throw new IllegalStateException("the builtin civilisation does not decode: " + problems);
        }
        return civ;
    }

    private static String canonicalCivs(CivDef civ) {
        JsonObject o = new JsonObject();
        o.add(civ.id().value(), civ.toJson());
        return CanonicalJson.write(o);
    }

    private BuiltinTemplates() {}

    static TemplateSet compile() {
        return compile(true);
    }

    /**
     * The set with its techs (the wood tech and the phases Village and Town), the class {@code Village} and the
     * {@code Researcher} of the storehouse and the civic centre, or without all of them, as it was before TECH-01.
     */
    static TemplateSet compile(boolean withTechs) {
        List<RawTemplate> templates = new ArrayList<>(List.of(
                raw("strategos:template/unit_base", UNIT_BASE, withTechs),
                raw("strategos:template/unit_soldier", UNIT_SOLDIER, withTechs),
                raw("strategos:template/unit_infantry", UNIT_INFANTRY, withTechs),
                raw("strategos:template/unit_fauna", UNIT_FAUNA),
                raw("strategos:template/building_base", BUILDING_BASE, withTechs),
                raw("strategos:unit/debug_walker", DEBUG_WALKER),
                raw("strategos:unit/infantry", INFANTRY, withTechs),
                raw("strategos:unit/archer", ARCHER, withTechs),
                raw("strategos:unit/horseman", HORSEMAN, withTechs),
                raw("strategos:unit/worker", WORKER, withTechs),
                raw("strategos:unit/sheep", SHEEP),
                raw("strategos:unit/pig", PIG),
                raw("strategos:unit/chicken", CHICKEN),
                raw("strategos:building/civic_centre", CIVIC_CENTRE, withTechs),
                raw("strategos:building/field", FIELD, withTechs),
                raw("strategos:building/house", HOUSE, withTechs),
                raw("strategos:building/barracks", BARRACKS, withTechs),
                raw("strategos:building/storehouse", STOREHOUSE, withTechs),
                raw("strategos:building/farmstead", FARMSTEAD, withTechs),
                raw("strategos:building/quarry", QUARRY),
                raw("strategos:building/quarry_large", QUARRY_LARGE),
                raw("strategos:building/iron_mine", IRON_MINE),
                raw("strategos:building/iron_mine_large", IRON_MINE_LARGE)));
        if (withTechs) {
            templates.add(raw("strategos:building/tower", TOWER)); // on the build list Town adds (BLD-17)
            templates.add(raw("strategos:building/arsenal", ARSENAL)); // M6 T19
            templates.add(raw("strategos:unit/ram", RAM));
            templates.add(
                    raw("strategos:building/sentry_tower", SENTRY_TOWER)); // on the Village worker's list (BLD-12)
            templates.add(raw("strategos:building/palisade", PALISADE)); // BLD-13
            templates.add(raw("strategos:building/palisade_gate", PALISADE_GATE));
            templates.add(raw("strategos:building/temple", TEMPLE)); // CMB-12
            templates.add(raw("strategos:unit/healer", HEALER));
            templates.add(raw("strategos:unit/wild_boar", WILD_BOAR)); // ECO-10
        }
        TemplateCompiler.Result result = TemplateCompiler.compile(
                templates,
                withTechs
                        ? List.of(
                                tech("strategos:gather_wood_1", GATHER_WOOD_1),
                                tech("strategos:gather_farm_1", GATHER_FARM_1),
                                tech("strategos:gather_stone_1", GATHER_STONE_1),
                                tech("strategos:gather_iron_1", GATHER_IRON_1),
                                tech("strategos:carry_1", CARRY_1),
                                tech("strategos:iron_weapons", IRON_WEAPONS),
                                tech("strategos:chainmail", CHAINMAIL),
                                tech("strategos:phase_town", PHASE_TOWN),
                                tech("strategos:gather_wood_2", GATHER_WOOD_2),
                                tech("strategos:gather_wood_3", GATHER_WOOD_3),
                                tech("strategos:gather_farm_2", GATHER_FARM_2),
                                tech("strategos:gather_farm_3", GATHER_FARM_3),
                                tech("strategos:gather_stone_2", GATHER_STONE_2),
                                tech("strategos:gather_stone_3", GATHER_STONE_3),
                                tech("strategos:gather_iron_2", GATHER_IRON_2),
                                tech("strategos:gather_iron_3", GATHER_IRON_3),
                                tech("strategos:carry_2", CARRY_2),
                                tech("strategos:carry_3", CARRY_3),
                                tech("strategos:diamond_weapons", DIAMOND_WEAPONS),
                                tech("strategos:netherite_weapons", NETHERITE_WEAPONS),
                                tech("strategos:iron_armour", IRON_ARMOUR),
                                tech("strategos:diamond_armour", DIAMOND_ARMOUR),
                                tech("strategos:masonry_1", MASONRY_1),
                                tech("strategos:masonry_2", MASONRY_2),
                                tech("strategos:arrow_slits", ARROW_SLITS),
                                tech("strategos:carpentry", CARPENTRY),
                                tech("strategos:guild_lumber", GUILD_LUMBER),
                                tech("strategos:guild_mining", GUILD_MINING),
                                tech("strategos:phalanx", PHALANX),
                                tech("strategos:skirmish_drill", SKIRMISH_DRILL),
                                tech("strategos:phase_city", PHASE_CITY),
                                tech("strategos:phase_village", PHASE_VILLAGE))
                        : List.of(),
                withTechs ? List.of(defaultCivFile()) : List.of(),
                PINNED,
                PINNED_TECHS,
                PlaceholderBlueprints.byId());
        if (!result.ok()) {
            throw new IllegalStateException("the builtin templates do not compile: " + result.problems());
        }
        return result.set();
    }

    private static RawCiv defaultCivFile() {
        return new RawCiv(
                DEFAULT_CIV_ID,
                "builtin/civs/default.json",
                JsonParser.parseString(DEFAULT_CIV_JSON).getAsJsonObject());
    }

    private static RawTech tech(String id, String json) {
        return new RawTech(
                new TechId(id),
                "builtin/techs/" + id.substring(id.indexOf(':') + 1) + ".json",
                JsonParser.parseString(json).getAsJsonObject());
    }

    /**
     * A template as the set had it before TECH-01 without {@code withTechs}: no {@code Researcher}, no {@code Look}
     * (TECH-03), and none of the class {@code Village} (TECH-02), so that set keeps its version.
     */
    private static RawTemplate raw(String id, String json, boolean withTechs) {
        JsonObject object = JsonParser.parseString(json).getAsJsonObject();
        if (!withTechs) {
            object.remove("Researcher");
            object.remove("Look"); // TECH-03's tiers, which only the techs raise
            object.remove("Abilities"); // UNIT-10's, long after that pin
            object.remove("Promotion"); // CMB-11's ranks and values, likewise
            object.remove("Loot"); // CMB-13's, too
            if (object.has("Vision")) { // MATCH-16's night factor and light radii
                JsonObject vision = object.getAsJsonObject("Vision");
                vision.remove("night");
                vision.remove("light");
                if (vision.size() == 0) {
                    object.remove("Vision");
                }
            }
            if (object.has("Builder")) {
                object.getAsJsonObject("Builder").remove("buildings"); // the build list the phases add to (BLD-17)
            }
            JsonObject identity = object.getAsJsonObject("Identity");
            if (identity != null) { // the soldier base (CMB-11) names no classes
                identity.addProperty(
                        "classes",
                        identity.get("classes")
                                .getAsString()
                                .replace(" +Village", "")
                                .replace(" +Town", "")
                                .replace(" +Hunter", "")); // ECO-10's
            }
            if (object.has("ResourceGatherer")) { // ECO-10's hunt rate, and the cavalry's whole gatherer block
                JsonObject gatherer = object.getAsJsonObject("ResourceGatherer");
                JsonObject food = gatherer.getAsJsonObject("rates").getAsJsonObject("food");
                food.remove("hunt");
                if (food.size() == 0) {
                    object.remove("ResourceGatherer");
                }
            }
        }
        String path = id.substring(id.indexOf(':') + 1);
        return new RawTemplate(new TemplateId(id), "builtin/" + path + ".json", object);
    }

    private static RawTemplate raw(String id, String json) {
        JsonObject object = JsonParser.parseString(json).getAsJsonObject();
        String path = id.substring(id.indexOf(':') + 1);
        return new RawTemplate(new TemplateId(id), "builtin/" + path + ".json", object);
    }
}
