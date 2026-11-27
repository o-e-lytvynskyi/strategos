package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.persist.MatchSnapshotCodec;
import dev.strategos.core.sim.persist.Migrations;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * CMB-13.2: {@code looted} is saved and hashed only once a kill has paid, so a match without one saves and hashes as
 * before; one that has survives a snapshot round trip with equal hashes, and its commands replay to the live hash.
 */
class LootPersistenceTest {
    private static final int FOOD = 0;

    private static SimHarness fightWon() {
        SimHarness h =
                SimHarness.builder().size(64, 64).yBand(0, 31, 1).factions(2).build();
        h.publishAll(VoxelFixtures.flat(64, 64, 32, 1));
        h.step();
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.INFANTRY, new FactionSlot(0), new Vec2d(20, 20), 4, 1));
        int[] spearmen = h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .toArray();
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.INFANTRY, new FactionSlot(1), new Vec2d(24, 20), 1, 0));
        int worker = h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .findFirst()
                .orElseThrow();
        h.submit(new PlayerSlot(0), Attack.replace(UnitSet.of(spearmen), worker));
        boolean died = false;
        for (int t = 0; t < 600 && !died; t++) {
            died = h.step().events().stream().anyMatch(UnitDied.class::isInstance);
        }
        assertThat(died).isTrue();
        return h;
    }

    private static MatchSnapshot roundTrip(MatchSnapshot snapshot) {
        return MatchSnapshotCodec.decode(
                        new Dynamic<>(JsonOps.INSTANCE, MatchSnapshotCodec.encode(JsonOps.INSTANCE, snapshot)),
                        Migrations.production())
                .getOrThrow();
    }

    @Test
    void aMatchWithoutAKillSavesNoLootedCounters() {
        SimHarness h = SimHarness.builder().factions(2).build();
        MatchState s = KernelAccess.state(h.core());
        s.treasury().grant(0, FOOD, 5_000);
        long before = s.treasury().mix(0);
        assertThat(s.treasury().save())
                .hasSize(s.treasury().factions() * s.treasury().resources() * 5);
        s.treasury().loot(0, FOOD, 0);
        assertThat(s.treasury().mix(0)).as("a zero loot is no loot").isEqualTo(before);
        s.treasury().loot(0, FOOD, 3_000);
        assertThat(s.treasury().mix(0)).isNotEqualTo(before);
        assertThat(s.treasury().save())
                .hasSize(s.treasury().factions() * s.treasury().resources() * 6);
    }

    @Test
    void lootedRoundTripsAndTheResumedMatchKeepsTheLiveHash() {
        SimHarness h = fightWon();
        MatchState s = KernelAccess.state(h.core());
        assertThat(s.treasury().looted(0, FOOD)).isEqualTo(11_000);
        MatchSnapshot saved = roundTrip(h.core().snapshot());
        MatchCore resumed = MatchCore.restore(saved, TemplateSet.builtin(), CorePorts.of(() -> 0L));
        assertThat(KernelAccess.state(resumed).treasury().looted(0, FOOD)).isEqualTo(11_000);
        assertThat(KernelAccess.state(resumed).treasury().broken()).isNull();
        assertThat(resumed.stateHash()).isEqualTo(h.stateHash());
        for (int i = 0; i < 5; i++) {
            long tick = h.tick() + 1;
            h.core().step(new TickInputs(tick, List.of()));
            resumed.step(new TickInputs(tick, List.of()));
            assertThat(resumed.stateHash()).isEqualTo(h.stateHash());
        }
    }

    @Test
    void anOldTreasuryWithoutTheLootedBlockStillLoads() {
        SimHarness h = SimHarness.builder().factions(2).build();
        KernelAccess.state(h.core()).treasury().grant(1, FOOD, 7_000);
        MatchSnapshot saved = roundTrip(h.core().snapshot());
        JsonObject json = MatchSnapshotCodec.encode(JsonOps.INSTANCE, saved).getAsJsonObject();
        assertThat(json.getAsJsonArray("treasury")).hasSize(2 * 4 * 5);
        MatchCore resumed = MatchCore.restore(saved, TemplateSet.builtin(), CorePorts.of(() -> 0L));
        assertThat(KernelAccess.state(resumed).treasury().stock(1, FOOD)).isEqualTo(7_000);
        assertThat(resumed.stateHash()).isEqualTo(h.stateHash());
    }

    @Test
    void aFightWithLootReplaysToTheLiveHash() {
        SimHarness h = fightWon();
        h.runTicks(20);
        var replay = h.replay();
        assertThat(replay.matches()).isTrue();
        assertThat(replay.finalHash()).isEqualTo(h.stateHash());
    }
}
