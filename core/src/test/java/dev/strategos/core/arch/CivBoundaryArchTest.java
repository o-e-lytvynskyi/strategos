package dev.strategos.core.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * CIV-05 (M10 spec 1.6 row 14): the systems reach a civilisation's rules and roles through {@code FactionCivs}, {@code
 * CivRules} and the AI's view, never through {@code CivId} or {@code CivDef}. The match config, the state hasher, the
 * scenario setup, content, protocol and client read them by design and are outside the rule.
 */
class CivBoundaryArchTest {
    private static final JavaClasses CORE = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("dev.strategos.core");

    @Test
    void theSystemsDoNotDependOnCivIdentity() {
        noClasses()
                .that()
                .resideInAnyPackage(
                        "dev.strategos.core.sim.econ..",
                        "dev.strategos.core.sim.build..",
                        "dev.strategos.core.sim.prod..",
                        "dev.strategos.core.sim.unitai..",
                        "dev.strategos.core.sim.ai..",
                        "dev.strategos.core.sim.combat..",
                        "dev.strategos.core.sim.territory..",
                        "dev.strategos.core.sim.capture..",
                        "dev.strategos.core.sim.garrison..",
                        "dev.strategos.core.sim.vision..",
                        "dev.strategos.core.sim.nav..",
                        "dev.strategos.core.sim.move..",
                        "dev.strategos.core.sim.order..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("dev.strategos.core.shared.CivId")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName("dev.strategos.core.content.CivDef")
                .because("a system reads a civilisation through FactionCivs, CivRules or AiView (ADR-0028)")
                .check(CORE);
    }
}
