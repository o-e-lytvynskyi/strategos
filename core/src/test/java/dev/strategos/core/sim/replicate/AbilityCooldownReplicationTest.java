package dev.strategos.core.sim.replicate;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.strategos.core.content.TemplateCompiler;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.Target;
import dev.strategos.core.protocol.UnitDetail;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.protocol.UseAbility;
import dev.strategos.core.protocol.WireCodec;
import dev.strategos.core.shared.AbilityId;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.testkit.SimHarness;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** UNIT-10: an ability's remaining cooldown rides {@code unit_detail} to its owner, and a frame without one is unchanged. */
class AbilityCooldownReplicationTest {
    private static final int MATCH = 1;
    private static final FactionSlot VIEWER = new FactionSlot(0);

    private static TemplateSet set() {
        List<TemplateCompiler.Flat> flats = new ArrayList<>();
        for (UnitTemplate t : TemplateSet.builtin().all()) {
            JsonObject json = Objects.requireNonNull(TemplateSet.builtin().flattened(t.id()));
            if (t.id().equals(TemplateSet.INFANTRY)) {
                json.add("Abilities", JsonParser.parseString("""
                        { "rally": { "target": "none", "cooldown": 100,
                                     "effects": [ { "kind": "heal", "params": { "amount": 1 } } ] } }
                        """));
            }
            flats.add(new TemplateCompiler.Flat(t.id(), t.netIndex(), json));
        }
        TemplateCompiler.Result result = TemplateCompiler.fromFlattened(
                flats,
                TemplateSet.builtin().techs().stream()
                        .map(TemplateCompiler.TechFlat::of)
                        .toList());
        assertThat(result.problems()).isEmpty();
        return result.set();
    }

    private static UnitDetail.Unit plain(int handle, List<UnitDetail.Cooldown> cooldowns) {
        return new UnitDetail.Unit(
                handle,
                0,
                2,
                100f,
                100f,
                0.2f,
                List.of(),
                -1,
                0,
                UnitDetail.NO_TARGET,
                UnitDetail.NO_STANCE,
                cooldowns);
    }

    @Test
    void aUsedAbilityRestsInTheDetailOfItsOwnerUntilItRunsOut() {
        TemplateSet templates = set();
        SimHarness h = SimHarness.builder().templates(templates).factions(2).build();
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.INFANTRY, VIEWER, new Vec2d(8, 8), 1, 0));
        int unit = h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .findFirst()
                .orElseThrow();
        assertThat(ReplicationService.detail(h.queries(), MATCH, VIEWER, List.of(unit))
                        .units()
                        .get(0)
                        .cooldowns())
                .as("nothing has been used")
                .isEmpty();

        h.submit(PlayerSlot.ADMIN, UseAbility.replace(UnitSet.of(unit), new AbilityId("rally"), Target.none()));
        h.runTicks(3);
        List<UnitDetail.Cooldown> resting = ReplicationService.detail(h.queries(), MATCH, VIEWER, List.of(unit))
                .units()
                .get(0)
                .cooldowns();
        assertThat(resting).hasSize(1);
        assertThat(resting.get(0).ability()).isEqualTo(templates.abilityIndex("rally"));
        assertThat(resting.get(0).ticks()).isBetween(90, 100);

        h.runTicks(100);
        assertThat(ReplicationService.detail(h.queries(), MATCH, VIEWER, List.of(unit))
                        .units()
                        .get(0)
                        .cooldowns())
                .as("ready again")
                .isEmpty();
    }

    @Test
    void theCooldownTrailerRoundTripsAndAFrameWithoutOneIsUnchanged() {
        UnitDetail none = new UnitDetail(MATCH, 9, List.of(plain(5, List.of())));
        byte[] noneBytes = WireCodec.encode(UnitDetail.WIRE, none);
        assertThat(WireCodec.encode(
                        UnitDetail.WIRE,
                        new UnitDetail(
                                MATCH, 9, List.of(new UnitDetail.Unit(5, 0, 2, 100f, 100f, 0.2f, List.of(), -1, 0)))))
                .as("no cooldown, no extra byte")
                .isEqualTo(noneBytes);

        UnitDetail one = new UnitDetail(MATCH, 9, List.of(plain(5, List.of(new UnitDetail.Cooldown(3, 600)))));
        byte[] oneBytes = WireCodec.encode(UnitDetail.WIRE, one);
        assertThat(WireCodec.decode(UnitDetail.WIRE, oneBytes)).isEqualTo(one);
        assertThat(oneBytes)
                .as(
                        "empty attack and stance counts, then one entry: place, count, ability, ticks (a varint of 600 is two)")
                .hasSize(noneBytes.length + 1 + 1 + 1 + 1 + 1 + 1 + 2);

        UnitDetail mixed = new UnitDetail(
                MATCH,
                9,
                List.of(
                        new UnitDetail.Unit(5, 0, 2, 100f, 100f, 0.2f, List.of(), -1, 0, 300, 4),
                        plain(6, List.of()),
                        plain(7, List.of(new UnitDetail.Cooldown(0, 1), new UnitDetail.Cooldown(2, 72_000)))));
        byte[] mixedBytes = WireCodec.encode(UnitDetail.WIRE, mixed);
        assertThat(WireCodec.decode(UnitDetail.WIRE, mixedBytes)).isEqualTo(mixed);
        assertThat(mixedBytes.length).isLessThanOrEqualTo(UnitDetail.WIRE.maxSize());
    }
}
