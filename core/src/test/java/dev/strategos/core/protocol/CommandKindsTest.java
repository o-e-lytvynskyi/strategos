package dev.strategos.core.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.shared.Vec2d;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommandKindsTest {

    @Test
    void idsAreUnique() {
        List<String> ids =
                CommandKinds.DEFAULT.all().stream().map(CommandKind::id).toList();
        assertThat(ids)
                .doesNotHaveDuplicates()
                .containsExactly(
                        "strategos:move",
                        "strategos:stop",
                        "strategos:spawn_units",
                        "strategos:attack",
                        "strategos:attack_move",
                        "strategos:follow",
                        "strategos:grant",
                        "strategos:spawn_building",
                        "strategos:gather",
                        "strategos:place_building",
                        "strategos:train",
                        "strategos:cancel_train",
                        "strategos:set_rally",
                        "strategos:reseed",
                        "strategos:resign",
                        "strategos:rule_change",
                        "strategos:announce_wave",
                        "strategos:set_ai",
                        "strategos:build");
    }

    @Test
    void everyPermittedCommandHasExactlyOneKind() {
        List<Class<?>> permitted = Arrays.asList(Command.class.getPermittedSubclasses());
        for (Class<?> type : permitted) {
            assertThat(CommandKinds.DEFAULT.all())
                    .as("kinds of %s", type.getSimpleName())
                    .filteredOn(kind -> kind.type() == type)
                    .hasSize(1);
        }
        assertThat(CommandKinds.DEFAULT.all()).hasSameSizeAs(permitted);
    }

    @Test
    void ofResolvesEveryRecordType() {
        for (Command command : CommandCodecTest.samples()) {
            assertThat(CommandKinds.DEFAULT.of(command).type()).isSameAs(command.getClass());
        }
        assertThat(CommandKinds.DEFAULT.byId("strategos:stop")).isSameAs(CommandKinds.STOP);
        assertThat(CommandKinds.DEFAULT.byId("strategos:fly")).isNull();
    }

    @Test
    void onlyTheAdministratorsCommandsAreAdminOnly() {
        assertThat(CommandKinds.DEFAULT.all())
                .filteredOn(CommandKind::adminOnly)
                .containsExactly(
                        CommandKinds.SPAWN_UNITS,
                        CommandKinds.GRANT,
                        CommandKinds.SPAWN_BUILDING,
                        CommandKinds.RULE_CHANGE,
                        CommandKinds.SET_AI);
    }

    @Test
    void aCatalogueRejectsDuplicates() {
        assertThatThrownBy(() -> new CommandKinds(List.of(CommandKinds.MOVE, CommandKinds.MOVE)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CommandKinds(List.of(CommandKinds.STOP))
                        .of(new Move(UnitSet.of(), new Vec2d(0, 0), QueueMode.REPLACE, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
