package dev.strategos.core.client.look;

import dev.strategos.core.client.hud.HudTheme;
import java.util.List;

/**
 * A body's placeholder look (ART-01, the style guide's colour and role rules): every unit wears a leather tunic dyed
 * its faction's colour, the outline's colour, so the owner reads at a glance; the role reads from what it wears and
 * holds. A worker wears the tunic alone and holds the tools and loads its work gives it; a spearman wears an iron
 * helmet and dyed leggings too, holds a trident, the nearest vanilla spear, and a shield whose banner is its faction's
 * dye. An archer holds a bow and no shield, a horseman a sword with a shield in the off hand (UNIT-07). Item ids are vanilla's; an empty id is nothing.
 * The faction's equipment tiers (TECH-03, TECH-05) change the gear: a horseman's stone sword is iron at weapon tier 1,
 * diamond at 2 and netherite at 3 (the spearman's trident and the archer's bow are their own at every tier); the
 * leather tunic and leggings are chainmail at armour tier 1, vanilla's iron at 2 and diamond at 3, none dyed, so the
 * shield still carries the colour. {@code dye} is the leather's RGB, {@code shieldDye} the name of
 * the dye colour nearest the faction's. A body of no {@code Organic} class, the debug walker, is a test body and wears
 * nothing, so the goldens of M1 and M2 keep their look.
 */
public record UnitLook(
        String head, String chest, String legs, String feet, String rest, String shield, int dye, String shieldDye) {
    public static final String TUNIC = "minecraft:leather_chestplate";
    public static final String LEGGINGS = "minecraft:leather_leggings";
    public static final String CHAIN_TUNIC = "minecraft:chainmail_chestplate";
    public static final String CHAIN_LEGGINGS = "minecraft:chainmail_leggings";
    public static final String STONE_SWORD = "minecraft:stone_sword";
    public static final String IRON_SWORD = "minecraft:iron_sword";
    public static final String DIAMOND_SWORD = "minecraft:diamond_sword";
    public static final String NETHERITE_SWORD = "minecraft:netherite_sword";
    public static final String IRON_TUNIC = "minecraft:iron_chestplate";
    public static final String IRON_LEGGINGS = "minecraft:iron_leggings";
    public static final String DIAMOND_TUNIC = "minecraft:diamond_chestplate";
    public static final String DIAMOND_LEGGINGS = "minecraft:diamond_leggings";

    /** The look of a unit of template classes {@code classes} and faction {@code faction} at the starting tiers. */
    public static UnitLook of(List<String> classes, int faction) {
        return of(classes, faction, 0, 0);
    }

    /** The look at the faction's weapon tier and armour tier (TECH-03): 0 is the starting gear. */
    public static UnitLook of(List<String> classes, int faction, int weaponTier, int armourTier) {
        String tunic =
                armourTier >= 3 ? DIAMOND_TUNIC : armourTier == 2 ? IRON_TUNIC : armourTier == 1 ? CHAIN_TUNIC : TUNIC;
        String leggings = armourTier >= 3
                ? DIAMOND_LEGGINGS
                : armourTier == 2 ? IRON_LEGGINGS : armourTier == 1 ? CHAIN_LEGGINGS : LEGGINGS;
        String sword = weaponTier >= 3
                ? NETHERITE_SWORD
                : weaponTier == 2 ? DIAMOND_SWORD : weaponTier == 1 ? IRON_SWORD : STONE_SWORD;
        int dye = HudTheme.DEFAULT.faction(faction) & 0xFFFFFF;
        String shieldDye = dyeName(faction);
        if (!classes.contains("Organic")) {
            return new UnitLook("", "", "", "", "", "", dye, shieldDye);
        }
        if (classes.contains("Archer")) {
            return new UnitLook("", tunic, leggings, "", "minecraft:bow", "", dye, shieldDye);
        }
        if (classes.contains("Cavalry")) {
            return new UnitLook("minecraft:iron_helmet", tunic, "", "", sword, "minecraft:shield", dye, shieldDye);
        }
        if (classes.contains("Spearman") || classes.contains("Infantry")) {
            return new UnitLook(
                    "minecraft:iron_helmet",
                    tunic,
                    leggings,
                    "",
                    "minecraft:trident",
                    "minecraft:shield",
                    dye,
                    shieldDye);
        }
        return new UnitLook("", TUNIC, "", "", "", "", dye, shieldDye); // a worker has no tiers
    }

    /** The vanilla dye nearest faction {@code faction}'s colour, by name: the banner's base on its shields. */
    public static String dyeName(int faction) {
        return switch (faction) {
            case 0 -> "blue";
            case 1 -> "red";
            case 2 -> "lime";
            case 3 -> "yellow";
            case 4 -> "purple";
            case 5 -> "orange";
            case 6 -> "cyan";
            default -> "white";
        };
    }
}
