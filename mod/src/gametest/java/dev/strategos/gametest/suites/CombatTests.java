package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.body;
import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;
import static dev.strategos.gametest.suites.Units.match;
import static dev.strategos.gametest.suites.Units.onlyUnit;
import static dev.strategos.gametest.suites.Units.spawn;

import com.mojang.authlib.GameProfile;
import dev.strategos.core.protocol.NoticeKind;
import dev.strategos.core.protocol.NoticeMsg;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.ExternalDamage;
import dev.strategos.core.sim.InputEvent;
import dev.strategos.core.sim.TickInputs;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MockViewer;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.net.payload.FeedbackPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * The damage model on real bodies (CMB-01): vanilla's blows reach the core as its own damage, the body's vanilla health
 * stays full until the core says it died, and a dead unit's body plays vanilla's death and goes.
 */
public final class CombatTests {
    private CombatTests() {}

    /**
     * CMB-01.4: a player's blow, an arrow, an explosion and {@code /kill} reach the core as hack, pierce, crush and
     * lethal; the blows take hit points off the unit while the body's vanilla health stays full (CMB-01.3's half that
     * vanilla sources can show), and the kill kills it.
     */
    @StrategosTest(timeoutTicks = 200)
    public static void vanillaBlowsBecomeCoreDamage(GameTestHelper h) {
        MatchRuntime rt = match(h);
        spawn(rt, 16, 16, 1, 0);
        var state = new Object() {
            int unit;
            double before;
            float vanilla;
        };
        h.startSequence()
                .thenWaitUntil(() -> {
                    state.unit = onlyUnit(rt);
                    body(rt, state.unit);
                })
                .thenExecute(() -> {
                    UnitEntity body = body(rt, state.unit);
                    state.before = core(rt).queries().hitPoints(state.unit);
                    state.vanilla = body.getHealth();
                    FakePlayer player = FakePlayerFactory.get(
                            h.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(new byte[] {21}), "strategos-cmb1"));
                    player.moveTo(body.getX() - 1.5, body.getY(), body.getZ(), -90, 0);
                    player.attack(body);
                    Arrow arrow = new Arrow(
                            h.getLevel(), body.getX(), body.getY() + 3, body.getZ(), new ItemStack(Items.ARROW), null);
                    body.hurt(h.getLevel().damageSources().arrow(arrow, player), 4);
                    h.getLevel()
                            .explode(
                                    null,
                                    body.getX() + 1.5,
                                    body.getY() + 0.5,
                                    body.getZ(),
                                    1.5f,
                                    Level.ExplosionInteraction.NONE);
                })
                .thenExecuteAfter(2, () -> {
                    List<ExternalDamage.Kind> kinds = kinds(rt, state.unit);
                    check(
                            kinds.contains(ExternalDamage.Kind.HACK)
                                    && kinds.contains(ExternalDamage.Kind.PIERCE)
                                    && kinds.contains(ExternalDamage.Kind.CRUSH),
                            "the core got " + kinds + " for a blow, an arrow and an explosion");
                    double now = core(rt).queries().hitPoints(state.unit);
                    check(now < state.before - 4, "the unit kept " + now + " of " + state.before + " hit points");
                    check(
                            body(rt, state.unit).getHealth() == state.vanilla,
                            "the body's vanilla health went from " + state.vanilla + " to "
                                    + body(rt, state.unit).getHealth());
                    body(rt, state.unit).kill(); // /kill's own call
                })
                .thenExecuteAfter(2, () -> {
                    check(kinds(rt, state.unit).contains(ExternalDamage.Kind.LETHAL), "/kill was not lethal");
                    check(core(rt).queries().unit(state.unit) == null, "the unit outlived /kill");
                })
                .thenSucceed();
    }

    /**
     * CMB-01.5: a unit killed in the core loses its record at once; its body drops to vanilla health 0, plays the death
     * ticks and is gone after them, and the owner's player gets the loss notice within a tick.
     */
    @StrategosTest(timeoutTicks = 200)
    public static void aDeadUnitsBodyDiesAndGoes(GameTestHelper h) {
        MatchRuntime rt = match(h);
        Vec3 at = h.absoluteVec(new Vec3(8.5, 20, 8.5));
        MockViewer owner = MockViewer.join(h.getLevel(), "strategos-owner", at.x, at.y, at.z, 4);
        StrategosServer.get().matches().join(rt, owner.player().getUUID(), new PlayerSlot(0));
        StrategosServer.get().net().joined(rt, owner.player(), new PlayerSlot(0)); // the faction's audience
        spawn(rt, 16, 16, 1, 0);
        var state = new Object() {
            int unit;
            UnitEntity body;
            long diedAt;
        };
        h.startSequence()
                .thenWaitUntil(() -> {
                    state.unit = onlyUnit(rt);
                    state.body = body(rt, state.unit);
                })
                .thenExecute(() -> {
                    owner.poll();
                    owner.capture().clear();
                    rt.damage(new ExternalDamage(
                            state.unit, ExternalDamage.Kind.LETHAL, 0, ExternalDamage.SourceKind.NONE));
                    state.diedAt = h.getTick();
                })
                .thenWaitUntil(() -> check(core(rt).queries().unit(state.unit) == null, "the unit is alive"))
                .thenExecute(() -> {
                    check(
                            h.getTick() - state.diedAt <= 2,
                            "the unit died " + (h.getTick() - state.diedAt) + " ticks later");
                    check(state.body.getHealth() == 0 && state.body.isDeadOrDying(), "the body is not dying");
                    check(rt.bodies().get(state.unit) == null, "the registry still holds the body");
                })
                .thenExecuteAfter(
                        1,
                        () -> { // the gateway flushes after the GameTest ticker, at the tick's end
                            owner.poll();
                            check(
                                    lossNotices(owner, state.unit) == 1,
                                    lossNotices(owner, state.unit) + " loss notices");
                        })
                .thenWaitUntil(() ->
                        check(state.body.isRemoved(), "the body is still there, death tick " + state.body.deathTime))
                .thenExecute(() -> {
                    check(rt.bodies().dyingCount() == 0, rt.bodies().dyingCount() + " bodies still dying");
                    check(Units.bodiesOf(h.getLevel(), rt).isEmpty(), "a body of the match is still in the level");
                    owner.close();
                })
                .thenSucceed();
    }

    /** The kinds of the vanilla damage the unit took, as the journal holds it. */
    private static List<ExternalDamage.Kind> kinds(MatchRuntime rt, int unit) {
        List<ExternalDamage.Kind> kinds = new ArrayList<>();
        for (TickInputs tick : core(rt).journal().nonEmptyTicks()) {
            for (InputEvent e : tick.events()) {
                if (e instanceof ExternalDamage d && d.handle() == unit) {
                    kinds.add(d.kind());
                }
            }
        }
        return kinds;
    }

    private static int lossNotices(MockViewer viewer, int unit) {
        int n = 0;
        for (ClientboundCustomPayloadPacket p : viewer.capture().of(ClientboundCustomPayloadPacket.class)) {
            if (p.payload() instanceof FeedbackPayload f
                    && f.message() instanceof NoticeMsg notice
                    && notice.kind() == NoticeKind.UNIT_LOST
                    && notice.handle() == unit) {
                n++;
            }
        }
        return n;
    }
}
