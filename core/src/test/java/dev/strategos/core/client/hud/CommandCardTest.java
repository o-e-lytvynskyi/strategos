package dev.strategos.core.client.hud;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.client.model.ClientMatchModel;
import dev.strategos.core.client.select.Selection;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.BuildingState;
import dev.strategos.core.protocol.CancelTrain;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.FactionState;
import dev.strategos.core.protocol.Reseed;
import dev.strategos.core.protocol.Stop;
import dev.strategos.core.protocol.Train;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.protocol.WireCodec;
import dev.strategos.core.shared.TemplateId;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** UI-06.2: one click on "train" sends exactly one command, and the card and panel show what the client model holds. */
class CommandCardTest {
    static final TemplateSet T = TemplateSet.builtin();
    static final List<String> RESOURCES = List.of("food", "wood", "stone", "iron");
    static final int MATCH = 3;
    static final HudRect CARD = HudLayout.compute(1920, 1080, 3).commandCard();
    static final int PAD = HudTheme.DEFAULT.padding();

    static int index(String id) {
        return java.util.Objects.requireNonNull(T.byId(new TemplateId(id))).netIndex();
    }

    static final int CENTRE = index("strategos:building/civic_centre");
    static final int FIELD = index("strategos:building/field");
    static final int WORKER = index("strategos:unit/worker");

    static BuildingState.Entry centre(List<Integer> queue, int trained) {
        return new BuildingState.Entry(
                1 << 20,
                CENTRE,
                0,
                4,
                4,
                0,
                1,
                CommandCard.COMPLETE,
                1000,
                255,
                true,
                queue,
                false,
                0,
                0,
                trained,
                -1,
                0);
    }

    static FactionState faction(long food, long wood) {
        return new FactionState(
                MATCH,
                0,
                10,
                List.of(food, wood, 0L, 0L),
                List.of(0L, 0L, 0L, 0L),
                List.of(0L, 0L, 0L, 0L),
                3,
                20,
                1,
                List.of(0, 0, 0, 0));
    }

    /** The client model after the wire carried {@code entries} and {@code faction}, as a player receives them. */
    static ClientMatchModel model(FactionState faction, BuildingState.Entry... entries) {
        ClientMatchModel model = new ClientMatchModel();
        BuildingState state = new BuildingState(MATCH, 10, List.of(entries));
        model.accept(WireCodec.decode(BuildingState.WIRE, WireCodec.encode(BuildingState.WIRE, state)), MATCH);
        model.accept(WireCodec.decode(FactionState.WIRE, WireCodec.encode(FactionState.WIRE, faction)), MATCH);
        return model;
    }

    static CommandCard card(
            ClientMatchModel model, List<Integer> unitTemplates, BuildingState.@Nullable Entry b, boolean buildPage) {
        return CommandCard.of(new CommandCard.Input(T, RESOURCES, model.faction(), unitTemplates, buildPage), b);
    }

    static double[] centreOf(HudRect r) {
        return new double[] {r.x() + r.width() / 2.0, r.y() + r.height() / 2.0};
    }

    @Test
    void oneTrainClickSendsOneCommand() {
        ClientMatchModel model = model(faction(100_000, 0), centre(List.of(), 0));
        Selection selection = new Selection(0);
        selection.clickBuilding(1 << 20, 0);
        BuildingState.Entry b = model.buildings().get(0);
        assertThat(selection.building()).isEqualTo(b.handle());
        CommandCard card = card(model, List.of(), b, false);
        assertThat(card.page()).isEqualTo(CommandCard.Page.BUILDING);
        assertThat(card.buttons())
                .as("the centre trains workers, at the worker's 60 food, which 100 covers; it can be demolished")
                .containsExactly(
                        new CommandCard.Button(CommandCard.Kind.TRAIN, 0, WORKER, List.of(60_000L, 0L, 0L, 0L), true),
                        new CommandCard.Button(
                                CommandCard.Kind.DEMOLISH, CommandCard.SLOTS - 1, CENTRE, List.of(), true));

        CardController controller = new CardController();
        List<Command> sent = new ArrayList<>();
        double[] at = centreOf(CommandCard.cell(CARD, PAD, 0));
        CardController.Click click =
                controller.click(card.at(CARD, PAD, at[0], at[1]), selection.handles(), b, T, sent::add);
        assertThat(click.effect()).isEqualTo(CardController.Effect.SENT);
        assertThat(sent).containsExactly(new Train(1 << 20, new TemplateId("strategos:unit/worker")));

        double[] beside = centreOf(CommandCard.cell(CARD, PAD, 1));
        controller.click(card.at(CARD, PAD, beside[0], beside[1]), selection.handles(), b, T, sent::add);
        controller.click(card.at(CARD, PAD, CARD.x() - 1, CARD.y()), selection.handles(), b, T, sent::add);
        assertThat(sent).as("an empty cell and a point outside send nothing").hasSize(1);
    }

