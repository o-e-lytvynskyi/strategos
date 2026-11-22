package dev.strategos.core.content;

import dev.strategos.core.protocol.FactionPresentation;

/** Material palette for the original Roman art on the built-in blueprints. */
public final class CivilisationArt {
    public static final String PLACEHOLDER = "strategos:placeholder";
    public static final String ROMAN = "strategos:roman";

    private CivilisationArt() {}

    public static boolean isRoman(FactionPresentation factions, int faction) {
        return faction >= 0
                && faction < factions.styles().size()
                && ROMAN.equals(factions.style(faction).civilisation());
    }

    /** Maps only full, solid building blocks. Footprints, door and spawn markers, and crop states are shared. */
    public static String material(String civilisation, String blueprint, String state) {
        if (!ROMAN.equals(civilisation)) {
            return state;
        }
        return switch (blueprint) {
            case PlaceholderBlueprints.CIVIC_CENTRE,
                    PlaceholderBlueprints.HOUSE,
                    PlaceholderBlueprints.BARRACKS,
                    PlaceholderBlueprints.STOREHOUSE,
                    PlaceholderBlueprints.FARMSTEAD,
                    PlaceholderBlueprints.TOWER,
                    PlaceholderBlueprints.ARSENAL -> romanMasonry(state);
            default -> state;
        };
    }

    private static String romanMasonry(String state) {
        return switch (state) {
            case "minecraft:cobblestone", "minecraft:packed_mud" -> "minecraft:smooth_sandstone";
            case "minecraft:stone_bricks", "minecraft:mud_bricks" -> "minecraft:cut_sandstone";
            case "minecraft:smooth_stone", "minecraft:polished_andesite" -> "minecraft:smooth_quartz";
            case "minecraft:chiseled_stone_bricks" -> "minecraft:quartz_pillar";
            case "minecraft:crimson_planks", "minecraft:terracotta" -> "minecraft:red_terracotta";
            default -> state;
        };
    }
}
