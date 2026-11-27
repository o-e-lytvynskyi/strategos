package dev.strategos.mod.client.territory;

import dev.strategos.core.protocol.MatchJoin;
import java.util.BitSet;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/**
 * The borders' second dirty pass (M9 spec §1.6 row 25): the sections a territory version touched are dirtied once more
 * {@value BorderRenderer#ECHO_TICKS} client ticks later, so a section whose build in flight took the old borders is
 * re-meshed (Sodium lost some first marks). Held by {@code StrategosClient}; client thread only.
 */
public final class BorderEcho {
    private final BitSet sections = new BitSet();
    private @Nullable MatchJoin join;
    private int sectionsX;
    private int ticksLeft;

    /** A version touched {@code touched}: they are dirtied again once the count-down from now ends. */
    public void arm(BitSet touched, int sectionsX, MatchJoin join) {
        MatchJoin last = this.join;
        if (last != null && (!last.equals(join) || this.sectionsX != sectionsX)) { // another match, or placed anew
            sections.clear();
        }
        sections.or(touched);
        this.join = join;
        this.sectionsX = sectionsX;
        ticksLeft = BorderRenderer.ECHO_TICKS;
    }

    /** Once a client tick: when the count-down ends, the sections are dirtied again. */
    public void tick() {
        MatchJoin j = join;
        if (j == null || ticksLeft <= 0 || --ticksLeft > 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            BorderRenderer.dirty(mc.levelRenderer, sections, sectionsX, j);
        }
        sections.clear();
        join = null;
    }
}
