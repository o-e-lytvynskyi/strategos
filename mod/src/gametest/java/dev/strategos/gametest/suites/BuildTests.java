package dev.strategos.gametest.suites;

import dev.strategos.core.shared.Handle;
import dev.strategos.core.testkit.TestKit;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.mod.Strategos;
import net.minecraft.gametest.framework.GameTestHelper;

/** The build wiring, seen from inside the game: core and the test kit run inside the mod module (QA-01, S5). */
public final class BuildTests {
    private BuildTests() {}

    @StrategosTest
    public static void coreInModModule(GameTestHelper helper) {
        sameNamedModule(helper, Handle.class);
    }

    @StrategosTest
    public static void testfixturesInModModule(GameTestHelper helper) {
        sameNamedModule(helper, TestKit.class);
    }

    private static void sameNamedModule(GameTestHelper helper, Class<?> type) {
        Module module = type.getModule();
        Module mod = Strategos.class.getModule();
        if (!module.isNamed()) {
            helper.fail(type.getName() + " runs in an unnamed module");
        }
        if (module != mod) {
            helper.fail(type.getName() + " runs in module " + module.getName() + ", the mod in " + mod.getName());
        }
        helper.succeed();
    }
}
