package net.minecraft.client;

/** A same-named stub for B5's fixture: Minecraft is not on the archTest classpath, and ArchUnit matches by name. */
public final class Minecraft {
    private Minecraft() {}

    public static Minecraft getInstance() {
        return new Minecraft();
    }
}
