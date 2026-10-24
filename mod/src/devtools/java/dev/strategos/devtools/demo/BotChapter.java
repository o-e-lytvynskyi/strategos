package dev.strategos.devtools.demo;

import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.SetAi;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.mod.server.content.Scenarios;
import java.util.List;
import java.util.Objects;

/** M4: the computer's economy on its own: its workers gather, it trains more and lays out its base. */
final class BotChapter implements DemoChapter {
    @Override
    public String id() {
        return "bot";
    }

    @Override
    public String title() {
        return "The computer builds its base";
    }

    @Override
    public List<String> groups() {
        return List.of("m4", "ai");
    }

    @Override
    public void script(DemoScript s) {
        s.run(st -> {
                    for (int k = 0; k < 16; k++) { // a wood to the north, berries to the east
                        st.oak(-12 + (k % 8) * 3, -22 - (k / 8) * 3, 5);
                    }
                    for (int k = 0; k < 8; k++) {
                        st.bush(18 + (k % 4) * 2, -2 + (k / 4) * 2);
                    }
                })
                .run(st -> st.match(DemoStage.PLOT))
                .run(st -> {
                    st.building(Ids.CIVIC_CENTRE, 1, 0, 0);
                    st.building(Ids.CIVIC_CENTRE, 0, -80, 80); // the player's, far off, so the match goes on
                    st.units(Ids.WORKER, 1, 0, 9, 5, 2);
                    st.admin(new Grant(new FactionSlot(1), "wood", 400));
                    st.admin(new Grant(new FactionSlot(1), "stone", 200));
                    String script = Objects.requireNonNull(Scenarios.script(st.server(), "economy"), "the economy");
                    st.admin(new SetAi(new FactionSlot(1), "economy", script));
                })
                .look(0, 0, 60)
                .until("the town", 10, st -> !st.buildings(1, Ids.CIVIC_CENTRE).isEmpty())
                .say("The computer's town: nobody gives it orders")
                .pause(8)
                .say("It gathers, trains and picks sites; sped up")
                .fast("three buildings of its own", 600, st -> built(st) >= 3)
                .say("Every building here is its own choice")
                .pause(6);
    }

    /** The computer's finished buildings bar its civic centre. */
    private static long built(DemoStage st) {
        return st.q().buildings().stream()
                .filter(b -> b.faction() == 1
                        && b.state() == BuildingStore.COMPLETE
                        && !b.template().equals(Ids.CIVIC_CENTRE))
                .count();
    }
}
