package dev.strategos.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static dev.strategos.arch.ArchPredicates.beFinalAndImmutablyTyped;
import static dev.strategos.arch.ArchPredicates.boxingCall;
import static dev.strategos.arch.ArchPredicates.callTo;
import static dev.strategos.arch.ArchPredicates.callToAny;
import static dev.strategos.arch.ArchPredicates.onlyUseTheCoreApi;
import static dev.strategos.arch.ArchPredicates.resideInANullMarkedPackage;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchRule;
import dev.strategos.mod.archfixture.BlockWriteFixtures;
import dev.strategos.mod.archfixture.ClientReferenceFixture;
import dev.strategos.mod.archfixture.CoreInternalsFixture;
import dev.strategos.mod.archfixture.DevCodeReferenceFixture;
import dev.strategos.mod.archfixture.HotPathFixtures;
import dev.strategos.mod.archfixture.ModClientReferenceFixture;
import dev.strategos.mod.archfixture.SendFixtures;
import dev.strategos.mod.archfixture.StaticStateFixtures;
import dev.strategos.mod.archfixture.ThreadFixture;
import dev.strategos.mod.archfixture.unmarked.UnmarkedPackageFixture;
import dev.strategos.mod.mixin.archfixture.RoutingFixture;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The mod rules of M0 spec 7.1 over the shipped classes of mod and core. Each test checks the code base, then proves
 * the rule still bites on its own violating fixtures (QA-17.1). Minecraft and NeoForge are not on this classpath, so
 * the rules name their types, and the fixtures use same-named stubs.
 */
class ModArchitectureTest {
    private static final JavaClasses SHIPPED = ArchClasses.load();

    private static final ArchRule MOD_USES_ONLY_CORE_API = classes()
            .that()
            .resideInAPackage("dev.strategos.mod..")
            .should(onlyUseTheCoreApi())
            .because("the core's internals stay free to change (B2)");

