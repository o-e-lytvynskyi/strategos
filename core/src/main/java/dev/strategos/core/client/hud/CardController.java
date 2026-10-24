package dev.strategos.core.client.hud;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.BuildingState;
import dev.strategos.core.protocol.CancelTrain;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.Reseed;
import dev.strategos.core.protocol.Stop;
import dev.strategos.core.protocol.Train;
import dev.strategos.core.protocol.TrainBatch;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.TemplateId;
import java.util.List;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * What a click on the command card or on the selection panel's queue does (UI-06): one click sends at most one
 * command through {@code send} (train, reseed, stop, or cancelling a queue entry), turns the card's page, or arms
 * placement with a building. It keeps whether the build page is open; a new selection closes it.
 */
public final class CardController {
    /** What a click did. */
    public enum Effect {
        NONE,
        SENT,
        PAGE,
        ARM
    }

    /** A click's effect, and for {@link Effect#ARM} the building template to place. */
    public record Click(Effect effect, int template) {
        static final Click NONE = new Click(Effect.NONE, -1);
    }

    private boolean buildPage;
    private List<Integer> lastUnits = List.of();
    private @Nullable Integer lastBuilding;

    /** Whether the build page is open. */
    public boolean buildPage() {
        return buildPage;
    }

    /** The selection now; the build page closes when it changed. */
    public void selected(List<Integer> units, @Nullable Integer building) {
        if (!units.equals(lastUnits) || !java.util.Objects.equals(building, lastBuilding)) {
            buildPage = false;
            lastUnits = List.copyOf(units);
            lastBuilding = building;
        }
    }

    /**
     * Button {@code button} clicked with {@code units} selected, or own building {@code building}: the command it
     * sends, if any, goes to {@code send}.
     */
    public Click click(
            CommandCard.@Nullable Button button,
            List<Integer> units,
            BuildingState.@Nullable Entry building,
            TemplateSet templates,
            Consumer<Command> send) {
        return click(button, units, building, templates, send, false);
    }

    /** As {@link #click}; with Shift held ({@code batch}), a train button queues a batch of five (PROD-02). */
    public Click click(
            CommandCard.@Nullable Button button,
            List<Integer> units,
            BuildingState.@Nullable Entry building,
            TemplateSet templates,
            Consumer<Command> send,
            boolean batch) {
        if (button == null) {
            return Click.NONE;
        }
        return switch (button.kind()) {
            case TRAIN -> {
                if (building == null || !templates.has(button.template())) {
                    yield Click.NONE;
                }
                TemplateId id = templates.byIndex(button.template()).id();
                send.accept(
                        batch
                                ? new TrainBatch(building.handle(), id, TrainBatch.MOST)
                                : new Train(building.handle(), id));
                yield new Click(Effect.SENT, button.template());
            }
            case RESEED -> {
                if (building == null) {
                    yield Click.NONE;
                }
                send.accept(new Reseed(building.handle()));
                yield new Click(Effect.SENT, button.template());
            }
            case STOP -> {
                if (units.isEmpty()) {
                    yield Click.NONE;
                }
                send.accept(new Stop(
                        UnitSet.of(units.stream().mapToInt(Integer::intValue).toArray())));
                yield new Click(Effect.SENT, -1);
            }
            case BUILD_PAGE -> {
                buildPage = true;
                yield new Click(Effect.PAGE, -1);
            }
            case BACK -> {
                buildPage = false;
                yield new Click(Effect.PAGE, -1);
            }
            case BUILD -> {
                buildPage = false;
                yield new Click(Effect.ARM, button.template());
            }
        };
    }

    /** Queue entry {@code index} of own building {@code building} clicked: it is cancelled. */
    public Click cancel(BuildingState.Entry building, int index, Consumer<Command> send) {
        if (!building.own() || index < 0 || index >= building.queue().size()) {
            return Click.NONE;
        }
        send.accept(new CancelTrain(building.handle(), index));
        return new Click(Effect.SENT, building.queue().get(index));
    }
}
