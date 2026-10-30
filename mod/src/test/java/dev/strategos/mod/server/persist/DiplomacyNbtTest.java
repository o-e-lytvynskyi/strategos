package dev.strategos.mod.server.persist;

import static org.assertj.core.api.Assertions.assertThat;

import com.mojang.serialization.Dynamic;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.DiplomacySetup;
import dev.strategos.core.sim.diplomacy.DiplomacyState;
import dev.strategos.core.sim.persist.MatchSnapshotCodec;
import dev.strategos.core.sim.persist.Migrations;
import dev.strategos.core.testkit.SimHarness;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.Test;

class DiplomacyNbtTest {
    @Test
    void coopBindingsGrantsAndLockedTeamsSurviveTheServerNbtCodec() {
        PlayerSlot p0 = new PlayerSlot(0);
        PlayerSlot p1 = new PlayerSlot(1);
        FactionSlot f0 = new FactionSlot(0);
        FactionSlot f1 = new FactionSlot(1);
        var authority = new DiplomacyState(2)
                .withTeam(f1, 0)
                .bind(p0, f1)
                .bind(p1, f0)
                .grant(p1, p0)
                .lockTeams();
        SimHarness h = SimHarness.builder().factions(2).build();
        h.schedule(1, new DiplomacySetup(authority.snapshot()));
        h.runTicks(3);
        var tag = MatchSnapshotCodec.encode(NbtOps.INSTANCE, h.core().snapshot());
        var decoded = MatchSnapshotCodec.decode(new Dynamic<>(NbtOps.INSTANCE, tag), Migrations.production())
                .getOrThrow();
        assertThat(decoded.diplomacy().snapshot()).isEqualTo(authority.snapshot());
        var restored = SimHarness.restore(decoded, TemplateSet.builtin());
        assertThat(restored.stateHash()).isEqualTo(h.stateHash());
        assertThat(restored.queries().diplomacy().controls(p0, f0)).isTrue();
        assertThat(restored.queries().diplomacy().factionOf(p0)).contains(f1);
        assertThat(restored.queries().diplomacy().teamsLocked()).isTrue();
    }
}
