package dev.strategos.core.client.hud;

import dev.strategos.core.content.CorePaths;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.ValuePath;
import dev.strategos.core.protocol.BuildingState;
import dev.strategos.core.protocol.CancelBuilding;
import dev.strategos.core.protocol.CancelTrain;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.Demolish;
import dev.strategos.core.protocol.Reseed;
import dev.strategos.core.protocol.SetAutoReseed;
import dev.strategos.core.protocol.SetStance;
import dev.strategos.core.protocol.Stop;
import dev.strategos.core.protocol.Train;
import dev.strategos.core.protocol.TrainBatch;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.Stance;
import dev.strategos.core.shared.TemplateId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * What a click on the command card or on the selection panel's queue does (UI-06): one click sends at most one
 * command through {@code send} (train, reseed, stop, or cancelling a queue entry), turns the card's page, or arms
 * placement with a building. It keeps whether the build page is open and whether a demolish waits for its
 * confirming second click (BLD-09); a new selection drops both.
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
    private @Nullable Integer demolishAsked;
    /** The stance each unit was last given from this card: what the client knows, the server not telling. */
    private final Map<Integer, Stance> chosen = new HashMap<>();

    /** Whether the build page is open. */
    public boolean buildPage() {
        return buildPage;
    }

    /** The selection now; the build page closes when it changed. */
    public void selected(List<Integer> units, @Nullable Integer building) {
        if (!units.equals(lastUnits) || !java.util.Objects.equals(building, lastBuilding)) {
            buildPage = false;
            demolishAsked = null;
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
            case AUTO_RESEED -> {
                if (building == null) {
                    yield Click.NONE;
                }
                send.accept(new SetAutoReseed(building.handle(), !building.autoReseed()));
                yield new Click(Effect.SENT, button.template());
            }
            case CANCEL_BUILDING -> {
                if (building == null) {
                    yield Click.NONE;
                }
                send.accept(new CancelBuilding(building.handle()));
                yield new Click(Effect.SENT, button.template());
            }
            case DEMOLISH -> {
                if (building == null) {
                    yield Click.NONE;
                }
                if (!Integer.valueOf(building.handle()).equals(demolishAsked)) {
                    demolishAsked = building.handle(); // the first click asks; the second confirms
                    yield new Click(Effect.PAGE, button.template());
                }
                demolishAsked = null;
                send.accept(new Demolish(building.handle()));
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
            case STANCE -> {
                Stance stance = button.stance();
                if (units.isEmpty() || stance == null) {
                    yield Click.NONE;
                }
                send.accept(new SetStance(
                        UnitSet.of(units.stream().mapToInt(Integer::intValue).toArray()), stance));
                for (int unit : units) {
                    chosen.put(unit, stance);
                }
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

    /**
     * The stance unit {@code handle} of template {@code template} is known to have: the last this card gave it, else
     * its template's ({@code Behaviour/stance}), else aggressive.
     */
    public Stance stanceOf(int handle, TemplateSet templates, int template) {
        Stance given = chosen.get(handle);
        if (given != null) {
            return given;
        }
        ValuePath path = templates.valuePaths().byPath(CorePaths.STANCE);
        double code = path == null || !templates.has(template) ? Double.NaN : templates.value(template, path.id());
        Stance own = Double.isFinite(code) ? Stance.of((int) code) : null;
        return own != null ? own : Stance.AGGRESSIVE;
    }

    /** The stance all of {@code stances} are, or null for none or a mix. */
    public static @Nullable Stance shared(List<Stance> stances) {
        Stance first = stances.isEmpty() ? null : stances.get(0);
        for (Stance s : stances) {
            if (s != first) {
                return null;
            }
        }
        return first;
    }

    /** Whether a demolish of building {@code handle} waits for its confirming click. */
    public boolean demolishAsked(int handle) {
        return Integer.valueOf(handle).equals(demolishAsked);
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
