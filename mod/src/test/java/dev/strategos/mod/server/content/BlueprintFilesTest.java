package dev.strategos.mod.server.content;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.PlaceholderBlueprints;
import dev.strategos.core.content.TemplateProblem;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

/**
 * BLD-01.1 and .2 in L2: the mod's blueprint files are the placeholders, written and read back equal, as any written
 * blueprint is; the lint refuses a block that burns, a blueprint without its door, and a rank in the bottom ring or
 * below what holds it up. {@code -Dstrategos.blueprints.update=true} writes the files from the placeholders.
 */
class BlueprintFilesTest {
    private static final String DIR = "data/strategos/structure/strategos/buildings/";

    private static String name(Blueprint b) {
        return b.id().substring(b.id().indexOf(':') + 1);
    }

    @Test
    void theCommittedBlueprintsAreThePlaceholders() throws IOException {
        for (Blueprint b : PlaceholderBlueprints.all()) {
            Path file = Path.of(
                    System.getProperty("strategos.repoRoot", "."), "mod/src/main/resources", DIR + name(b) + ".nbt");
            if (Boolean.getBoolean("strategos.blueprints.update")) {
                Files.createDirectories(file.getParent());
                NbtIo.writeCompressed(BlueprintLoader.write(b), file);
            }
            CompoundTag tag;
            assertThat(file).as("the committed blueprint").exists();
            try (InputStream in = Files.newInputStream(file)) {
                tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
            }
            assertThat(BlueprintLoader.read(b.id(), tag)).as(b.id()).isEqualTo(b);
        }
    }

    @Test
    void aWrittenBlueprintReadsBackEqualWhateverItsFilesBlockOrder() {
        for (Blueprint b : PlaceholderBlueprints.all()) {
            CompoundTag tag = BlueprintLoader.write(b);
            assertThat(BlueprintLoader.read(b.id(), tag)).isEqualTo(b);
            var blocks = tag.getList("blocks", 10);
            List<CompoundTag> reversed = new ArrayList<>();
            for (int i = blocks.size() - 1; i >= 0; i--) {
                reversed.add(blocks.getCompound(i));
            }
            blocks.clear();
            blocks.addAll(reversed);
            assertThat(BlueprintLoader.read(b.id(), tag)).as("listed backwards").isEqualTo(b);
        }
    }

    @Test
    void theLintRefusesABurningBlockAMissingDoorAndARankedBottomRing() {
        Blueprint house = PlaceholderBlueprints.house();
        List<String> palette = new ArrayList<>(house.palette());
        palette.set(2, "minecraft:oak_planks");
        Blueprint burning = rebuild(house, palette, house.markers());
        assertThat(TemplateLint.palette(burning, "house.json"))
                .extracting(TemplateProblem::message)
                .contains("strategos:house holds minecraft:oak_planks, which burns");
        assertThat(TemplateLint.palette(house, "house.json")).isEmpty();

        Blueprint doorless = rebuild(house, house.palette(), List.of(new Blueprint.Marker("spawn", 2, 2, 0)));
        assertThat(PlaceholderBlueprints.problems(doorless)).contains("strategos:house has no door marker");

        int[] ranks = new int[house.count()];
        for (int k = 0; k < ranks.length; k++) {
            ranks[k] = house.rank(k);
        }
        int bottom = 0; // block 0 is (0, 0, 0), a corner of the bottom ring
        ranks[bottom] = 5;
        Blueprint ranked = new Blueprint(
                house.id(),
                house.sizeX(),
                house.sizeY(),
                house.sizeZ(),
                house.palette(),
                blocks(house),
                ranks,
                house.markers());
        assertThat(PlaceholderBlueprints.problems(ranked))
                .anyMatch(p -> p.contains("two lowest layers of the outer ring and ranked 5"));
        assertThat(PlaceholderBlueprints.problems(house)).isEmpty();
    }

    @Test
    void theLintRefusesABlockEntityButTheCivicCentresTownBell() {
        Blueprint house = PlaceholderBlueprints.house();
        List<String> palette = new ArrayList<>(house.palette());
        palette.set(2, "minecraft:chest");
        assertThat(TemplateLint.palette(rebuild(house, palette, house.markers()), "house.json"))
                .extracting(TemplateProblem::message)
                .contains("strategos:house holds minecraft:chest, a block entity");
        Blueprint centre = PlaceholderBlueprints.civicCentre();
        assertThat(centre.palette()).contains(PlaceholderBlueprints.BELL_BLOCK);
        assertThat(TemplateLint.palette(centre, "civic_centre.json")).isEmpty();
        boolean bellStands = false;
        for (int k = 0; k < centre.count(); k++) {
            bellStands |= centre.x(k) == PlaceholderBlueprints.CENTRE_BELL_X
                    && centre.y(k) == PlaceholderBlueprints.CENTRE_BELL_Y
                    && centre.z(k) == PlaceholderBlueprints.CENTRE_BELL_Z
                    && centre.palette().get(centre.state(k)).equals(PlaceholderBlueprints.BELL_BLOCK);
        }
        assertThat(bellStands)
                .as("the bell stands on the floor just inside the doorway")
                .isTrue();
    }

    private static int[] blocks(Blueprint b) {
        int[] out = new int[b.count() * 4];
        for (int k = 0; k < b.count(); k++) {
            out[k * 4] = b.x(k);
            out[k * 4 + 1] = b.y(k);
            out[k * 4 + 2] = b.z(k);
            out[k * 4 + 3] = b.state(k);
        }
        return out;
    }

    private static Blueprint rebuild(Blueprint b, List<String> palette, List<Blueprint.Marker> markers) {
        int[] ranks = new int[b.count()];
        Arrays.setAll(ranks, b::rank);
        return new Blueprint(b.id(), b.sizeX(), b.sizeY(), b.sizeZ(), palette, blocks(b), ranks, markers);
    }
}
