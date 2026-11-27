package dev.strategos.devtools.demo;

import dev.strategos.core.protocol.AttackMove;
import dev.strategos.core.protocol.Target;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.protocol.UseAbility;
import dev.strategos.core.shared.AbilityId;
import java.util.List;
import java.util.Locale;

/** M10: horsemen use {@code charge} on the approach; its cooldown shows. */
final class ChargeChapter implements DemoChapter {
    static final int RIDERS = 6;
    static final int FOES = 4;
    static final AbilityId CHARGE = new AbilityId("charge");

    @Override
    public String id() {
        return "charge";
    }

    @Override
    public String title() {
        return "The horsemen's charge";
    }

    @Override
    public List<String> groups() {
        return List.of("m10", "combat");
    }

    @Override
    public void script(DemoScript s) {
        s.run(Stage::match)
                .run(st -> {
                    st.units(Ids.HORSEMAN, 0, -30, 0, RIDERS, 2);
                    st.units(Ids.INFANTRY, 1, 22, 0, FOES, 2);
                })
                .look(0, 0, 70)
                .until(
                        "both sides",
                        10,
                        st -> Fights.alive(st, 0, Ids.HORSEMAN) >= RIDERS && Fights.alive(st, 1, Ids.INFANTRY) >= FOES)
                .say("Six horsemen, four spearmen far off")
                .pause(3)
                .say("On the approach they charge: faster, harder")
                .run(st -> st.admin(UseAbility.replace(UnitSet.of(st.handles(0, Ids.HORSEMAN)), CHARGE, Target.none())))
                .until(
                        "the charge begun",
                        10,
                        st -> st.q().abilityCooldown(st.handles(0, Ids.HORSEMAN)[0], "charge") > 0)
                .run(st -> st.admin(AttackMove.replace(UnitSet.of(st.handles(0, Ids.HORSEMAN)), st.at(22, 0))))
                .run(st -> DemoDirector.say(
                        st,
                        String.format(
                                Locale.ROOT,
                                "Charge: %d s to rest before it can be used again",
                                st.q().abilityCooldown(st.handles(0, Ids.HORSEMAN)[0], "charge") / 20)))
                .until("the foes beaten", 90, st -> Fights.alive(st, 1, Ids.INFANTRY) == 0)
                .say("The cooldown keeps counting down")
                .pause(5);
    }
}
