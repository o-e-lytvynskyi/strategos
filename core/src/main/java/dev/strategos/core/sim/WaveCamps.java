package dev.strategos.core.sim;

import dev.strategos.core.sim.ai.ComputerPlayer;
import dev.strategos.core.sim.world.WritePriority;
import java.util.ArrayList;
import java.util.List;

/**
 * A wave's camp (AI-04): cosmetic blocks, a campfire between two banners, at the muster. They are raised when the wave
 * is announced and taken down when it leaves, as {@link WorldEffect}s through the same writer as every other block.
 * The camp's place is the computer player's own memory (saved and hashed with it), so a resumed match knows which
 * camps still stand and {@link #claims} gives them back to the reconciler.
 */
public final class WaveCamps {
    /** The writer site of faction 0's camp; each faction's is this plus its number, far above a building's or a node's. */
    static final int SITE = 1 << 28;

    private static final String FIRE = "minecraft:campfire[lit=true]";
    private static final String BANNER = "minecraft:red_banner[rotation=0]";

    private WaveCamps() {}

    /** Raises faction {@code faction}'s camp at match-local column ({@code x}, {@code z}); none on ground with no floor. */
    public static void raise(MatchState s, int faction, int x, int z) {
        ComputerPlayer player = s.aiSlots().get(faction);
        double floor = dev.strategos.core.sim.build.Placing.floor(
                s.surfaces(), s.config().bounds(), x, z);
        if (player == null || !Double.isFinite(floor)) {
            return;
        }
        int y = (int) Math.floor(floor + 1e-6);
        player.setCamp(x, y, z);
        for (WorldEffect e : blocks(faction, x, y, z, false)) {
            s.outputs().add(e);
        }
    }

    /** Takes down the camp of every computer player whose wave is no longer mustering. */
    static void clearLeft(MatchState s) {
        AiSlots slots = s.aiSlots();
        if (!slots.any()) {
            return;
        }
        for (int f = 0; f < s.config().factions(); f++) {
            ComputerPlayer p = slots.get(f);
            if (p != null && p.campUp() && !p.mustering()) {
                int[] at = p.camp();
                for (WorldEffect e : blocks(f, at[0], at[1], at[2], true)) {
                    s.outputs().add(e);
                }
                p.clearCamp();
            }
        }
    }

    /** The camps still standing, for a resumed match's reconciler. */
    public static List<WorldEffect> claims(MatchState s) {
        List<WorldEffect> out = new ArrayList<>();
        for (int f = 0; f < s.config().factions(); f++) {
            ComputerPlayer p = s.aiSlots().get(f);
            if (p != null && p.campUp()) {
                int[] at = p.camp();
                out.addAll(blocks(f, at[0], at[1], at[2], false));
            }
        }
        return out;
    }

    private static List<WorldEffect> blocks(int faction, int x, int y, int z, boolean remove) {
        int site = SITE + faction;
        String fire = remove ? WorldEffect.AIR : FIRE;
        String banner = remove ? WorldEffect.AIR : BANNER;
        return List.of(
                new WorldEffect(site, WritePriority.COSMETIC, x, y, z, fire, 0),
                new WorldEffect(site, WritePriority.COSMETIC, x - 2, y, z, banner, 0),
                new WorldEffect(site, WritePriority.COSMETIC, x + 2, y, z, banner, 0));
    }
}
