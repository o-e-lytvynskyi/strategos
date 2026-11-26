package dev.strategos.core.content;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CivilisationArtTest {
    @Test
    void romanMasonryCoversEveryBuiltStructureWithoutChangingFieldOrDeposits() {
        for (Blueprint blueprint : PlaceholderBlueprints.all()) {
            boolean themed = switch (blueprint.id()) {
                case PlaceholderBlueprints.FIELD,
                        PlaceholderBlueprints.QUARRY,
                        PlaceholderBlueprints.QUARRY_LARGE,
                        PlaceholderBlueprints.IRON_MINE,
                        PlaceholderBlueprints.IRON_MINE_LARGE -> false;
                default -> true;
            };
            assertThat(blueprint.palette().stream()
                            .anyMatch(state -> !state.equals(
                                    CivilisationArt.material(CivilisationArt.ROMAN, blueprint.id(), state))))
                    .as(blueprint.id())
                    .isEqualTo(themed);
        }
    }

    @Test
    void neutralAndCustomMaterialsRetainTheirOriginalState() {
        assertThat(CivilisationArt.material(
                        CivilisationArt.PLACEHOLDER, PlaceholderBlueprints.TOWER, "minecraft:stone_bricks"))
                .isEqualTo("minecraft:stone_bricks");
        assertThat(CivilisationArt.material(CivilisationArt.ROMAN, "custom:tower", "minecraft:stone_bricks"))
                .isEqualTo("minecraft:stone_bricks");
        assertThat(CivilisationArt.material(
                        CivilisationArt.ROMAN, PlaceholderBlueprints.CIVIC_CENTRE, PlaceholderBlueprints.BELL_BLOCK))
                .isEqualTo(PlaceholderBlueprints.BELL_BLOCK);
    }
}
