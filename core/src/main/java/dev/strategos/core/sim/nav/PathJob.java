package dev.strategos.core.sim.nav;

import dev.strategos.core.port.JobScratch;
import dev.strategos.core.port.PureJob;

/**
 * One request's search (04 §9.3): A* over the grid as it was when the request was made, then string pulling. Pure:
 * the grid is immutable (copy-on-write), and the search's scratch belongs to the thread that runs it.
 */
public record PathJob(SurfaceGrid grid, PathRequest request) implements PureJob<PathResult> {
    @Override
    public PathResult run(JobScratch scratch) {
        GridAStar search = scratch.get(GridAStar.class, GridAStar::new);
        int goalX = PathService.clampColumn(request.goalX(), grid.sizeX());
        int goalZ = PathService.clampColumn(request.goalZ(), grid.sizeZ());
        GridAStar.Result found = search.search(grid, request.fromNode(), goalX, goalZ, request.cap());
        PathStore.Waypoints waypoints = StringPuller.pull(
                grid, found.nodes(), found.status() == PathStatus.OK, request.goalX(), request.goalZ());
        return new PathResult(request.id(), found.status(), waypoints, found.expansions());
    }
}
