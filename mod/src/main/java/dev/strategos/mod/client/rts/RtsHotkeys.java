package dev.strategos.mod.client.rts;

import dev.strategos.core.client.hud.ClientFactionView;
import dev.strategos.core.client.hud.IdleWorkers;
import dev.strategos.core.client.order.HotkeyOrders;
import dev.strategos.core.client.select.CameraBookmarks;
import dev.strategos.core.client.select.DeleteConfirm;
import dev.strategos.core.client.select.UnitRole;
import dev.strategos.core.protocol.BuildingState;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.MatchJoin;
import dev.strategos.core.protocol.UnitSummary;
import dev.strategos.core.shared.Handle;
import dev.strategos.mod.client.StrategosClient;
import dev.strategos.mod.entity.UnitEntity;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * The hotkeys that act on the selection or the camera (UI-17): the idle keys, select-all, camera bookmarks, the all
 * clear, garrison and delete. The rules are the core's ({@code HotkeyOrders}, {@code UnitRole}, {@code IdleWorkers});
 * this only reads the client's state and sends what they choose.
 */
final class RtsHotkeys {
    private final RtsMode mode;
    private final IdleWorkers idleWorkers = new IdleWorkers(UnitRole.WORKER);
    private final IdleWorkers idleSoldiers = new IdleWorkers(UnitRole.SOLDIER);
    private final CameraBookmarks bookmarks = new CameraBookmarks();
    private final DeleteConfirm deleteConfirm = new DeleteConfirm();
    /** The idle unit the key took whose body has not arrived yet, its index; -1 for none, and ticks left to wait. */
    private int pendingIdle = -1;

    private int pendingIdleTicks;
    private long now;

    RtsHotkeys(RtsMode mode) {
        this.mode = mode;
    }

    /** One client tick in RTS mode. */
    void tick() {
        now++;
        selectPendingIdle();
    }

    /**
     * The idle key (UI-10, UI-17): the next idle unit of {@code role}, in index order, is selected and the camera goes
     * to it; a far one is selected when its body arrives after the jump.
     */
    void nextIdle(UnitRole role) {
        MatchJoin join = StrategosClient.match();
        if (join == null || mode.rig() == null) {
            return;
        }
        IdleWorkers key = role == UnitRole.WORKER ? idleWorkers : idleSoldiers;
        UnitSummary.Entry e =
                key.next(StrategosClient.model().units(), join.slot().value(), StrategosClient.templates());
        if (e == null) {
            return;
        }
        mode.centreOn(join.originX() + e.qx() / 8.0, join.originZ() + e.qz() / 8.0);
        pendingIdle = e.index();
        pendingIdleTicks = 100;
        selectPendingIdle();
    }

    private void selectPendingIdle() {
        Minecraft mc = Minecraft.getInstance();
        if (pendingIdle < 0 || mc.level == null) {
            return;
        }
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof UnitEntity body
                    && body.spawnData() != null
                    && Handle.index(body.spawnData().handle()) == pendingIdle) {
                mode.selection().set(List.of(body.spawnData().handle()));
                pendingIdle = -1;
                return;
            }
        }
        if (--pendingIdleTicks <= 0) {
            pendingIdle = -1;
        }
    }

    /** Every own unit of {@code role} selected, on screen or not. */
    void selectAll(UnitRole role) {
        MatchJoin join = StrategosClient.match();
        if (join == null) {
            return;
        }
        List<Integer> all = role.select(mode.seen(), join.slot().value(), StrategosClient.templates());
        if (!all.isEmpty()) {
            mode.selection().set(all);
        }
    }

    /** Bookmark {@code slot} keeps where the camera looks. */
    void setBookmark(int slot) {
        if (mode.rig() != null) {
            bookmarks.set(slot, mode.rig().focusX(), mode.rig().focusZ());
        }
    }

    /** The camera to bookmark {@code slot}, if it was set. */
    void jumpToBookmark(int slot) {
        double[] place = bookmarks.get(slot);
        if (place != null) {
            mode.centreOn(place[0], place[1]);
        }
    }

    /** Forgets the bookmarks: a new match starts with none. */
    void clearBookmarks() {
        bookmarks.clear();
    }

    /** The all clear at the selected holder, else at every own holder. */
    void backToWork() {
        for (Command c : HotkeyOrders.backToWork(
                StrategosClient.templates(),
                selectedBuilding(),
                StrategosClient.model().buildings())) {
            mode.send(c);
        }
    }

    /** The selected units into the nearest own building that takes them. */
    void garrison() {
        if (mode.selection().handles().isEmpty()) {
            return;
        }
        Command c = HotkeyOrders.garrison(
                StrategosClient.templates(),
                new ClientFactionView(
                        StrategosClient.templates(),
                        StrategosClient.model().faction(),
                        StrategosClient.model().buildings(),
                        StrategosClient.viewerCiv()),
                mode.selection().handles(),
                StrategosClient.model()::unit,
                StrategosClient.model().buildings());
        if (c != null) {
            mode.send(c);
        }
    }

    /** Delete: the first press asks on the action bar, a second within three seconds sends. */
    void delete() {
        List<Integer> units = mode.selection().handles();
        BuildingState.Entry building = selectedBuilding();
        if (units.isEmpty() && building == null) {
            return;
        }
        if (!deleteConfirm.press(units, building == null ? 0 : building.handle(), now)) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance()
                        .player
                        .displayClientMessage(
                                Component.translatable("strategos.hotkeys.delete_confirm", units.size()), true);
            }
            return;
        }
        for (Command c : HotkeyOrders.delete(units, building)) {
            mode.send(c);
        }
    }

    private BuildingState.@Nullable Entry selectedBuilding() {
        Integer handle = mode.selection().building();
        if (handle == null) {
            return null;
        }
        for (BuildingState.Entry b : StrategosClient.model().buildings()) {
            if (b.handle() == handle) {
                return b;
            }
        }
        return null;
    }
}
