package dev.strategos.mod.server.persist;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CorePorts;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.persist.MatchSnapshotCodec;
import dev.strategos.core.sim.persist.Migrations;
import dev.strategos.core.testkit.SimHarness;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** CORE-07.2: a 500-unit snapshot written as compressed NBT and read back keeps its state hash. */
class MatchSnapshotNbtTest {

    @Test
    void aFiveHundredUnitSnapshotSurvivesNbtOnDisk(@TempDir Path dir) throws IOException {
        SimHarness h = SimHarness.builder().size(64, 64).build();
        int[] west = h.spawn(new FactionSlot(0), new Vec2d(16, 16), 250, 12.0);
        int[] east = h.spawn(new FactionSlot(1), new Vec2d(48, 48), 250, 12.0);
        h.submit(new PlayerSlot(0), Move.replace(UnitSet.of(Arrays.copyOf(west, 200)), new Vec2d(48, 16)));
        h.submit(new PlayerSlot(1), Move.replace(UnitSet.of(Arrays.copyOf(east, 100)), new Vec2d(16, 48)));
        h.runTicks(30);
        MatchSnapshot snapshot = h.core().snapshot();
        assertThat(snapshot.units()).hasSize(500);
        Path file = dir.resolve("match.nbt");

        NbtIo.writeCompressed((CompoundTag) MatchSnapshotCodec.encode(NbtOps.INSTANCE, snapshot), file);
        CompoundTag read = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        MatchSnapshot decoded = MatchSnapshotCodec.decode(NbtBridge.dynamic(read), Migrations.production())
                .getOrThrow();

        assertThat(decoded).isEqualTo(snapshot);
        MatchCore restored = MatchCore.restore(decoded, TemplateSet.builtin(), CorePorts.of(() -> 0L));
        assertThat(restored.stateHash()).isEqualTo(h.stateHash());
    }
}
