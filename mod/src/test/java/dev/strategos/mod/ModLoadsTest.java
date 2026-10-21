package dev.strategos.mod;

import static org.assertj.core.api.Assertions.assertThat;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import org.junit.jupiter.api.Test;

class ModLoadsTest {

    @Test
    void loadsTheMod() {
        assertThat(ModList.get().isLoaded(Strategos.MOD_ID)).isTrue();
    }

    @Test
    void runsAsADedicatedServer() {
        assertThat(FMLEnvironment.dist).isEqualTo(Dist.DEDICATED_SERVER);
    }
}
