package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.handles;
import static dev.strategos.gametest.suites.Units.match;
import static dev.strategos.gametest.suites.Units.unit;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.OutputEvent;
import dev.strategos.core.sim.UnitState;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.command.StrategosCommands;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.persist.MatchData;
import dev.strategos.mod.server.persist.Quarantine;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** {@code /strategos} in a real server (QA-10.1, CORE-03.6, CORE-01): permission, the command path, match commands. */
public final class CommandTests {
    private CommandTests() {}

    /** QA-10.1: the root needs permission level 2, so players below it neither run nor see it. */
    @StrategosTest
    public static void rootRequiresPermission2(GameTestHelper h) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        StrategosCommands.register(dispatcher);
        CommandNode<CommandSourceStack> root = dispatcher.getRoot().getChild("strategos");
        check(root != null, "no strategos node");
        CommandSourceStack source = h.getLevel().getServer().createCommandSourceStack();

        check(!root.canUse(source.withPermission(0)), "permission 0 may use /strategos");
        check(!root.canUse(source.withPermission(1)), "permission 1 may use /strategos");
        check(root.canUse(source.withPermission(2)), "permission 2 may not use /strategos");
        h.succeed();
    }

    /** CORE-03.6: debug spawn and debug move reach the core as commands and reply with their results. */
    @StrategosTest
    public static void debugCommandsUseTheCommandPath(GameTestHelper h) {
        MatchRuntime rt = match(h);
        List<CommandResult> results = new ArrayList<>();
        rt.addListener(out -> {
            for (OutputEvent event : out.events()) {
                if (event instanceof CommandResult result) {
                    results.add(result);
                }
            }
        });
        Recorder recorder = new Recorder();
        CommandSourceStack source = recorder.at(h, new Vec3(16.5, 1, 16.5));
        BlockPos target = h.absolutePos(new BlockPos(24, 1, 16));
        perform(h, source, "strategos debug spawn 3");
        h.startSequence()
                .thenWaitUntil(() -> check(handles(rt).length == 3, "not spawned yet"))
                .thenExecute(() -> perform(h, source, "strategos debug move " + target.getX() + " " + target.getZ()))
                .thenWaitUntil(() -> check(results.size() == 2, "the move has no result yet"))
                .thenExecute(() -> {
                    for (CommandResult result : results) {
                        check(
                                result.outcome() == CommandOutcome.ACCEPTED && result.accepted() == 3,
                                result.kind() + " got " + result + ", not ACCEPTED 3/3");
                    }
                    check(
                            results.get(0).kind().equals("strategos:spawn_units")
                                    && results.get(1).kind().equals("strategos:move"),
                            "the results are " + results);
                    for (int handle : handles(rt)) {
                        check(unit(rt, handle).state() == UnitState.WALKING, "a moved unit does not walk");
                    }
                    long replies = recorder.lines.stream()
                            .filter(l -> l.endsWith("accepted 3/3"))
                            .count();
                    check(replies == 2, "the source got " + recorder.lines + ", not two replies of accepted 3/3");
                })
                .thenSucceed();
    }

    /**
     * CORE-01: match create makes a saved match around the source, with its record and, once the level saves, its blob
     * file; list shows it; dispose removes the match, the record and the file.
     */
    @StrategosTest
    public static void matchCreateListDispose(GameTestHelper h) {
        Recorder recorder = new Recorder();
        CommandSourceStack source = recorder.at(h, new Vec3(16.5, 1, 16.5));
        BlockPos feet = h.absolutePos(new BlockPos(16, 1, 16));
        StrategosServer server = StrategosServer.get();

        perform(h, source, "strategos match create 16 16");
        MatchRuntime rt = server.matches().at(h.getLevel(), feet);
        check(rt != null, "no match around the source after match create: " + recorder.lines);
        check(
                rt.space().sizeX() == 16
                        && rt.space().sizeZ() == 16
                        && rt.space().contains(feet),
                "the match is not 16 x 16 around the source: " + rt.space());
        check(rt.persistent() && server.matches().record(rt.id()) != null, "match create made no saved match");
        Path blob = Quarantine.dataFile(h.getLevel(), MatchData.name(rt.id()));
        String id = rt.id().toString();
        perform(h, source, "strategos match list");
        check(
                recorder.lines.stream().anyMatch(l -> l.startsWith(id + " RUNNING")),
                "match list does not show " + id + ": " + recorder.lines);
        PersistenceTests.saveAndWait(h.getLevel());
        check(Files.exists(blob), "no blob file after the level saved");

        perform(h, source, "strategos match dispose " + id);
        check(server.matches().get(rt.id()) == null, "match dispose left " + id);
        check(server.matches().record(rt.id()) == null, "match dispose left the record of " + id);
        check(!Files.exists(blob), "match dispose left the blob file of " + id);
        PersistenceTests.saveAndWait(h.getLevel());
        check(!Files.exists(blob), "the disposed blob was written again");
        h.succeed();
    }

    private static void perform(GameTestHelper h, CommandSourceStack source, String command) {
        MinecraftServer server = h.getLevel().getServer();
        server.getCommands().performPrefixedCommand(source, command);
    }

    /** A command source that keeps every line sent to it. */
    private static final class Recorder implements CommandSource {
        final List<String> lines = new ArrayList<>();

        /** An operator's source (permission 2) at a point of the test's arena. */
        CommandSourceStack at(GameTestHelper h, Vec3 relative) {
            return new CommandSourceStack(
                    this,
                    h.absoluteVec(relative),
                    Vec2.ZERO,
                    h.getLevel(),
                    2,
                    "strategos-test",
                    Component.literal("strategos-test"),
                    h.getLevel().getServer(),
                    null);
        }

        @Override
        public void sendSystemMessage(Component component) {
            lines.add(component.getString());
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    }
}
