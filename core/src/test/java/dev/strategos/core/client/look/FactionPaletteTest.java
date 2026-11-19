package dev.strategos.core.client.look;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.client.hud.HudTheme;
import dev.strategos.core.protocol.FactionPresentation;
import java.util.List;
import org.junit.jupiter.api.Test;

class FactionPaletteTest {
    @Test
    void legacyColoursAndShieldDyesRemainExactlyTheReleasedValues() {
        var palette = new FactionPalette(FactionPresentation.legacy(16));
        for (int i = 0; i < 16; i++) {
            assertThat(palette.colour(i)).isEqualTo(HudTheme.DEFAULT.faction(i));
            assertThat(palette.dyeName(i)).isEqualTo(UnitLook.dyeName(i));
        }
    }

    @Test
    void chosenColoursFollowThePaletteRatherThanTheFactionOrPlayerNumber() {
        var palette = new FactionPalette(new FactionPresentation(List.of(
                new FactionPresentation.Style("strategos:placeholder", 14),
                new FactionPresentation.Style("strategos:placeholder", 11))));
        assertThat(palette.colour(0)).isEqualTo(0xFFB02E26);
        assertThat(palette.dyeName(0)).isEqualTo("red");
        assertThat(palette.colour(1)).isEqualTo(0xFF3C44AA);
        assertThat(palette.dyeName(1)).isEqualTo("blue");
        assertThat(palette.colour(8)).isEqualTo(HudTheme.DEFAULT.faction(8));
    }

    @Test
    void tintingEquipmentPreservesRoleAndTierChoices() {
        var base = UnitLook.of(List.of("Organic", "Infantry"), 0, 1, 1);
        var tinted = base.tinted(0xB02E26, "red");
        assertThat(tinted.dye()).isEqualTo(0xB02E26);
        assertThat(tinted.shieldDye()).isEqualTo("red");
        assertThat(tinted.head()).isEqualTo(base.head());
        assertThat(tinted.chest()).isEqualTo(base.chest());
        assertThat(tinted.legs()).isEqualTo(base.legs());
        assertThat(tinted.rest()).isEqualTo(base.rest());
        assertThat(tinted.shield()).isEqualTo(base.shield());
    }
}
