package dev.strategos.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import dev.strategos.mod.archfixture.ClientReferenceFixture;
import dev.strategos.mod.archfixture.DevCodeReferenceFixture;
import org.junit.jupiter.api.Test;

/**
 * The mod rules of M0 spec 7.1 over the shipped classes of mod and core. Each test checks the code base, then proves
 * the rule still bites on its own violating fixture. T3 brings B5 and B11; T11 brings the rest.
 */
class ModArchitectureTest {
    private static final JavaClasses SHIPPED = ArchClasses.load();

    private static final ArchRule COMMON_CODE_NEVER_TOUCHES_CLIENT_CLASSES = noClasses()
            .that()
            .resideOutsideOfPackage("dev.strategos.mod.client..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("net.minecraft.client..", "com.mojang.blaze3d..", "net.neoforged.neoforge.client..")
            .because("a dedicated server has no client classes (B5)");

    private static final ArchRule MAIN_NEVER_DEPENDS_ON_DEV_CODE = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "dev.strategos.gametest..",
                    "dev.strategos.devtools..",
                    "dev.strategos.core.testkit..",
                    "dev.strategos.arch..")
            .because("dev-only code never ships in the jar (B11)");

    @Test
    void commonCodeNeverTouchesClientClasses() {
        COMMON_CODE_NEVER_TOUCHES_CLIENT_CLASSES.check(SHIPPED);
        assertBites(COMMON_CODE_NEVER_TOUCHES_CLIENT_CLASSES, ClientReferenceFixture.class);
    }

    @Test
    void mainNeverDependsOnDevCode() {
        MAIN_NEVER_DEPENDS_ON_DEV_CODE.check(SHIPPED);
        assertBites(MAIN_NEVER_DEPENDS_ON_DEV_CODE, DevCodeReferenceFixture.class);
    }

    private static void assertBites(ArchRule rule, Class<?> fixture) {
        assertTrue(
                rule.evaluate(ArchClasses.fixtures(fixture)).hasViolation(),
                () -> "the rule no longer flags its fixture " + fixture.getName() + ": " + rule.getDescription());
    }
}
