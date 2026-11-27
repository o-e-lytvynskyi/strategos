package dev.strategos.core.sim.formation;

import dev.strategos.core.protocol.FormationShape;
import dev.strategos.core.shared.DetMath;
import java.util.Arrays;

/**
 * One formation (ORD-06, 04 §9.4): a core entity, not a Minecraft one. It is a point, the anchor, that walks a route
 * ({@link #along} blocks from its start) at its slowest member's speed, a facing that turns toward the route's heading
 * no faster than its outermost place can keep up with, and a place for each member as an offset from the anchor along
 * and across the facing. Member {@code k} keeps place {@code k}; the places are chosen when the members change
 * ({@link FormationLayout}) and are never recomputed otherwise. What follows from these ({@link #ax anchor}, {@link
 * #lookX look-ahead point}, {@link #speed}) is derived on every tick and is neither saved nor hashed.
 */
public final class FormationController {
    private final int id;
    private final FormationShape shape;
    private double[] route; // x, z pairs from the formation's start to the end of the route
    private double[] cumulative; // distance along the route at each point, derived
    private double goalX;
    private double goalZ;
    private boolean more; // the route stops short of the goal (a partial or a truncated path): plan again from its end
    private double along;
    private double facingX;
    private double facingZ;
    private boolean holding;
    private boolean advancing; // the anchor walked this tick: its members walk to where their places will be
    private int waited;
    private int[] members = new int[0];
    private double[] lateral = new double[0];
    private double[] forward = new double[0];

    // derived on every tick
    private double ax;
    private double az;
    private double lookX;
    private double lookZ;
    private double speed;
    private double reach = 1;

    FormationController(int id, FormationShape shape, double[] route, boolean more, double goalX, double goalZ) {
        this.id = id;
        this.shape = shape;
        this.goalX = goalX;
        this.goalZ = goalZ;
        setRoute(route, more);
        this.facingX = 0;
        this.facingZ = 1;
        if (heading(0)) {
            this.facingX = headingX;
            this.facingZ = headingZ;
        }
        refresh();
    }

    /** A controller as a save keeps it; derived values follow from {@link #refresh()}. */
    FormationController(
            int id,
            FormationShape shape,
            double[] route,
            boolean more,
            double goalX,
            double goalZ,
            double along,
            double facingX,
            double facingZ,
            boolean holding,
            boolean advancing,
            int waited,
            int[] members,
            double[] lateral,
            double[] forward) {
        this.id = id;
        this.shape = shape;
        this.goalX = goalX;
        this.goalZ = goalZ;
        setRoute(route, more);
        this.along = along;
        this.facingX = facingX;
        this.facingZ = facingZ;
        this.holding = holding;
        this.advancing = advancing;
        this.waited = waited;
        setPlaces(members, lateral, forward);
        refresh();
    }

    public int id() {
        return id;
    }

    public FormationShape shape() {
        return shape;
    }

    public int size() {
        return members.length;
    }

    /** The handle of the unit that keeps place {@code k}. */
    public int member(int k) {
        return members[k];
    }

    /** Whether the formation has arrived and stands in its shape. */
    public boolean holding() {
        return holding;
    }

    /** Whether the anchor walked on in the last tick, so that members walk a moment ahead of their places. */
    public boolean advancing() {
        return advancing;
    }

    /** How many ticks it has waited, in this wait, for its members to reach their places. */
    public int waited() {
        return waited;
    }

    /** Distance walked along the route, in blocks. */
    public double along() {
        return along;
    }

    public double routeLength() {
        return cumulative[cumulative.length - 1];
    }

    public double facingX() {
        return facingX;
    }

    public double facingZ() {
        return facingZ;
    }

    public double goalX() {
        return goalX;
    }

    public double goalZ() {
        return goalZ;
    }

    /** Whether the route stops short of the goal. */
    public boolean more() {
        return more;
    }

    /** The anchor, the formation's centre, now. */
    public double anchorX() {
        return ax;
    }

    public double anchorZ() {
        return az;
    }

    /** The speed it walks at: its slowest member's, as of the last tick. */
    public double speed() {
        return speed;
    }

    /** The farthest a place lies from the anchor, in blocks (at least 1). */
    public double reach() {
        return reach;
    }

    /** Place {@code k}'s offset across the facing, positive to its left. */
    public double lateral(int k) {
        return lateral[k];
    }

    /** Place {@code k}'s offset along the facing, positive ahead. */
    public double forward(int k) {
        return forward[k];
    }

    /** Where place {@code k} is now. */
    public double slotX(int k) {
        return ax + facingX * forward[k] - facingZ * lateral[k];
    }

    public double slotZ(int k) {
        return az + facingZ * forward[k] + facingX * lateral[k];
    }

    /** Where place {@code k} will be a moment from now: what its member walks to. */
    public double placeX(int k) {
        return lookX + facingX * forward[k] - facingZ * lateral[k];
    }

    public double placeZ(int k) {
        return lookZ + facingZ * forward[k] + facingX * lateral[k];
    }

    /** Where place {@code k} is when the formation stands at the end of its route, as it faces now. */
    public double endSlotX(int k) {
        int last = route.length - 2;
        return route[last] + facingX * forward[k] - facingZ * lateral[k];
    }

