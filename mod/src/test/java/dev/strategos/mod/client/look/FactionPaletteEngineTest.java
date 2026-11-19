package dev.strategos.mod.client.look;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.client.look.FactionPalette;
import dev.strategos.core.protocol.FactionPresentation;
import java.util.ArrayList;
import net.minecraft.world.item.DyeColor;
import org.junit.jupiter.api.Test;

class FactionPaletteEngineTest {
    @Test
    void allSelectedIdsMatchThePinnedEnginePaletteWithoutClientClasses() {
        var styles = new ArrayList<FactionPresentation.Style>();
        for (int i = 0; i < 16; i++) styles.add(new FactionPresentation.Style("strategos:placeholder", i));
        var palette = new FactionPalette(new FactionPresentation(styles));
        for (int i = 0; i < 16; i++) {
            assertThat(palette.colour(i) & 0xFFFFFF).isEqualTo(DyeColor.byId(i).getTextureDiffuseColor());
            assertThat(palette.dyeName(i)).isEqualTo(DyeColor.byId(i).getName());
        }
    }
}
