package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.match;

import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MockViewer;
import dev.strategos.mod.StrategosServer;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import net.minecraft.world.level.GameType;

/** Actual vanilla packet routes, each with a vanilla spectator positive before the member denial. */
public final class CommanderPowerTests {
    private CommanderPowerTests() {}

    @StrategosTest(timeoutTicks = 200)
    public static void aMemberCannotTeleportToAnEntityButAVanillaSpectatorCan(GameTestHelper h) {
        var rt = match(h);
        var member = FirstPersonTests.commander(h, rt, "guard-tp-member");
        var origin = member.player().position();
        var target = MockViewer.join(h.getLevel(), "guard-tp-target", origin.x + 10, origin.y, origin.z + 8, 2);
        var vanilla = MockViewer.join(h.getLevel(), "guard-tp-free", origin.x, origin.y, origin.z, 2);
        target.player().setGameMode(GameType.SPECTATOR);
        vanilla.player().setGameMode(GameType.SPECTATOR);
        h.onEachTick(() -> {
            member.poll();
            target.poll();
            vanilla.poll();
        });
        h.startSequence()
                .thenExecute(() -> vanilla.sendPacket(
                        new ServerboundTeleportToEntityPacket(target.player().getUUID())))
                .thenExecuteAfter(1, () -> {
                    check(
                            vanilla.player()
                                            .position()
                                            .distanceTo(target.player().position())
                                    < 1e-6,
                            "the vanilla teleport positive did not reach its actual entity target");
                    member.sendPacket(new ServerboundTeleportToEntityPacket(
                            target.player().getUUID()));
                })
                .thenExecuteAfter(1, () -> {
                    boolean denied = member.player().position().distanceTo(origin) < 1e-6;
                    boolean connected = !member.disconnected();
                    member.close();
                    target.close();
                    vanilla.close();
                    StrategosServer.get().matches().dispose(rt.id());
                    check(denied, "a member's vanilla teleport packet moved its commander body");
                    check(connected, "a refused spectator teleport disconnected a valid member");
                })
                .thenSucceed();
    }

    @StrategosTest(timeoutTicks = 200)
    public static void anAttackCannotChangeACommandersCameraButVanillaSpectatingStillWorks(GameTestHelper h) {
        var rt = match(h);
        Units.spawn(rt, 8.5, 8.5, 1, 0);
        var member = FirstPersonTests.commander(h, rt, "guard-at-member");
        var at = member.player().position();
        var vanilla = MockViewer.join(h.getLevel(), "guard-at-free", at.x, at.y, at.z, 2);
        vanilla.player().setGameMode(GameType.SPECTATOR);
        h.onEachTick(() -> {
            member.poll();
            vanilla.poll();
        });
        h.startSequence()
                .thenWaitUntil(() -> check(
                        Units.handles(rt).length == 1 && rt.bodies().get(Units.onlyUnit(rt)) != null,
                        "attack-camera fixture has no actual unit body"))
                .thenExecute(() -> {
                    var target = Units.body(rt, Units.onlyUnit(rt));
                    for (var viewer : java.util.List.of(member, vanilla)) {
                        viewer.player().connection.teleport(target.getX() + 1, target.getY(), target.getZ(), 0, 0);
                        FirstPersonTests.accept(viewer);
                    }
                    vanilla.sendPacket(ServerboundInteractPacket.createAttackPacket(target, false));
                })
                .thenExecuteAfter(1, () -> {
                    var target = Units.body(rt, Units.onlyUnit(rt));
                    check(
                            vanilla.player().getCamera().getUUID().equals(target.getUUID()),
                            "the vanilla attack positive did not switch to its actual unit camera");
                    member.sendPacket(ServerboundInteractPacket.createAttackPacket(target, false));
                })
                .thenExecuteAfter(1, () -> {
                    boolean ownCamera = member.player()
                            .getCamera()
                            .getUUID()
                            .equals(member.player().getUUID());
                    boolean connected = !member.disconnected();
                    member.close();
                    vanilla.close();
                    StrategosServer.get().matches().dispose(rt.id());
                    check(ownCamera, "a commander's vanilla attack packet took another entity's camera");
                    check(connected, "a refused spectator attack disconnected a valid commander");
                })
                .thenSucceed();
    }
}
