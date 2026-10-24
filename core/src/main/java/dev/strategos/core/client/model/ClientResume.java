package dev.strategos.core.client.model;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * What the client keeps of each match it left (MATCH-04, ADR-0018): the camera's pose and the control groups, by match
 * id, so a resume, a rejoin or the first-person round trip finds them as they were; the server keeps neither. For the
 * client's session only.
 */
public final class ClientResume {
    /** The camera over the match: its focus, its yaw and its height. */
    public record Pose(double focusX, double focusZ, double yaw, double height) {}

    /** One match's: the pose and the groups 0 to 9. */
    public record Saved(Pose pose, List<List<Integer>> groups) {
        public Saved {
            groups = groups.stream().map(List::copyOf).toList();
        }
    }

    private final Map<Integer, Saved> byMatch = new TreeMap<>();

    /** Match {@code matchId} left with {@code pose} and {@code groups}. */
    public void save(int matchId, Pose pose, List<List<Integer>> groups) {
        byMatch.put(matchId, new Saved(pose, groups));
    }

    /** What match {@code matchId} was left with, or null for one never left. */
    public @Nullable Saved of(int matchId) {
        return byMatch.get(matchId);
    }
}
