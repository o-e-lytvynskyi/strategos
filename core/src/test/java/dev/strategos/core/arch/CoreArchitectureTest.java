package dev.strategos.core.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import dev.strategos.core.content.archfixture.CycleB;
import dev.strategos.core.diag.archfixture.CycleA;
import dev.strategos.core.protocol.archfixture.SimReferenceFixture;
import dev.strategos.core.shared.archfixture.ForeignLibraryFixture;
import dev.strategos.core.shared.archfixture.unmarked.UnmarkedPackageFixture;
import dev.strategos.core.sim.archfixture.ClientReferenceFixture;
import dev.strategos.core.sim.archfixture.DeterminismFixtures;
import dev.strategos.core.sim.archfixture.HotPathFixtures;
import dev.strategos.core.sim.archfixture.LoggerFactoryFixture;
import dev.strategos.core.sim.archfixture.StaticStateFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The core rules of M0 spec 7.1 over core's shipped classes (no tests, no test kit). Each test checks the code base,
 * then proves the rule still bites on its own violating fixtures, which live in packages the rule selects but outside
 * the production import (QA-17.1, CORE-05.2).
 */
class CoreArchitectureTest {
    private static final JavaClasses CORE = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(location -> !location.contains("/dev/strategos/core/testkit/"))
            .importPackages("dev.strategos.core");

    /** Deterministic code (04 §2.4): Rng, DetMath and Hasher live in shared, so it is covered too. */
    private static final String[] DETERMINISTIC = {
        "dev.strategos.core.shared..",
        "dev.strategos.core.protocol..",
        "dev.strategos.core.content..",
        "dev.strategos.core.sim.."
    };

    private static final String[] BOXED = {
        "java.lang.Integer",
        "java.lang.Long",
        "java.lang.Double",
        "java.lang.Float",
        "java.lang.Short",
        "java.lang.Byte",
        "java.lang.Character",
        "java.lang.Boolean"
    };

    static final ArchRule CORE_DEPENDS_ONLY_ON_ALLOWED_LIBRARIES = classes()
            .that()
            .resideInAPackage("dev.strategos.core..")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage(
                    "dev.strategos.core..",
                    "java..",
                    "com.mojang.serialization..",
                    "com.mojang.datafixers..",
                    "it.unimi.dsi.fastutil..",
                    "com.google.gson..",
                    "org.slf4j..",
                    "org.jspecify..")
            .because("core runs without Minecraft and uses only what the game ships (B1)");

