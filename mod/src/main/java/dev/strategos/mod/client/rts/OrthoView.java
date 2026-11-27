package dev.strategos.mod.client.rts;

import com.mojang.blaze3d.platform.InputConstants;
import dev.strategos.core.client.camera.CameraRig;
import dev.strategos.core.client.pick.Orthographic;
import dev.strategos.mod.client.StrategosClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The optional orthographic RTS view (CAM-06, D-09, 04 §13.1): the option, the switch key (Ctrl + O in RTS mode), and
 * what the two client mixins ask. It is on only while the option is on, the RTS camera is active and no fault was seen;
 * with Sodium or Iris loaded the option is ignored unless {@code orthographicWithRenderMods} says to try. Any
 * exception while the mixins build the matrix or bypass the frustum turns the view off for the session and logs once,
 * so the next frame is perspective. Client thread only.
 */
public final class OrthoView {
    private static final Logger LOG = LoggerFactory.getLogger(OrthoView.class);
    private static final int KEY_O = 79; // GLFW_KEY_O

    private static final OrthoView INSTANCE = new OrthoView();

    private @Nullable RtsMode rts;
    private volatile boolean wanted;
    private volatile boolean failed;
    private boolean keyWasDown;

    private OrthoView() {}

    /** Listens for the switch key; {@code mode} owns the camera rig the view is sized from. */
    static void register(RtsMode mode) {
        INSTANCE.rts = mode;
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> INSTANCE.tick());
    }

    /** Whether Sodium or Iris is loaded, the mods whose own culling and shadow passes the view is untested under. */
    public static boolean renderModLoaded() {
        ModList mods = ModList.get();
        return mods != null && (mods.isLoaded("sodium") || mods.isLoaded("iris"));
    }

    /** Whether the option allows the view at all: on, and not blocked by a render mod or an earlier fault. */
    static boolean allowed(boolean option, boolean tryWithRenderMods, boolean renderMod, boolean fault) {
        return option && !fault && (!renderMod || tryWithRenderMods);
    }

    /** Whether the view is drawn now: allowed and in RTS mode. */
    public static boolean active() {
        return INSTANCE.isActive();
    }

    private boolean isActive() {
        RtsMode mode = rts;
        return wanted && mode != null && mode.active();
    }

    /** The orthographic projection for this frame, or null for the perspective one. */
    public static @Nullable Matrix4f projection(float depthFar) {
        return INSTANCE.matrix(depthFar);
    }

    private @Nullable Matrix4f matrix(float depthFar) {
        if (!isActive()) {
            return null;
        }
        try {
            CameraRig rig = rts == null ? null : rts.rig();
            if (rig == null) {
                return null;
            }
            double aspect = (double) Minecraft.getInstance().getWindow().getWidth()
                    / Minecraft.getInstance().getWindow().getHeight();
            double halfHeight = Orthographic.halfHeight(rig.height(), rig.pose().pitch(), CameraRig.FOV);
            double halfWidth = halfHeight * aspect;
            if (!Double.isFinite(halfWidth) || halfWidth <= 0 || halfHeight <= 0) {
                throw new IllegalStateException("no usable orthographic size: " + halfWidth + " x " + halfHeight);
            }
            return new Matrix4f()
                    .setOrtho(
                            (float) -halfWidth,
                            (float) halfWidth,
                            (float) -halfHeight,
                            (float) halfHeight,
                            (float) Orthographic.NEAR,
                            depthFar);
        } catch (RuntimeException | LinkageError e) {
            failWith(e);
            return null;
        }
    }

    /** Turns the view off for the session after a fault, logging it once. */
    public static void fault(Throwable cause) {
        INSTANCE.failWith(cause);
    }

    private void failWith(Throwable cause) {
        if (!failed) {
            failed = true;
            wanted = false;
            LOG.error("The orthographic view failed; back to perspective for this session", cause);
        }
    }

    /** The option's value, flipped and saved; the view follows on the next tick. */
    static void toggle() {
        INSTANCE.flip();
    }

    private void flip() {
        StrategosClientConfig options = StrategosClientConfig.INSTANCE;
        options.orthographic.set(!options.orthographic.get());
        options.orthographic.save();
        refresh();
    }

    private void refresh() {
        StrategosClientConfig options = StrategosClientConfig.INSTANCE;
        boolean renderMod = renderModLoaded();
        wanted = allowed(options.orthographic.get(), options.orthographicWithRenderMods.get(), renderMod, failed);
    }

    private void tick() {
        try {
            refresh();
        } catch (IllegalStateException notLoaded) {
            wanted = false; // the config is not loaded yet
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean down = Screen.hasControlDown()
                && InputConstants.isKeyDown(mc.getWindow().getWindow(), KEY_O);
        if (down && !keyWasDown && mc.screen instanceof RtsScreen) {
            toggle();
        }
        keyWasDown = down;
    }
}
