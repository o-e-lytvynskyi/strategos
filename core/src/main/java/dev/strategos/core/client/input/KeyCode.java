package dev.strategos.core.client.input;

/**
 * A key or a mouse button, in our own codes: keys carry GLFW's key codes, mouse buttons GLFW's button numbers. The
 * adapter converts once, here, so a later input change (1.21.9's input objects, 26.3's SDL scancodes) lands in one
 * place.
 */
public record KeyCode(Kind kind, int code) {
    public enum Kind {
        KEY,
        MOUSE
    }

    public static final int SPACE = 32;
    public static final int COMMA = 44;
    public static final int MINUS = 45;
    public static final int PERIOD = 46;
    public static final int EQUAL = 61;
    public static final int A = 65;
    public static final int D = 68;
    public static final int H = 72;
    public static final int S = 83;
    public static final int V = 86;
    public static final int W = 87;
    public static final int Z = 90;
    public static final int ESCAPE = 256;
    public static final int F1 = 290;
    public static final int F2 = 291;
    public static final int F3 = 292;
    public static final int F6 = 295;
    public static final int F8 = 297;
    public static final int F11 = 300;
    public static final int MOUSE_LEFT = 0;
    public static final int MOUSE_RIGHT = 1;
    public static final int MOUSE_MIDDLE = 2;

    public static KeyCode key(int glfw) {
        return new KeyCode(Kind.KEY, glfw);
    }

    public static KeyCode mouse(int button) {
        return new KeyCode(Kind.MOUSE, button);
    }

    /** A digit key 0–9, whose GLFW code is its character. */
    public static KeyCode digit(int n) {
        if (n < 0 || n > 9) {
            throw new IllegalArgumentException("no digit " + n);
        }
        return key('0' + n);
    }

    /** The digit 0–9 this key is, or -1. */
    public int digit() {
        return kind == Kind.KEY && code >= '0' && code <= '9' ? code - '0' : -1;
    }
}