    static final ArchRule SIM_DOES_NOT_DEPEND_ON_CLIENT = noClasses()
            .that()
            .resideInAPackage("dev.strategos.core.sim..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("dev.strategos.core.client..")
            .because("the simulation never sees client prediction (B3)");

    // core.client is empty in M0.
    static final ArchRule CLIENT_DOES_NOT_DEPEND_ON_SIM = noClasses()
            .that()
            .resideInAPackage("dev.strategos.core.client..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("dev.strategos.core.sim..")
            .allowEmptyShould(true)
            .because("client prediction never reaches into the simulation (B3)");

    static final ArchRule LOWER_LAYERS_DO_NOT_DEPEND_ON_SIM = noClasses()
            .that()
            .resideInAnyPackage(
                    "dev.strategos.core.shared..",
                    "dev.strategos.core.port..",
                    "dev.strategos.core.diag..",
                    "dev.strategos.core.protocol..",
                    "dev.strategos.core.content..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("dev.strategos.core.sim..")
            .because("the simulation sits on top of the core's layers (B3)");

    static final ArchRule CORE_SLICES_ARE_FREE_OF_CYCLES =
            slices().matching("dev.strategos.core.(*)..").should().beFreeOfCycles();

    static final ArchRule NO_WALL_CLOCK_IN_DETERMINISTIC_CODE = noClasses()
            .that()
            .resideInAnyPackage(
                    "dev.strategos.core.shared..",
                    "dev.strategos.core.protocol..",
                    "dev.strategos.core.content..",
                    "dev.strategos.core.sim..",
                    "dev.strategos.core.diag..")
            .should()
            .callMethodWhere(callTo("java.lang.System", "currentTimeMillis", "nanoTime"))
            .orShould()
            .dependOnClassesThat()
            .resideInAPackage("java.time..")
            .because("time reaches the core only through Clock (B4, 04 §2.2)");

    static final ArchRule NO_UNSEEDED_RANDOMNESS = noClasses()
            .that()
            .resideInAnyPackage(DETERMINISTIC)
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("java.util.Random")
            .orShould()
            .dependOnClassesThat()
            .haveFullyQualifiedName("java.util.concurrent.ThreadLocalRandom")
            .orShould()
            .dependOnClassesThat()
            .haveFullyQualifiedName("java.util.SplittableRandom")
            .orShould()
            .dependOnClassesThat()
            .haveFullyQualifiedName("java.security.SecureRandom")
            .orShould()
            .callMethodWhere(callTo("java.lang.Math", "random"))
            .because("all randomness comes from the match's seeded Rng streams (B4)");

    static final ArchRule NO_TRANSCENDENTAL_MATH_OUTSIDE_DET_MATH = noClasses()
            .that()
            .resideInAnyPackage(DETERMINISTIC)
            .should()
            .callMethodWhere(callTo(
                    "java.lang.Math",
                    "sin",
                    "cos",
                    "tan",
                    "asin",
                    "acos",
                    "atan",
                    "atan2",
                    "exp",
                    "log",
                    "log10",
                    "log1p",
                    "expm1",
                    "pow",
                    "cbrt",
                    "hypot",
                    "sinh",
                    "cosh",
                    "tanh"))
            .because("Math may differ across JVMs and platforms; DetMath is exact (B4)");

    static final ArchRule NO_HASH_ORDERED_COLLECTIONS = noClasses()
            .that()
            .resideInAnyPackage(DETERMINISTIC)
            .should()
            .dependOnClassesThat()
            .haveNameMatching(
                    "java\\.util\\.(HashMap|HashSet|IdentityHashMap|WeakHashMap|Hashtable)|java\\.util\\.concurrent\\.ConcurrentHashMap")
            .orShould()
            .callMethodWhere(callTo("java.util.Set", "of", "copyOf"))
            .orShould()
            .callMethodWhere(callTo("java.util.Map", "of", "ofEntries", "copyOf"))
            .orShould()
            .callMethodWhere(callTo(
                    "java.util.stream.Collectors",
                    "toSet",
                    "toUnmodifiableSet",
                    "toMap",
                    "toUnmodifiableMap",
                    "groupingBy",
                    "groupingByConcurrent",
                    "toConcurrentMap"))
            .orShould()
            .callMethodWhere(callTo("java.lang.System", "identityHashCode"))
            .orShould()
            .dependOnClassesThat()
            .haveNameMatching(
                    "it\\.unimi\\.dsi\\.fastutil\\..*\\.(Object|Reference)(2\\w+)?(Open|OpenCustom)Hash(Map|Set)")
            .because("iteration order must not depend on hash codes or identity (B4)");

    static final ArchRule NO_THREADS_OR_PARALLEL_STREAMS = noClasses()
            .that()
            .resideInAnyPackage(DETERMINISTIC)
            .should()
            .dependOnClassesThat()
            .haveNameMatching(
                    "java\\.lang\\.Thread|java\\.util\\.concurrent\\.Executor.*|java\\.util\\.concurrent\\.(ForkJoinPool|CompletableFuture)")
            .orShould()
            .callMethodWhere(callNamed("parallel", "parallelStream"))
            .because("a match steps on one thread, in one order (B4)");

    static final ArchRule NO_STATIC_MUTABLE_STATE = fields().that()
            .areStatic()
            .and()
            .doNotHaveModifier(JavaModifier.SYNTHETIC)
            .and()
            .areDeclaredInClassesThat()
            .resideInAPackage("dev.strategos.core..")
            .should(beFinalAndImmutablyTyped())
            .because("matches share no state through statics (B8)");

    static final ArchRule HOT_PATHS_DO_NOT_STREAM_OR_BOX = noClasses()
            .that()
            .areAnnotatedWith("dev.strategos.core.shared.HotPath")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("java.util.stream..")
            .orShould()
            .callMethodWhere(boxingCall())
            .because("per-tick kernels neither stream nor box (B9)");

    static final ArchRule EVERY_PACKAGE_IS_NULL_MARKED =
            classes().should(resideInANullMarkedPackage()).because("NullAway checks only @NullMarked code (B13)");

    static final ArchRule SIM_LOGS_ONLY_THROUGH_MATCH_LOG = noClasses()
            .that()
            .resideInAPackage("dev.strategos.core.sim..")
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("org.slf4j.LoggerFactory")
            .because("every sim log line carries its match and tick (QA-10)");

    @Test
    void coreDependsOnlyOnAllowedLibraries() {
        CORE_DEPENDS_ONLY_ON_ALLOWED_LIBRARIES.check(CORE);
        assertBites(CORE_DEPENDS_ONLY_ON_ALLOWED_LIBRARIES, ForeignLibraryFixture.class);
    }

    @Test
    void simAndClientDoNotDependOnEachOther() {
        SIM_DOES_NOT_DEPEND_ON_CLIENT.check(CORE);
        CLIENT_DOES_NOT_DEPEND_ON_SIM.check(CORE);
        assertBites(SIM_DOES_NOT_DEPEND_ON_CLIENT, ClientReferenceFixture.class);
        assertBites(CLIENT_DOES_NOT_DEPEND_ON_SIM, dev.strategos.core.client.archfixture.SimReferenceFixture.class);
    }

    @Test
    void lowerLayersDoNotDependOnSim() {
        LOWER_LAYERS_DO_NOT_DEPEND_ON_SIM.check(CORE);
        assertBites(LOWER_LAYERS_DO_NOT_DEPEND_ON_SIM, SimReferenceFixture.class);
    }

    @Test
    void coreSlicesAreFreeOfCycles() {
        CORE_SLICES_ARE_FREE_OF_CYCLES.check(CORE);
        assertBites(CORE_SLICES_ARE_FREE_OF_CYCLES, CycleA.class, CycleB.class);
    }

    @Test
    void noWallClockInDeterministicCode() {
        NO_WALL_CLOCK_IN_DETERMINISTIC_CODE.check(CORE);
        assertEachBites(
                NO_WALL_CLOCK_IN_DETERMINISTIC_CODE,
                DeterminismFixtures.CurrentTimeMillis.class,
                DeterminismFixtures.NanoTime.class,
                DeterminismFixtures.InstantNow.class);
    }

    @Test
    void noUnseededRandomness() {
        NO_UNSEEDED_RANDOMNESS.check(CORE);
        assertEachBites(
                NO_UNSEEDED_RANDOMNESS,
                DeterminismFixtures.MathRandom.class,
                DeterminismFixtures.UtilRandom.class,
                DeterminismFixtures.ThreadLocalRandomUse.class,
                DeterminismFixtures.SplittableRandomUse.class,
                DeterminismFixtures.SecureRandomUse.class);
    }

    @Test
    void noTranscendentalMathOutsideDetMath() {
        NO_TRANSCENDENTAL_MATH_OUTSIDE_DET_MATH.check(CORE);
        assertEachBites(NO_TRANSCENDENTAL_MATH_OUTSIDE_DET_MATH, DeterminismFixtures.MathSin.class);
    }

    @Test
    void noHashOrderedCollections() {
        NO_HASH_ORDERED_COLLECTIONS.check(CORE);
        assertEachBites(
                NO_HASH_ORDERED_COLLECTIONS,
                DeterminismFixtures.HashMapUse.class,
                DeterminismFixtures.HashSetUse.class,
                DeterminismFixtures.IdentityHashMapUse.class,
                DeterminismFixtures.SetOf.class,
                DeterminismFixtures.MapOf.class,
                DeterminismFixtures.MapOfEntries.class,
                DeterminismFixtures.CollectorsToSet.class,
                DeterminismFixtures.CollectorsToMap.class,
                DeterminismFixtures.GroupingBy.class,
                DeterminismFixtures.ObjectOpenHashMap.class);
        assertPasses(NO_HASH_ORDERED_COLLECTIONS, DeterminismFixtures.LinkedCollections.class);
    }

    @Test
    void noThreadsOrParallelStreams() {
        NO_THREADS_OR_PARALLEL_STREAMS.check(CORE);
        assertEachBites(
                NO_THREADS_OR_PARALLEL_STREAMS,
                DeterminismFixtures.ThreadUse.class,
                DeterminismFixtures.ExecutorsUse.class,
                DeterminismFixtures.ParallelStream.class);
    }

    @Test
    void noStaticMutableState() {
        NO_STATIC_MUTABLE_STATE.check(CORE);
        assertEachBites(
                NO_STATIC_MUTABLE_STATE,
                StaticStateFixtures.NonFinal.class,
                StaticStateFixtures.FinalArray.class,
                StaticStateFixtures.FinalList.class,
                StaticStateFixtures.FinalMap.class,
                StaticStateFixtures.FinalAtomic.class,
                StaticStateFixtures.FinalFastutil.class);
        assertPasses(NO_STATIC_MUTABLE_STATE, StaticStateFixtures.Constants.class);
    }

    @Test
    void hotPathsDoNotStreamOrBox() {
        HOT_PATHS_DO_NOT_STREAM_OR_BOX.check(CORE);
        assertEachBites(HOT_PATHS_DO_NOT_STREAM_OR_BOX, HotPathFixtures.Streaming.class, HotPathFixtures.Boxing.class);
    }

    @Test
    void everyPackageIsNullMarked() {
        EVERY_PACKAGE_IS_NULL_MARKED.check(CORE);
        assertBites(EVERY_PACKAGE_IS_NULL_MARKED, UnmarkedPackageFixture.class);
    }

    @Test
    void simLogsOnlyThroughMatchLog() {
        SIM_LOGS_ONLY_THROUGH_MATCH_LOG.check(CORE);
        assertBites(SIM_LOGS_ONLY_THROUGH_MATCH_LOG, LoggerFactoryFixture.class);
    }

    /** A call to one of {@code names} declared by {@code owner}. */
    static DescribedPredicate<JavaMethodCall> callTo(String owner, String... names) {
        List<String> banned = List.of(names);
        return DescribedPredicate.describe(
                "a call to " + owner + "." + String.join("/", names),
                call -> call.getTargetOwner().getName().equals(owner) && banned.contains(call.getName()));
    }

    static DescribedPredicate<JavaMethodCall> callNamed(String... names) {
        List<String> banned = List.of(names);
        return DescribedPredicate.describe(
                "a call to a method named " + String.join("/", names), call -> banned.contains(call.getName()));
    }

    /** Boxing ({@code valueOf}) or unboxing ({@code intValue()} and the like) on a wrapper type. */
    static DescribedPredicate<JavaMethodCall> boxingCall() {
        List<String> boxed = List.of(BOXED);
        return DescribedPredicate.describe(
                "boxing or unboxing",
                call -> boxed.contains(call.getTargetOwner().getName())
                        && (call.getName().equals("valueOf") || call.getName().endsWith("Value")));
    }

    static ArchCondition<JavaField> beFinalAndImmutablyTyped() {
        return new ArchCondition<>("be final and of no array, collection, map, atomic or fastutil type") {
            @Override
            public void check(JavaField field, ConditionEvents events) {
                JavaClass type = field.getRawType();
                String problem = null;
                if (!field.getModifiers().contains(JavaModifier.FINAL)) {
                    problem = "is not final";
                } else if (type.isArray()) {
                    problem = "is an array";
                } else if (type.isAssignableTo(java.util.Collection.class)
                        || type.isAssignableTo(java.util.Map.class)) {
                    problem = "is a " + type.getSimpleName();
                } else if (type.getPackageName().startsWith("java.util.concurrent.atomic")
                        || type.getPackageName().startsWith("it.unimi.dsi.fastutil")) {
                    problem = "is a mutable " + type.getName();
                }
                if (problem != null) {
                    events.add(SimpleConditionEvent.violated(
                            field, "static field " + field.getFullName() + " " + problem));
                }
            }
        };
    }

    static ArchCondition<JavaClass> resideInANullMarkedPackage() {
        return new ArchCondition<>("reside in a @NullMarked package") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                if (!javaClass.getPackage().isAnnotatedWith("org.jspecify.annotations.NullMarked")) {
                    events.add(SimpleConditionEvent.violated(
                            javaClass,
                            javaClass.getName() + " resides in " + javaClass.getPackageName()
                                    + ", which has no @NullMarked package-info"));
                }
            }
        };
    }

    /** The rule flags the fixtures, imported together. */
    private static void assertBites(ArchRule rule, Class<?>... fixtures) {
        assertTrue(
                rule.evaluate(new ClassFileImporter().importClasses(fixtures)).hasViolation(),
                () -> "the rule no longer flags its fixture " + List.of(fixtures) + ": " + rule.getDescription());
    }

    /** The rule flags each fixture on its own: one per banned API. */
    private static void assertEachBites(ArchRule rule, Class<?>... fixtures) {
        for (Class<?> fixture : fixtures) {
            assertBites(rule, fixture);
        }
    }

    /** The rule lets the fixture through: what it allows stays allowed. */
    private static void assertPasses(ArchRule rule, Class<?> fixture) {
        assertTrue(
                !rule.evaluate(new ClassFileImporter().importClasses(fixture)).hasViolation(),
                () -> "the rule flags " + fixture.getName() + ", which it should allow: " + rule.getDescription());
    }
}