    public double endSlotZ(int k) {
        int last = route.length - 2;
        return route[last + 1] + facingZ * forward[k] + facingX * lateral[k];
    }

    /** The end of the route. */
    public double anchorEndX() {
        return route[route.length - 2];
    }

    public double anchorEndZ() {
        return route[route.length - 1];
    }

    /** The route as x, z pairs. */
    public double[] route() {
        return route.clone();
    }

    double routeX(int p) {
        return route[2 * p];
    }

    double routeZ(int p) {
        return route[2 * p + 1];
    }

    int routePoints() {
        return route.length / 2;
    }

    int[] membersCopy() {
        return members.clone();
    }

    double[] lateralCopy() {
        return lateral.clone();
    }

    double[] forwardCopy() {
        return forward.clone();
    }

    void setSpeed(double speed) {
        this.speed = speed;
    }

    void setWaited(int waited) {
        this.waited = waited;
    }

    void setAdvancing(boolean advancing) {
        this.advancing = advancing;
    }

    void setHolding(boolean holding) {
        this.holding = holding;
    }

    void setGoal(double x, double z) {
        this.goalX = x;
        this.goalZ = z;
    }

    /** The places chosen anew, for {@code members}; {@code lateral} and {@code forward} are their offsets. */
    void setPlaces(int[] members, double[] lateral, double[] forward) {
        this.members = members.clone();
        this.lateral = lateral.clone();
        this.forward = forward.clone();
        double far = 1;
        for (int k = 0; k < members.length; k++) {
            far = Math.max(far, DetMath.length(lateral[k], forward[k]));
        }
        this.reach = far;
    }

    /** A new route, from the anchor on, walked from its start. */
    void setRoute(double[] points, boolean more) {
        this.route = points.clone();
        this.more = more;
        this.cumulative = new double[points.length / 2];
        for (int p = 1; p < cumulative.length; p++) {
            cumulative[p] = cumulative[p - 1] + DetMath.length(routeX(p) - routeX(p - 1), routeZ(p) - routeZ(p - 1));
        }
        this.along = 0;
    }

    /** Walks {@code distance} blocks along the route, no farther than its end; whether it is there. */
    boolean advance(double distance) {
        along = Math.min(routeLength(), along + distance);
        return along >= routeLength();
    }

    /** Turns the facing toward the route's heading where the anchor is, by at most {@code limit} radians. */
    void turn(double limit) {
        if (!heading(along)) {
            return;
        }
        double tx = headingX;
        double tz = headingZ;
        double cross = facingX * tz - facingZ * tx;
        double dot = facingX * tx + facingZ * tz;
        double angle = DetMath.atan2(cross, dot);
        if (Math.abs(angle) <= limit) {
            facingX = tx;
            facingZ = tz;
            return;
        }
        double turn = angle < 0 ? -limit : limit;
        double c = DetMath.cos(turn);
        double s = DetMath.sin(turn);
        double nx = facingX * c - facingZ * s;
        double nz = facingX * s + facingZ * c;
        double length = DetMath.length(nx, nz);
        facingX = nx / length;
        facingZ = nz / length;
    }

    /** Whether the facing is the route's heading where the anchor is (or the route has none). */
    boolean aligned() {
        return !heading(along) || (facingX == headingX && facingZ == headingZ);
    }

    /** The anchor and the look-ahead point, from {@link #along}. */
    void refresh() {
        point(along);
        ax = pointX;
        az = pointZ;
        point(advancing ? along + LOOK : along);
        lookX = pointX;
        lookZ = pointZ;
    }

    private void point(double distance) {
        int n = cumulative.length;
        if (n == 1) {
            pointX = routeX(0);
            pointZ = routeZ(0);
            return;
        }
        double d = Math.max(0, Math.min(distance, routeLength()));
        int p = 1;
        while (p < n - 1 && cumulative[p] < d) {
            p++;
        }
        double span = cumulative[p] - cumulative[p - 1];
        double t = span == 0 ? 1 : (d - cumulative[p - 1]) / span;
        pointX = routeX(p - 1) + (routeX(p) - routeX(p - 1)) * t;
        pointZ = routeZ(p - 1) + (routeZ(p) - routeZ(p - 1)) * t;
    }

    /**
     * The unit heading of the route's segment that holds distance {@code d} (the last one with length, at or past its
     * end) into {@link #headingX} and {@link #headingZ}; false when the route has no segment of length.
     */
    private boolean heading(double d) {
        int found = -1;
        for (int p = 1; p < cumulative.length; p++) {
            if (cumulative[p] > cumulative[p - 1]) {
                found = p;
                if (cumulative[p] > d) {
                    break;
                }
            }
        }
        if (found < 0) {
            return false;
        }
        double dx = routeX(found) - routeX(found - 1);
        double dz = routeZ(found) - routeZ(found - 1);
        double length = DetMath.length(dx, dz);
        headingX = dx / length;
        headingZ = dz / length;
        return true;
    }

    private double headingX;
    private double headingZ;
    private double pointX;
    private double pointZ;

    /** How far ahead of its place a member walks while the formation marches, in blocks: about five ticks of a walk. */
    static final double LOOK = 1.0;

    @Override
    public String toString() {
        return "formation " + id + " " + shape + " " + Arrays.toString(members);
    }
}
