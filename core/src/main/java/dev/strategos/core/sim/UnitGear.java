package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;

/**
 * What unit {@code handle} shows in its hands changed (ECO-05, M3 spec §2.4): the tool of its task ({@link #NONE},
 * {@link #AXE}, {@link #PICKAXE}, {@link #HOE}, {@link #BUILDING}) and the load it carries, a resource and subtype of
 * the match's economy, -1 for none. The mod puts the tool in the body's main hand and the load in its off hand, which
 * vanilla shows to everyone tracking the body.
 */
@CoreApi
public record UnitGear(int handle, int tool, int resource, int subtype) implements MatchEvent {
    public static final int NONE = 0;
    public static final int AXE = 1;
    public static final int PICKAXE = 2;
    public static final int HOE = 3;
    /** The builder's tool, a mace until the style guide picks one (M3 spec §1.6 row 9). */
    public static final int BUILDING = 4;
}
