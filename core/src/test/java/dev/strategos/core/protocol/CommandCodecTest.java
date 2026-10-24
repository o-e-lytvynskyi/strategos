package dev.strategos.core.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommandCodecTest {
    private static final Codec<Command> CODEC = CommandKinds.DEFAULT.codec();

    /** One command of every kind the default catalogue lists. */
    static List<Command> samples() {
        return List.of(
                new Move(UnitSet.of(3, 1, 2), new Vec2d(12.5, 8.5), QueueMode.REPLACE, 0),
                new Stop(UnitSet.of(5)),
                new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(1), new Vec2d(4, 8), 8, 2.0),
                new Attack(UnitSet.of(4, 6), 0x300007, QueueMode.QUEUE),
                new AttackMove(UnitSet.of(2), new Vec2d(30.25, 9), QueueMode.PUSH_FRONT),
                new Follow(UnitSet.of(1, 9), QueueMode.REPLACE),
                new Grant(new FactionSlot(1), "wood", 250),
                new SpawnBuilding(new TemplateId("strategos:building/house"), new FactionSlot(0), -3, 40, 2),
                new Gather(UnitSet.of(7, 8), 12, -5, 300, QueueMode.QUEUE),
                new PlaceBuilding(
                        new TemplateId("strategos:building/house"), 40, 7, 1, UnitSet.of(2, 3), QueueMode.REPLACE),
                new Train(0x80003, new TemplateId("strategos:unit/worker")),
                new CancelTrain(0x80003, 4),
                new SetRally(UnitSet.of(0x80003, 0x80004), 30, 70, -2),
                new Reseed(0x80003),
                new Resign(new FactionSlot(1)),
                new RuleChange("victory", "civic_centre"),
                new AnnounceWave(40, -12, 600),
                new SetAi(new FactionSlot(1), "first_loop", "{}"),
                Build.replace(UnitSet.of(1, 2), 524_289));
    }

    @Test
    void everyKindRoundTripsThroughJson() {
        assertThat(samples())
                .extracting(CommandKinds.DEFAULT::of)
                .containsExactlyElementsOf(CommandKinds.DEFAULT.all());
        for (Command command : samples()) {
            JsonElement json = CODEC.encodeStart(JsonOps.INSTANCE, command).getOrThrow();
            assertThat(json.getAsJsonObject().get("kind").getAsString())
                    .isEqualTo(CommandKinds.DEFAULT.of(command).id());
            assertThat(CODEC.parse(JsonOps.INSTANCE, json).getOrThrow()).isEqualTo(command);
        }
    }

    @Test
    void readsTheWireFormat() {
        JsonElement json = JsonParser.parseString(
                "{\"kind\":\"strategos:move\",\"units\":[2,1],\"target\":{\"x\":1.5,\"z\":2.0},\"mode\":\"replace\",\"formation\":0}");
        assertThat(CODEC.parse(JsonOps.INSTANCE, json).getOrThrow())
                .isEqualTo(Move.replace(UnitSet.of(1, 2), new Vec2d(1.5, 2.0)));
    }

    @Test
    void anUnknownKindFailsNamingIt() {
        JsonElement json = JsonParser.parseString("{\"kind\":\"strategos:fly\",\"units\":[1]}");
        DataResult<Command> result = CODEC.parse(JsonOps.INSTANCE, json);
        assertThat(result.error()).isPresent();
        assertThat(result.error().get().message()).contains("strategos:fly");
    }

    @Test
    void anUnknownQueueModeFailsNamingIt() {
        JsonElement json = JsonParser.parseString(
                "{\"kind\":\"strategos:move\",\"units\":[1],\"target\":{\"x\":0,\"z\":0},\"mode\":\"later\",\"formation\":0}");
        DataResult<Command> result = CODEC.parse(JsonOps.INSTANCE, json);
        assertThat(result.error()).isPresent();
        assertThat(result.error().get().message()).contains("later");
    }
}
