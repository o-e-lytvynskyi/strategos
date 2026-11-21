package dev.strategos.mod.client.rts;

import dev.strategos.core.client.model.ClientResume;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Session-only camera/groups cache, available only after an authenticated match confirmation. */
final class ClientResumeSession {
    record Scope(String realm, UUID account) {}

    private @Nullable Scope scope;
    private int confirmedMatch = -1;
    private ClientResume cache = new ClientResume();

    void joined(@Nullable Scope next, int matchId) {
        if (next == null || !next.equals(scope)) cache = new ClientResume();
        scope = next;
        confirmedMatch = matchId;
    }

    void disconnected() {
        confirmedMatch = -1;
    }

    void remember(int matchId, ClientResume.Pose pose, List<List<Integer>> groups) {
        if (matchId == confirmedMatch && confirmedMatch > 0) cache.save(matchId, pose, groups);
    }

    ClientResume.@Nullable Saved of(int matchId) {
        return matchId == confirmedMatch && confirmedMatch > 0 ? cache.of(matchId) : null;
    }
}
