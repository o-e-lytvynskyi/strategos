package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.match;

import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MockViewer;
import dev.strategos.mod.StrategosServer;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

/** Actual accepted vanilla movement across each commander boundary, with an unseated spectator control. */
public final class CommanderBoundsTests {
    private CommanderBoundsTests() {}

    @StrategosTest(timeoutTicks = 800)
    public static void aForeignDimensionIsCorrectedWithinOnePlayerTick(GameTestHelper h) {
        var rt = match(h);
        var member = FirstPersonTests.commander(h, rt, "guard-level-in");
        var holder = StrategosServer.get();
        var foreign = holder.server().getLevel(Level.NETHER);
        check(foreign != null, "fixture server has no alternate dimension");
        var destination = java.util.Objects.requireNonNull(foreign);
        check(
                !destination.dimension().equals(rt.space().level()),
                "fixture alternate dimension equals match dimension");
        var at = member.player().position();
        h.onEachTick(member::poll);
        h.startSequence()
                .thenExecute(() -> {
                    member.player().teleportTo(destination, at.x, at.y, at.z, 0, 0);
                    check(
                            member.player().level().dimension().equals(destination.dimension()),
                            "foreign-dimension fixture did not move the actual player");
                    FirstPersonTests.accept(member);
                })
                .thenExecuteAfter(1, () -> {
                    check(
                            member.player()
                                    .level()
                                    .dimension()
                                    .equals(rt.space().level()),
                            "commander remained in a foreign dimension after one tick");
                    check(!member.disconnected(), "dimension correction disconnected the commander");
                    member.close();
                    holder.matches().dispose(rt.id());
                })
                .thenSucceed();
    }

    @StrategosTest(timeoutTicks = 200)
    public static void allSixCommanderBoundsAreRestoredWithinOneTickWhileVanillaSpectatorsCanCross(GameTestHelper h) {
        var rt = match(h);
        var member = FirstPersonTests.commander(h, rt, "guard-bounds-in");
        var at = member.player().position();
        var vanilla = MockViewer.join(h.getLevel(), "guard-bounds-out", at.x, at.y, at.z, 2);
        vanilla.player().setGameMode(GameType.SPECTATOR);
        h.onEachTick(() -> {
            member.poll();
            vanilla.poll();
        });
        var space = rt.space();
        double[] min = {
            space.origin().getX() - 32, space.minY() - 32, space.origin().getZ() - 32
        };
        double[] max = {
            space.origin().getX() + space.sizeX() + 32,
            space.maxY() + 64,
            space.origin().getZ() + space.sizeZ() + 32
        };
        var sequence = h.startSequence();
        for (int axis = 0; axis < 3; axis++) {
            for (int sign : new int[] {-1, 1}) {
                int dimension = axis;
                double boundary = sign < 0 ? min[axis] : max[axis];
                double[] inside = {(min[0] + max[0]) / 2, (min[1] + max[1]) / 2, (min[2] + max[2]) / 2};
                inside[axis] = boundary - sign;
                double[] outside = inside.clone();
                outside[axis] = boundary + sign * 4;
                sequence.thenExecute(() -> {
                            for (var peer : java.util.List.of(member, vanilla)) {
                                peer.player().connection.teleport(inside[0], inside[1], inside[2], 0, 0);
                                FirstPersonTests.accept(peer);
                            }
                        })
                        .thenExecuteAfter(1, () -> {
                            for (var peer : java.util.List.of(member, vanilla)) {
                                peer.sendPacket(
                                        new ServerboundMovePlayerPacket.Pos(outside[0], outside[1], outside[2], false));
                            }
                        })
                        .thenExecuteAfter(1, () -> {
                            double[] commander = {
                                member.player().getX(),
                                member.player().getY(),
                                member.player().getZ()
                            };
                            double[] plain = {
                                vanilla.player().getX(),
                                vanilla.player().getY(),
                                vanilla.player().getZ()
                            };
                            check(
                                    Math.abs(plain[dimension] - outside[dimension]) < 1e-6,
                                    "vanilla spectator did not cross boundary " + dimension + "/" + sign);
                            check(
                                    Math.abs(commander[dimension] - boundary) < 1e-6,
                                    "commander remained beyond boundary " + dimension + "/" + sign + ": "
                                            + member.player().position());
                            check(!member.disconnected(), "boundary correction disconnected the commander");
                        });
            }
        }
        sequence.thenExecute(() -> {
                    member.close();
                    vanilla.close();
                    StrategosServer.get().matches().dispose(rt.id());
                })
                .thenSucceed();
    }
}