    @Test
    void thePanelShowsTheModelsQueueAndACancelSendsOne() {
        ClientMatchModel model = model(faction(0, 0), centre(List.of(WORKER, WORKER), 450));
        BuildingState.Entry b = model.buildings().get(0);
        SelectionPanel panel = SelectionPanel.of(SelectionSummary.EMPTY, null, b);
        assertThat(panel.kind()).isEqualTo(SelectionPanel.Kind.BUILDING);
        assertThat(panel.building()).isEqualTo(centre(List.of(WORKER, WORKER), 450));
        assertThat(card(model, List.of(), b, false).buttons().get(0).affordable())
                .as("no food: the button shows it")
                .isFalse();

        HudRect r = HudLayout.compute(1920, 1080, 3).selectionPanel();
        double[] second = centreOf(SelectionPanel.queueCell(r, PAD, 1));
        int k = panel.queueAt(r, PAD, second[0], second[1]);
        assertThat(k).isEqualTo(1);
        List<Command> sent = new ArrayList<>();
        new CardController().cancel(b, k, sent::add);
        assertThat(sent).containsExactly(new CancelTrain(1 << 20, 1));
        double[] third = centreOf(SelectionPanel.queueCell(r, PAD, 2));
        assertThat(panel.queueAt(r, PAD, third[0], third[1]))
                .as("two entries only")
                .isEqualTo(-1);
    }

    @Test
    void unitsShowTheBuildPageWhichArmsPlacement() {
        ClientMatchModel model = model(faction(0, 100_000));
        CardController controller = new CardController();
        List<Integer> units = List.of(5, 6);
        controller.selected(units, null);
        CommandCard main = card(model, List.of(WORKER), null, controller.buildPage());
        assertThat(main.buttons())
                .extracting(CommandCard.Button::kind)
                .containsExactly(CommandCard.Kind.BUILD_PAGE, CommandCard.Kind.STOP);
        List<Command> sent = new ArrayList<>();
        assertThat(controller
                        .click(main.buttons().get(0), units, null, T, sent::add)
                        .effect())
                .isEqualTo(CardController.Effect.PAGE);
        CommandCard build = card(model, List.of(WORKER), null, controller.buildPage());
        assertThat(build.page()).isEqualTo(CommandCard.Page.BUILD);
        assertThat(build.buttons())
                .filteredOn(b -> b.kind() == CommandCard.Kind.BUILD)
                .extracting(CommandCard.Button::template)
                .containsExactlyElementsOf(CommandCard.buildings(T));
        CommandCard.Button house = build.buttons().stream()
                .filter(b -> b.template() == index("strategos:building/house"))
                .findFirst()
                .orElseThrow();
        assertThat(house.cost()).containsExactly(0L, 80_000L, 0L, 0L);
        assertThat(house.affordable()).isTrue();
        CardController.Click arm = controller.click(house, units, null, T, sent::add);
        assertThat(arm).isEqualTo(new CardController.Click(CardController.Effect.ARM, house.template()));
        assertThat(controller.buildPage()).as("placing closes the page").isFalse();
        controller.click(main.buttons().get(1), units, null, T, sent::add);
        assertThat(sent).as("only stop sent anything").containsExactly(new Stop(UnitSet.of(5, 6)));

        controller.click(main.buttons().get(0), units, null, T, sent::add);
        controller.selected(List.of(5), null);
        assertThat(controller.buildPage()).as("a new selection closes the page").isFalse();
        assertThat(card(model, List.of(index("strategos:unit/infantry")), null, false)
                        .buttons())
                .extracting(CommandCard.Button::kind)
                .as("a spearman builds nothing")
                .containsExactly(CommandCard.Kind.STOP);
    }

