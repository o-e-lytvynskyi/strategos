package dev.strategos.core.sim.combat;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonParser;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.RawTemplate;
import dev.strategos.core.content.TemplateCompiler;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.NoticeKind;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CorePorts;
import dev.strategos.core.sim.ExternalDamage;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.Notice;
import dev.strategos.core.sim.TickOutputs;
import dev.strategos.core.sim.UnitDied;
import dev.strategos.core.sim.UnitHurt;
import dev.strategos.core.sim.persist.MatchSnapshotCodec;
import dev.strategos.core.sim.persist.Migrations;
import dev.strategos.core.testkit.SimHarness;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * CMB-01 in L1: vanilla blows through the COMBAT phase on a tank of 500 hit points with hack resistance 2; twenty in
 * one tick take exactly the sum the formula gives (CMB-01.2); a unit's death takes it out of the match and tells its
 * faction (CMB-01.5's core side); the damage taken survives a snapshot.
 */
class CombatSystemTest {
    private static final TemplateId TANK = new TemplateId("test:unit/tank");
    private static final String TANK_JSON = """
            {"Identity": {"name": "tank", "classes": "Infantry Heavy"}, "UnitMotion": {"walkSpeed": 0.25},
             "Body": {"entityType": "strategos:unit_humanoid", "width": 0.6, "height": 1.8},
             "Health": {"max": 500}, "Resistance": {"hack": 2, "pierce": 1}}
            """;

    private static TemplateSet templates() {
        TemplateCompiler.Result result = TemplateCompiler.compile(List.of(new RawTemplate(
                TANK, "tank.json", JsonParser.parseString(TANK_JSON).getAsJsonObject())));
        assertThat(result.problems()).isEmpty();
        return result.set();
    }

    private static SimHarness harness() {
        return SimHarness.builder().templates(templates()).factions(2).build();
    }

    private static int tank(SimHarness h, int faction) {
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(TANK, new FactionSlot(faction), new Vec2d(16, 16), 1, 0));
        h.runTicks(2);
        int[] units = h.queries().unitHandlesOf(new FactionSlot(faction));
        assertThat(units).hasSize(1);
        return units[0];
    }

    private static ExternalDamage hack(int unit, float amount) {
        return new ExternalDamage(unit, ExternalDamage.Kind.HACK, amount, ExternalDamage.SourceKind.MOB);
    }

    @Test
    void twentyBlowsInOneTickTakeExactlyTheirSum() {
        SimHarness h = harness();
        int target = tank(h, 1);
        assertThat(h.queries().maxHitPoints(target)).isEqualTo(500);
        double expected = 0;
        for (int k = 0; k < 20; k++) {
            float amount = 3.5f + k * 0.75f;
            h.damage(hack(target, amount));
            expected += amount * StrictMath.pow(0.9, 2);
        }
        h.step();

        assertThat(h.queries().hitPoints(target)).isEqualTo(500 - expected);
        List<UnitHurt> hits = h.events(UnitHurt.class);
        assertThat(hits).hasSize(20).allMatch(hit -> hit.handle() == target && hit.attacker() == Handle.NONE);
    }

    @Test
    void eachTypeMeetsTheTanksResistance() {
        SimHarness h = harness();
        int target = tank(h, 1);
        h.damage(new ExternalDamage(target, ExternalDamage.Kind.PIERCE, 10, ExternalDamage.SourceKind.PLAYER));
        h.damage(new ExternalDamage(target, ExternalDamage.Kind.CRUSH, 10, ExternalDamage.SourceKind.NONE));
        h.step();
        assertThat(h.queries().hitPoints(target)).isEqualTo(500 - (10 * 0.9 + 10));
    }

    @Test
    void deathTakesTheUnitOutAndTellsItsFaction() {
        SimHarness h = harness();
        int target = tank(h, 1);
        h.damage(hack(target, 300)); // 243 after resistance
        h.damage(hack(target, 400)); // 324: dead
        h.damage(hack(target, 1)); // after its death in the same tick: dropped
        h.step();

        assertThat(h.events(UnitHurt.class)).extracting(UnitHurt::amount).hasSize(2);
        assertThat(h.events(UnitDied.class)).containsExactly(new UnitDied(target, new FactionSlot(1), Handle.NONE));
        assertThat(h.events(Notice.class))
                .anyMatch(n -> n.kind() == NoticeKind.UNIT_LOST
                        && n.handle() == target
                        && n.faction().value() == 1);
        assertThat(h.queries().unit(target)).isNull();
        assertThat(h.queries().unitHandles()).isEmpty();
        assertThat(h.queries().hitPoints(target)).isNaN();

        h.damage(hack(target, 5)); // a stale handle next tick
        TickOutputs later = h.step();
        assertThat(later.events()).noneMatch(e -> e instanceof UnitHurt);

        int reborn = tank(h, 1);
        assertThat(Handle.index(reborn)).isEqualTo(Handle.index(target));
        assertThat(reborn).isNotEqualTo(target);
        assertThat(h.queries().hitPoints(reborn))
                .as("a new unit at the freed index is whole")
                .isEqualTo(500);
        h.assertInvariants();
    }

    @Test
    void aLethalBlowTakesWhateverIsLeft() {
        SimHarness h = harness();
        int target = tank(h, 0);
        h.damage(new ExternalDamage(target, ExternalDamage.Kind.LETHAL, 0, ExternalDamage.SourceKind.NONE));
        h.step();
        assertThat(h.events(UnitHurt.class)).extracting(UnitHurt::amount).containsExactly(500f);
        assertThat(h.events(UnitDied.class)).hasSize(1);
    }

    @Test
    void theDamageTakenSurvivesASnapshot() {
        SimHarness h = harness();
        int target = tank(h, 1);
        h.damage(hack(target, 50));
        h.step();
        MatchSnapshot snapshot = h.core().snapshot();
        MatchSnapshot decoded = MatchSnapshotCodec.decode(
                        new Dynamic<>(JsonOps.INSTANCE, MatchSnapshotCodec.encode(JsonOps.INSTANCE, snapshot)),
                        Migrations.production())
                .getOrThrow();
        assertThat(decoded).isEqualTo(snapshot);
        MatchCore restored = MatchCore.restore(decoded, templates(), CorePorts.of(() -> 0L));
        assertThat(restored.queries().hitPoints(target))
                .isEqualTo(h.queries().hitPoints(target))
                .isEqualTo(500 - 40.5);
        assertThat(restored.stateHash()).isEqualTo(h.stateHash());
    }
}
