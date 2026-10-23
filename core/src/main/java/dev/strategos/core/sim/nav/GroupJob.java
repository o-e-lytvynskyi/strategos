package dev.strategos.core.sim.nav;

import dev.strategos.core.port.JobScratch;
import dev.strategos.core.port.PureJob;
import java.util.List;

/**
 * A group's request (PATH-09): one search from the surface nearest the group's centroid to its goal, as a {@link
 * PathJob}, then each member's path from it ({@link GroupPaths}), joins of at most {@code joinCap} expansions and at
 * most {@code maxJoins} of them included. Pure, like a unit's search: the grid is immutable, and the members are as
 * admission found them.
 */
public record GroupJob(
        SurfaceGrid grid, PathRequest request, List<GroupPaths.Member> members, int joinCap, int maxJoins)
        implements PureJob<PathResult> {
    public GroupJob {
        members = List.copyOf(members);
    }

    @Override
    public PathResult run(JobScratch scratch) {
        PathResult group = new PathJob(grid, request).run(scratch);
        return GroupPaths.members(
                grid, request, group, members, joinCap, maxJoins, scratch.get(GridAStar.class, GridAStar::new));
    }
}
