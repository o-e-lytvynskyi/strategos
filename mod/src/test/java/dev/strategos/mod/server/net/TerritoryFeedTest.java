package dev.strategos.mod.server.net;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.client.territory.ClientTerritory;
import dev.strategos.core.protocol.TerritoryUpdate;
import dev.strategos.core.shared.TerritoryGrid;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The territory feed's queueing (M9 spec §2.4): no Minecraft. */
class TerritoryFeedTest {
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);

    /** A noisy grid, so its full runs do not fit one part. */
    private static TerritoryGrid noisy() {
        TerritoryGrid g = new TerritoryGrid(400, 400);
        for (int i = 0; i < g.size(); i++) {
            g.set(i, (byte) (i % 2 == 0 ? 1 : 0x41));
        }
        return g;
    }

    @Test
    void aNewPlayerGetsAFullVersionOnePartPerPollThenNothing() {
        TerritoryFeed feed = new TerritoryFeed();
        TerritoryGrid grid = noisy();
        feed.offer(A, 7, 100, 1, grid);
        ClientTerritory client = new ClientTerritory();
        int parts = 0;
        boolean done = false;
        for (TerritoryUpdate u = feed.poll(A); u != null; u = feed.poll(A)) {
            assertThat(u.full()).isTrue();
            assertThat(u.wireSize()).isLessThanOrEqualTo(TerritoryUpdate.CAP);
            parts++;
            done = client.accept(u);
        }
        assertThat(parts).isGreaterThan(1);
        assertThat(done).isTrue();
        assertThat(client.grid()).isEqualTo(grid);
        feed.offer(A, 7, 101, 1, grid);
        assertThat(feed.poll(A)).isNull();
    }

    @Test
    void aMovedVersionSendsOnlyTheChangedTiles() {
        TerritoryFeed feed = new TerritoryFeed();
        TerritoryGrid grid = new TerritoryGrid(40, 30);
        feed.offer(A, 7, 1, 1, grid);
        while (feed.poll(A) != null) {
            // drain the full version
        }
        grid.set(10, (byte) 2);
        feed.offer(A, 7, 2, 2, grid);
        TerritoryUpdate delta = feed.poll(A);
        assertThat(delta).isNotNull();
        assertThat(delta.full()).isFalse();
        assertThat(delta.entries()).isEqualTo(1);
        assertThat(delta.key(0)).isEqualTo(10);
        assertThat(delta.last()).isTrue();
        assertThat(feed.poll(A)).isNull();
    }

    @Test
    void aVersionThatMovesWhilePartsAreQueuedWaitsForTheQueue() {
        TerritoryFeed feed = new TerritoryFeed();
        TerritoryGrid grid = noisy();
        feed.offer(A, 7, 1, 1, grid);
        grid.set(0, (byte) 5);
        feed.offer(A, 7, 2, 2, grid);
        int full = 0;
        while (feed.poll(A) != null) {
            full++;
        }
        assertThat(full).isGreaterThan(1);
        feed.offer(A, 7, 3, 2, grid);
        TerritoryUpdate delta = feed.poll(A);
        assertThat(delta).isNotNull();
        assertThat(delta.full()).isFalse();
    }

    @Test
    void aForgottenOrMovedPlayerStartsAgainFull() {
        TerritoryFeed feed = new TerritoryFeed();
        TerritoryGrid grid = new TerritoryGrid(40, 30);
        feed.offer(A, 7, 1, 1, grid);
        feed.offer(B, 7, 1, 1, grid);
        assertThat(feed.players()).isEqualTo(2);
        feed.retain(A::equals);
        assertThat(feed.players()).isEqualTo(1);
        assertThat(feed.poll(B)).isNull();
        feed.forget(A);
        feed.offer(A, 7, 5, 1, grid);
        assertThat(feed.poll(A).full()).isTrue();
        while (feed.poll(A) != null) {
            // drain
        }
        feed.offer(A, 8, 6, 1, grid); // another match
        assertThat(feed.poll(A).full()).isTrue();
    }
}