    @Test
    void anEmptyFieldOffersItsReseed() {
        BuildingState.Entry ripe = new BuildingState.Entry(
                (1 << 20) + 1,
                FIELD,
                0,
                9,
                9,
                0,
                1,
                CommandCard.COMPLETE,
                1000,
                255,
                true,
                List.of(),
                false,
                0,
                0,
                0,
                120_000,
                0);
        BuildingState.Entry empty = new BuildingState.Entry(
                (1 << 20) + 1,
                FIELD,
                0,
                9,
                9,
                0,
                1,
                CommandCard.COMPLETE,
                1000,
                255,
                true,
                List.of(),
                false,
                0,
                0,
                0,
                0,
                0);
        BuildingState.Entry ripening = new BuildingState.Entry(
                (1 << 20) + 1,
                FIELD,
                0,
                9,
                9,
                0,
                1,
                CommandCard.COMPLETE,
                1000,
                255,
                true,
                List.of(),
                false,
                0,
                0,
                0,
                0,
                300);
        ClientMatchModel model = model(faction(0, 50_000), ripe, empty, ripening);
        CommandCard.Button demolish =
                new CommandCard.Button(CommandCard.Kind.DEMOLISH, CommandCard.SLOTS - 1, FIELD, List.of(), true);
        assertThat(card(model, List.of(), model.buildings().get(0), false).buttons())
                .as("a ripe field: only its demolish")
                .containsExactly(demolish);
        assertThat(card(model, List.of(), model.buildings().get(2), false).buttons())
                .containsExactly(demolish);
        CommandCard card = card(model, List.of(), model.buildings().get(1), false);
        assertThat(card.buttons())
                .containsExactly(
                        new CommandCard.Button(CommandCard.Kind.RESEED, 0, FIELD, List.of(0L, 40_000L, 0L, 0L), true),
                        demolish);
        List<Command> sent = new ArrayList<>();
        new CardController()
                .click(card.buttons().get(0), List.of(), model.buildings().get(1), T, sent::add);
        assertThat(sent).containsExactly(new Reseed((1 << 20) + 1));
    }

    @Test
    void templatesNameTheirIcons() {
        assertThat(CommandCard.icon(T, WORKER)).isEqualTo("minecraft:wooden_axe");
        assertThat(CommandCard.icon(T, FIELD)).isEqualTo("minecraft:wheat");
        for (var t : T.all()) {
            assertThat(CommandCard.icon(T, t.netIndex())).as(t.id().value()).isNotNull();
        }
    }

    @Test
    void anotherFactionsBuildingOffersNothing() {
        BuildingState.Entry theirs = new BuildingState.Entry(
                1 << 20,
                CENTRE,
                1,
                4,
                4,
                0,
                1,
                CommandCard.COMPLETE,
                1000,
                255,
                false,
                List.of(),
                false,
                0,
                0,
                0,
                -1,
                0);
        ClientMatchModel model = model(faction(1_000_000, 0), theirs);
        Selection selection = new Selection(0);
        selection.clickBuilding(theirs.handle(), 1);
        assertThat(selection.building()).isNull();
        assertThat(selection.shownBuilding()).isEqualTo(theirs.handle());
        assertThat(card(model, List.of(), model.buildings().get(0), false).buttons())
                .isEmpty();
    }

    /** UI-08.1: the fifteen cells take Alt and the classics' grid in reading order, and the card lays them out so. */
    @Test
    void theGridHotkeysAndTheLayout() {
        assertThat(CommandCard.SLOTS).isEqualTo(15);
        StringBuilder keys = new StringBuilder();
        for (int slot = 0; slot < CommandCard.SLOTS; slot++) {
            keys.append(CommandCard.hotkey(slot));
        }
        assertThat(keys).hasToString("QWERTASDFGZXCVB");
        for (dev.strategos.core.client.input.Action a : dev.strategos.core.client.input.Action.values()) {
            if (a.cardSlot() < 0) {
                continue;
            }
            var map = dev.strategos.core.client.input.ActionMap.defaults();
            var key = dev.strategos.core.client.input.KeyCode.key(CommandCard.hotkey(a.cardSlot()));
            assertThat(map.resolve(
                            dev.strategos.core.client.input.ActionContext.RTS,
                            key,
                            dev.strategos.core.client.input.Modifiers.ALT))
                    .as("Alt + %s", CommandCard.hotkey(a.cardSlot()))
                    .isEqualTo(a);
        }
        var map = dev.strategos.core.client.input.ActionMap.defaults();
        assertThat(map.resolve(
                        dev.strategos.core.client.input.ActionContext.RTS,
                        dev.strategos.core.client.input.KeyCode.key('W'),
                        dev.strategos.core.client.input.Modifiers.NONE))
                .as("W alone still pans")
                .isEqualTo(dev.strategos.core.client.input.Action.CAMERA_PAN_FORWARD);
        HudRect first = CommandCard.cell(CARD, PAD, 0);
        HudRect sixth = CommandCard.cell(CARD, PAD, 5);
        HudRect last = CommandCard.cell(CARD, PAD, 14);
        assertThat(sixth.x()).as("A under Q").isEqualTo(first.x());
        assertThat(sixth.y()).isGreaterThan(first.y());
        assertThat(last.x()).as("B at the bottom right").isGreaterThan(first.x());
        assertThat(last.y()).isGreaterThan(sixth.y());
    }

