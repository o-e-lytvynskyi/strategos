package dev.strategos.core.sim.capture;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Capture;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.unitai.UnitAi;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import org.junit.jupiter.api.Test;

/**
 * CMB-10's order (L1, M9 spec §2.6): the Capture command's acceptance and refusals, and twenty infantry ordered onto an
 * undefended enemy barracks take it in the maths' time (800 points against 20 × 2.5 − 10 a second: 20 s, and the walk)
 * and then go idle.
 */
class CaptureOrderTest {
    static final TemplateId BARRACKS = new TemplateId("strategos:building/barracks");
    static final TemplateId FIELD = new TemplateId("strategos:building/field");
    static final TemplateId RAM = new TemplateId("strategos:unit/ram");
    static final PlayerSlot P0 = new PlayerSlot(0);

    final SimHarness h =
            SimHarness.builder().size(128, 128).yBand(0, 31, 1).factions(2).build();
    final MatchState s;

    CaptureOrderTest() {
        h.publishAll(VoxelFixtures.flat(128, 128, 32, 1));
        h.step();
        s = KernelAccess.state(h.core());
    }

    int building(TemplateId template, int faction, int x, int z) {
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(template, new FactionSlot(faction), x, z, 0));
        h.step();
        int[] handles = s.buildings().handles();
        return handles[handles.length - 1];
    }

    int[] units(TemplateId template, int faction, double x, double z, int count) {
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(template, new FactionSlot(faction), new Vec2d(x, z), count, 1.5));
        return h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .toArray();
    }

    CommandResult send(PlayerSlot issuer, Command command) {
        int seq = h.submit(issuer, command);
        h.step();
        return h.results().stream()
                .filter(r -> r.clientSeq() == seq)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void refusalsAndSkips() {
        int enemy = building(BARRACKS, 1, 80, 80);
        int own = building(BARRACKS, 0, 20, 20);
        int field = building(FIELD, 1, 80, 40);
        int[] infantry = units(TemplateSet.INFANTRY, 0, 60, 80, 2);
        int[] rams = units(RAM, 0, 60, 90, 1);
        int[] foreign = units(TemplateSet.INFANTRY, 1, 100, 100, 1);

        assertThat(send(P0, new Capture(UnitSet.of(infantry), 0x7FFFF)).firstReason())
                .as("no such building")
                .isEqualTo(RejectReason.UNKNOWN_TARGET);
        assertThat(send(P0, new Capture(UnitSet.of(infantry), own)).firstReason())
                .as("the issuer's own building")
                .isEqualTo(RejectReason.INVALID_ARGUMENT);
        assertThat(send(P0, new Capture(UnitSet.of(infantry), field)).firstReason())
                .as("a template with no capture points")
                .isEqualTo(RejectReason.UNSUPPORTED);
        assertThat(send(P0, new Capture(UnitSet.of(rams), enemy)).firstReason())
                .as("a ram has no capture strength")
                .isEqualTo(RejectReason.UNSUPPORTED);
        assertThat(send(PlayerSlot.ADMIN, new Capture(UnitSet.of(foreign), enemy))
                        .firstReason())
                .as("the owner's own units")
                .isEqualTo(RejectReason.INVALID_ARGUMENT);
        assertThat(send(P0, new Capture(UnitSet.of(foreign), enemy)).firstReason())
                .as("units the issuer does not control (unseen ones are unknown)")
                .isIn(RejectReason.NOT_OWNER, RejectReason.UNKNOWN_TARGET);
        assertThat(s.orders().size(Handle.index(infantry[0]))).isZero();

        CommandResult mixed = send(P0, new Capture(UnitSet.of(infantry[0], rams[0]), enemy));
        assertThat(mixed.outcome()).as("the ram is skipped").isEqualTo(CommandOutcome.PARTIAL);
        assertThat(mixed.firstReason()).isEqualTo(RejectReason.UNSUPPORTED);
        assertThat(s.orders().kind(Handle.index(infantry[0]))).isEqualTo(OrderKind.CAPTURE);
        assertThat(s.orders().target(Handle.index(infantry[0]))).isEqualTo(enemy);
        assertThat(s.orders().kind(Handle.index(rams[0]))).isEqualTo(OrderKind.NONE);
    }

    @Test
    void twentyInfantryTakeAnUndefendedBarracksAndGoIdle() {
        int barracks = building(BARRACKS, 1, 80, 80);
        int[] infantry = units(TemplateSet.INFANTRY, 0, 66, 80, 20);
        assertThat(send(P0, new Capture(UnitSet.of(infantry), barracks)).outcome())
                .isEqualTo(CommandOutcome.ACCEPTED);

        int start = (int) s.tick();
        boolean taken = h.runUntil(() -> s.buildings().faction(barracks) == 0, 60 * CaptureSystem.SECOND);

        assertThat(taken).as("taken within a minute").isTrue();
        double seconds = ((int) s.tick() - start) / (double) CaptureSystem.SECOND;
        assertThat(seconds).as("seconds: 20 of capture and the walk").isBetween(17.0, 32.0);
        assertThat(s.capture().points(barracks))
                .as("the new owner holds every point")
                .isNull();
        h.runTicks(10);
        for (int unit : infantry) {
            int i = Handle.index(unit);
            assertThat(s.orders().kind(i)).as("order done").isEqualTo(OrderKind.NONE);
            assertThat(s.ai().code(i)).as("idle").isEqualTo(UnitAi.IDLE.code());
        }
    }
}