    private static final ArchRule COMMON_CODE_NEVER_TOUCHES_CLIENT_CLASSES = noClasses()
            .that()
            .resideOutsideOfPackage("dev.strategos.mod.client..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("net.minecraft.client..", "com.mojang.blaze3d..", "net.neoforged.neoforge.client..")
            .because("a dedicated server has no client classes (B5)");

    private static final ArchRule COMMON_CODE_NEVER_REFERENCES_MOD_CLIENT = noClasses()
            .that()
            .resideOutsideOfPackage("dev.strategos.mod.client..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("dev.strategos.mod.client..")
            .because("the mod's client code loads on clients only (B5)");

    private static final ArchRule ONLY_NET_GATEWAY_SENDS = noClasses()
            .that()
            .doNotHaveFullyQualifiedName("dev.strategos.mod.server.net.NetGateway")
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("net.neoforged.neoforge.network.PacketDistributor")
            .orShould()
            .callMethodWhere(callToAny(
                    List.of(
                            "net.minecraft.network.Connection",
                            "net.minecraft.server.network.ServerCommonPacketListenerImpl",
                            "net.minecraft.server.network.ServerGamePacketListenerImpl"),
                    "send"::equals))
            .orShould()
            .callMethodWhere(callTo("net.minecraft.server.level.ServerChunkCache", "broadcast", "broadcastAndSend"))
            .orShould()
            .callMethodWhere(
                    callToAny(List.of("net.minecraft.server.players.PlayerList"), name -> name.startsWith("broadcast")))
            .because("one gateway sends, so budgets and ordering hold (B6)");

    private static final ArchRule ONLY_BLOCK_WRITER_WRITES_BLOCKS = noClasses()
            .that()
            .doNotHaveFullyQualifiedName("dev.strategos.mod.server.world.BlockWriter")
            .should()
            .callMethodWhere(DescribedPredicate.describe(
                    "a block write on a Minecraft owner",
                    call -> (call.getTargetOwner().getName().startsWith("net.minecraft.")
                                    && List.of("setBlock", "setBlockAndUpdate", "removeBlock", "destroyBlock")
                                            .contains(call.getName()))
                            || (call.getTargetOwner().getPackageName().startsWith("net.minecraft.world.level.chunk")
                                    && call.getName().equals("setBlockState"))))
            .because("every block change goes through the core's world port (B7)");

    private static final ArchRule NO_STATIC_MUTABLE_STATE_EXCEPT_HOLDERS = fields().that()
            .areStatic()
            .and()
            .doNotHaveModifier(JavaModifier.SYNTHETIC)
            .and()
            .areDeclaredInClassesThat()
            .resideInAPackage("dev.strategos.mod..")
            .and()
            .areDeclaredInClassesThat()
            .doNotHaveFullyQualifiedName("dev.strategos.mod.StrategosServer")
            .and()
            .areDeclaredInClassesThat()
            .doNotHaveFullyQualifiedName("dev.strategos.mod.client.StrategosClient")
            .should(beFinalAndImmutablyTyped())
            .because("an integrated server restarts in the same JVM; only the holders keep state (B8)");

    // mod.server.jobs is empty in M0; the rule covers every other mod class.
    private static final ArchRule THREADS_ONLY_IN_SERVER_JOBS = noClasses()
            .that()
            .resideInAPackage("dev.strategos.mod..")
            .and()
            .resideOutsideOfPackage("dev.strategos.mod.server.jobs..")
            .should()
            .dependOnClassesThat()
            .haveNameMatching(
                    "java\\.lang\\.Thread|java\\.util\\.concurrent\\.Executor.*|java\\.util\\.concurrent\\.ForkJoinPool")
            .because("the game's state is touched on the server thread only (B4)");

    private static final ArchRule HOT_PATHS_DO_NOT_STREAM_OR_BOX = noClasses()
            .that()
            .areAnnotatedWith("dev.strategos.core.shared.HotPath")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("java.util.stream..")
            .orShould()
            .callMethodWhere(boxingCall())
            .because("per-tick kernels neither stream nor box (B9)");

    // The mixin package is empty in M0.
    private static final ArchRule MIXINS_ONLY_ROUTE = classes()
            .that()
            .resideInAPackage("dev.strategos.mod.mixin..")
            .should()
            .onlyDependOnClassesThat(DescribedPredicate.describe(
                    "are platform classes or one of the routers",
                    (JavaClass c) -> List.of("BlockChangeRouter", "FogGate", "CommanderGuard")
                                    .contains(c.getSimpleName())
                            || startsWithAny(
                                    c.getPackageName(),
                                    "java",
                                    "org.spongepowered",
                                    "com.llamalad7.mixinextras",
                                    "net.minecraft",
                                    "net.neoforged",
                                    "dev.strategos.mod.mixin")))
            .allowEmptyShould(true)
            .because("a mixin only hands the event to a router (B10)");

    private static final ArchRule MAIN_NEVER_DEPENDS_ON_DEV_CODE = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "dev.strategos.gametest..",
                    "dev.strategos.devtools..",
                    "dev.strategos.core.testkit..",
                    "dev.strategos.arch..")
            .because("dev-only code never ships in the jar (B11)");

    private static final ArchRule EVERY_PACKAGE_IS_NULL_MARKED =
            classes().should(resideInANullMarkedPackage()).because("NullAway checks only @NullMarked code (B13)");

    @Test
    void modUsesOnlyCoreApi() {
        MOD_USES_ONLY_CORE_API.check(SHIPPED);
        assertBites(MOD_USES_ONLY_CORE_API, CoreInternalsFixture.class);
    }

    @Test
    void commonCodeNeverTouchesClientClasses() {
        COMMON_CODE_NEVER_TOUCHES_CLIENT_CLASSES.check(SHIPPED);
        assertBites(COMMON_CODE_NEVER_TOUCHES_CLIENT_CLASSES, ClientReferenceFixture.class);
    }

    @Test
    void commonCodeNeverReferencesModClient() {
        COMMON_CODE_NEVER_REFERENCES_MOD_CLIENT.check(SHIPPED);
        assertBites(COMMON_CODE_NEVER_REFERENCES_MOD_CLIENT, ModClientReferenceFixture.class);
    }

    @Test
    void onlyNetGatewaySends() {
        ONLY_NET_GATEWAY_SENDS.check(SHIPPED);
        assertBites(ONLY_NET_GATEWAY_SENDS, SendFixtures.Distributor.class);
        assertBites(ONLY_NET_GATEWAY_SENDS, SendFixtures.ConnectionSend.class);
    }

    @Test
    void onlyBlockWriterWritesBlocks() {
        ONLY_BLOCK_WRITER_WRITES_BLOCKS.check(SHIPPED);
        assertBites(ONLY_BLOCK_WRITER_WRITES_BLOCKS, BlockWriteFixtures.SetBlock.class);
        assertBites(ONLY_BLOCK_WRITER_WRITES_BLOCKS, BlockWriteFixtures.SetBlockState.class);
    }

    @Test
    void noStaticMutableStateExceptHolders() {
        NO_STATIC_MUTABLE_STATE_EXCEPT_HOLDERS.check(SHIPPED);
        assertBites(NO_STATIC_MUTABLE_STATE_EXCEPT_HOLDERS, StaticStateFixtures.NonFinal.class);
        assertBites(NO_STATIC_MUTABLE_STATE_EXCEPT_HOLDERS, StaticStateFixtures.FinalList.class);
    }

    @Test
    void threadsOnlyInServerJobs() {
        THREADS_ONLY_IN_SERVER_JOBS.check(SHIPPED);
        assertBites(THREADS_ONLY_IN_SERVER_JOBS, ThreadFixture.class);
    }

    @Test
    void hotPathsDoNotStreamOrBox() {
        HOT_PATHS_DO_NOT_STREAM_OR_BOX.check(SHIPPED);
        assertBites(HOT_PATHS_DO_NOT_STREAM_OR_BOX, HotPathFixtures.Streaming.class);
        assertBites(HOT_PATHS_DO_NOT_STREAM_OR_BOX, HotPathFixtures.Unboxing.class);
    }

    @Test
    void mixinsOnlyRoute() {
        MIXINS_ONLY_ROUTE.check(SHIPPED);
        assertBites(MIXINS_ONLY_ROUTE, RoutingFixture.class);
    }

    @Test
    void mainNeverDependsOnDevCode() {
        MAIN_NEVER_DEPENDS_ON_DEV_CODE.check(SHIPPED);
        assertBites(MAIN_NEVER_DEPENDS_ON_DEV_CODE, DevCodeReferenceFixture.class);
    }

    @Test
    void everyPackageIsNullMarked() {
        EVERY_PACKAGE_IS_NULL_MARKED.check(SHIPPED);
        assertBites(EVERY_PACKAGE_IS_NULL_MARKED, UnmarkedPackageFixture.class);
    }

    private static boolean startsWithAny(String packageName, String... prefixes) {
        for (String prefix : prefixes) {
            if (packageName.equals(prefix) || packageName.startsWith(prefix + ".")) {
                return true;
            }
        }
        return false;
    }

    private static void assertBites(ArchRule rule, Class<?> fixture) {
        assertTrue(
                rule.evaluate(ArchClasses.fixtures(fixture)).hasViolation(),
                () -> "the rule no longer flags its fixture " + fixture.getName() + ": " + rule.getDescription());
    }
}
