package dev.strategos.core.client.input;

/** Modifier bits, GLFW's: Shift 1, Ctrl 2, Alt 4, Super 8. */
public final class Modifiers {
    public static final int NONE = 0;
    public static final int SHIFT = 1;
    public static final int CTRL = 2;
    public static final int ALT = 4;
    public static final int SUPER = 8;

    private Modifiers() {}

    public static boolean shift(int mods) {
        return (mods & SHIFT) != 0;
    }

    public static boolean ctrl(int mods) {
        return (mods & CTRL) != 0;
    }

    public static boolean alt(int mods) {
        return (mods & ALT) != 0;
    }
}
