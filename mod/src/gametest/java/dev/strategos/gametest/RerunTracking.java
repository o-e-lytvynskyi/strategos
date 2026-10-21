package dev.strategos.gametest;

import dev.strategos.mod.Strategos;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.MultipleTestTracker;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Makes declared retries work on the 1.21.1 GameTest server (QA-04.4).
 *
 * <p>{@code GameTestServer#startTests} builds its {@code MultipleTestTracker} once, from the first attempt of every
 * test, and never hears of a rerun ({@code GameTestRunner#rerunTest}). When a retried test's first attempt fails, the
 * tracker already sees every test done, so the server halts before the rerun and exits with that first failure. This
 * listener follows each test through its reruns and puts the newest attempt in the tracker's place, so the server
 * waits for the rerun and counts only final outcomes. It reads two private vanilla fields; if an update renames them,
 * the first GameTest run fails naming the field.
 */
@EventBusSubscriber(modid = Strategos.MOD_ID)
public final class RerunTracking implements GameTestListener {
    // Gametest code: one GameTest server per JVM, which builds its tracker once.
    private static @Nullable Field serverTracker;
    private static @Nullable Field trackedTests;
    private static boolean following;

    private final Collection<GameTestInfo> tracked;

    private RerunTracking(Collection<GameTestInfo> tracked) {
        this.tracked = tracked;
    }

    /** Runs before the tests' first tick: the server built its tracker at the end of the tick before. */
    @SubscribeEvent
    static void onServerTickPre(ServerTickEvent.Pre event) {
        if (following || !(event.getServer() instanceof GameTestServer server)) {
            return;
        }
        if (serverTracker == null) {
            serverTracker = field(GameTestServer.class, "testTracker");
            trackedTests = field(MultipleTestTracker.class, "tests");
        }
        MultipleTestTracker tracker = (MultipleTestTracker) read(serverTracker, server);
        if (tracker == null) {
            return;
        }
        following = true;
        @SuppressWarnings("unchecked") // MultipleTestTracker#tests is a Collection<GameTestInfo>
        Collection<GameTestInfo> tests = (Collection<GameTestInfo>) read(trackedTests, tracker);
        RerunTracking listener = new RerunTracking(tests);
        List.copyOf(tests).forEach(test -> test.addListener(listener));
    }

    @Override
    public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
        newTest.addListener(this); // a rerun starts with no listeners: follow it to its own reruns
        if (tracked instanceof List<GameTestInfo> list && list.contains(oldTest)) {
            list.set(list.indexOf(oldTest), newTest); // same place, so the progress bar keeps its order
        } else if (tracked.remove(oldTest)) {
            tracked.add(newTest);
        }
    }

    @Override
    public void testStructureLoaded(GameTestInfo testInfo) {}

    @Override
    public void testPassed(GameTestInfo test, GameTestRunner runner) {}

    @Override
    public void testFailed(GameTestInfo test, GameTestRunner runner) {}

    private static Field field(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException | RuntimeException e) {
            throw new IllegalStateException(
                    "Retries need " + owner.getName() + "#" + name + ", which this Minecraft version lacks or hides",
                    e);
        }
    }

    private static @Nullable Object read(@Nullable Field field, Object owner) {
        if (field == null) {
            throw new IllegalStateException("RerunTracking used before its fields were resolved");
        }
        try {
            return field.get(owner);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("cannot read " + field, e);
        }
    }
}
