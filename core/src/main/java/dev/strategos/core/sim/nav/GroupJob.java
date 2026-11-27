package dev.strategos.core.sim.nav;

import dev.strategos.core.port.JobScratch;
import dev.strategos.core.port.PureJob;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A group's request (PATH-09): one search from the surface nearest the group's centroid to its goal, as a {@link
 * PathJob}, then each member's path from it ({@link GroupPaths}), joins of at most {@code joinCap} expansions and at
 * most {@code maxJoins} of them included. Pure, like a unit's search: the grid is immutable, and the members are as
 * admission found them.
 */
public record GroupJob(
        SurfaceGrid grid,
        PathRequest request,
        List<GroupPaths.Member> members,
        int joinCap,
        int maxJoins,
        GateLinks.@Nullable Pass gates)
        implements PureJob<PathResult> {
    public GroupJob(
            SurfaceGrid grid,
            PathRequest request,
            List<GroupPaths.Member> members,
            int joinCap,
            int maxJoins,
            GateLinks.@Nullable Pass gates) {
        this.grid = grid;
        this.request = request;
        this.members = List.copyOf(members);
        this.joinCap = joinCap;
        this.maxJoins = maxJoins;
        this.gates = gates;
    }

    /** A group's job where no gate is crossed. */
    public GroupJob(SurfaceGrid grid, PathRequest request, List<GroupPaths.Member> members, int joinCap, int maxJoins) {
        this(grid, request, members, joinCap, maxJoins, null);
    }

    @Override
    public PathResult run(JobScratch scratch) {
        PathResult group = new PathJob(grid, request, gates).run(scratch);
        return GroupPaths.members(
                grid, request, group, members, joinCap, maxJoins, scratch.get(GridAStar.class, GridAStar::new), gates);
    }
}
