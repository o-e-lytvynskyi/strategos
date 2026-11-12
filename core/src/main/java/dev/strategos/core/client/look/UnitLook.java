package dev.strategos.core.client.look;

import dev.strategos.core.client.hud.HudTheme;
import java.util.List;

/**
 * A body's placeholder look (ART-01, the style guide's colour and role rules): every unit wears a leather tunic dyed
 * its faction's colour, the outline's colour, so the owner reads at a glance; the role reads from what it wears and
 * holds. A worker wears the tunic alone and holds the tools and loads its work gives it; a spearman wears an iron
 * helmet and dyed leggings too, holds a trident, the nearest vanilla spear, and a shield whose banner is its faction's
 * dye. An archer holds a bow and no shield, a horseman a sword with a shield in the off hand (UNIT-07). Item ids are vanilla's; an empty id is nothing. {@code dye} is the leather's RGB, {@code shieldDye} the name of
 * the dye colour nearest the faction's. A body of no {@code Organic} class, the debug walker, is a test body and wears
 * nothing, so the goldens of M1 and M2 keep their look.
 */
public record UnitLook(
        String head, String chest, String legs, String feet, String rest, String shield, int dye, String shieldDye) {
    public static final String TUNIC = "minecraft:leather_chestplate";
    public static final String LEGGINGS = "minecraft:leather_leggings";

    /** The look of a unit of template classes {@code classes} and faction {@code faction}. */
    public static UnitLook of(List<String> classes, int faction) {
        int dye = HudTheme.DEFAULT.faction(faction) & 0xFFFFFF;
        String shieldDye = dyeName(faction);
        if (!classes.contains("Organic")) {
            return new UnitLook("", "", "", "", "", "", dye, shieldDye);
        }
        if (classes.contains("Archer")) {
            return new UnitLook("", TUNIC, LEGGINGS, "", "minecraft:bow", "", dye, shieldDye);
        }
        if (classes.contains("Cavalry")) {
            return new UnitLook(
                    "minecraft:iron_helmet", TUNIC, "", "", "minecraft:iron_sword", "minecraft:shield", dye, shieldDye);
        }
        if (classes.contains("Spearman") || classes.contains("Infantry")) {
            return new UnitLook(
                    "minecraft:iron_helmet",
                    TUNIC,
                    LEGGINGS,
                    "",
                    "minecraft:trident",
                    "minecraft:shield",
                    dye,
                    shieldDye);
        }
        return new UnitLook("", TUNIC, "", "", "", "", dye, shieldDye);
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
