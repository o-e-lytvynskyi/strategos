package dev.strategos.mod.client.menu;

import dev.strategos.mod.server.net.LobbyReply;
import dev.strategos.mod.server.net.LobbyState;
import org.jspecify.annotations.Nullable;

/** Client-only request/view state; late frames cannot reopen a lobby after leaving or disconnecting. */
public final class LobbyClientSession {
    private @Nullable LobbyScreenModel model;
    private @Nullable LobbyReply error;
    private boolean waiting;
    private boolean ignoreFrames = true;

    public @Nullable LobbyScreenModel model() {
        return model;
    }

    public @Nullable LobbyReply error() {
        return error;
    }

    public boolean waiting() {
        return waiting;
    }

    public void beginRequest() {
        ignoreFrames = false;
        waiting = true;
        error = null;
    }

    public boolean accept(LobbyState state) {
        if (ignoreFrames) return false;
        model = new LobbyScreenModel(state);
        waiting = false;
        error = null;
        return true;
    }

    public void refuse(LobbyReply reply) {
        if (ignoreFrames) return;
        error = reply;
        waiting = false;
    }

    public void clear() {
        model = null;
        error = null;
        waiting = false;
        ignoreFrames = true;
    }
}