    /** UI-08.2: a disabled button names what is missing exactly: the amounts, housing, the limit, a full queue. */
    @Test
    void aDisabledButtonNamesItsReason() {
        ClientMatchModel poor = model(faction(20_000, 0), centre(List.of(), 0));
        CommandCard.Button train =
                card(poor, List.of(), poor.buildings().get(0), false).buttons().get(0);
        assertThat(train.affordable()).isFalse();
        assertThat(train.reason()).isEqualTo("Needs 40 food");

        FactionState full = new FactionState(
                MATCH,
                0,
                10,
                List.of(1_000_000L, 0L, 0L, 0L),
                List.of(0L, 0L, 0L, 0L),
                List.of(0L, 0L, 0L, 0L),
                20,
                20,
                0,
                List.of(0, 0, 0, 0));
        ClientMatchModel housed = model(full, centre(List.of(), 0));
        assertThat(card(housed, List.of(), housed.buildings().get(0), false)
                        .buttons()
                        .get(0)
                        .reason())
                .isEqualTo("Needs housing");

        List<Integer> eight = List.of(WORKER, WORKER, WORKER, WORKER, WORKER, WORKER, WORKER, WORKER);
        ClientMatchModel queued = model(faction(1_000_000, 0), centre(eight, 0));
        assertThat(card(queued, List.of(), queued.buildings().get(0), false)
                        .buttons()
                        .get(0)
                        .reason())
                .isEqualTo("Queue full");

        ClientMatchModel one = model(faction(10_000_000, 10_000_000), centre(List.of(), 0));
        CommandCard page = CommandCard.of(
                new CommandCard.Input(T, RESOURCES, one.faction(), List.of(WORKER), true, one.buildings(), false),
                null);
        CommandCard.Button centre = page.buttons().stream()
                .filter(b -> b.kind() == CommandCard.Kind.BUILD && b.template() == CENTRE)
                .findFirst()
                .orElseThrow();
        assertThat(centre.reason()).as("one centre a faction").isEqualTo("Limit reached");
    }

    /** UI-08.3: the tooltip's numbers are the server's: each line equals the stat service's value for the template. */
    @Test
    void theTooltipNumbersAreTheServersBreakdown() {
        var server = new dev.strategos.core.sim.stats.StatService(
                T, 1, new dev.strategos.core.sim.IdentityStore(), new dev.strategos.core.sim.MatchBus());
        for (int template : List.of(WORKER, index("strategos:unit/infantry"), CENTRE)) {
            List<CommandCard.Line> lines = CommandCard.breakdown(T, template);
            assertThat(lines).isNotEmpty();
            for (CommandCard.Line line : lines) {
                int path = server.path(line.path());
                double unit = line.path().equals("UnitMotion/walkSpeed") ? 20 : 1;
                assertThat(line.value())
                        .as("%s of %s", line.path(), T.byIndex(template).id().value())
                        .isEqualTo(server.blockValue(template, 0, path) * unit);
            }
        }
    }

    /** CMB-16.3: the archer's tooltip names the high-ground source and its knobs; a melee unit has none of it. */
    @Test
    void theArcherBreakdownNamesTheHighGroundSource() {
        List<CommandCard.Line> archer = CommandCard.breakdown(T, index("strategos:unit/archer"));
        assertThat(archer)
                .extracting(CommandCard.Line::label, CommandCard.Line::value)
                .contains(
                        org.assertj.core.groups.Tuple.tuple("Pierce, ranged", 6.0),
                        org.assertj.core.groups.Tuple.tuple("Range, blocks", 20.0),
                        org.assertj.core.groups.Tuple.tuple("High ground, % range a block", 5.0),
                        org.assertj.core.groups.Tuple.tuple("High ground, % damage a block", 4.0),
                        org.assertj.core.groups.Tuple.tuple("High ground, up to blocks", 8.0));
        assertThat(CommandCard.breakdown(T, index("strategos:unit/infantry")))
                .extracting(CommandCard.Line::label)
                .noneMatch(l -> l.startsWith("High ground") || l.endsWith("ranged") || l.startsWith("Range"));
    }
}
