package dev.strategos.core.sim;

import java.util.Arrays;

/** Each unit's template (by net index) and faction, by handle index. */
public final class IdentityStore {
    private int[] templateIndex = new int[0];
    private int[] faction = new int[0];

    public void ensureCapacity(int size) {
        if (size > templateIndex.length) {
            int capacity = Math.max(size, templateIndex.length * 2);
            templateIndex = Arrays.copyOf(templateIndex, capacity);
            faction = Arrays.copyOf(faction, capacity);
        }
    }

    public int templateIndex(int i) {
        return templateIndex[i];
    }

    public int faction(int i) {
        return faction[i];
    }

    public void set(int i, int templateIndex, int faction) {
        this.templateIndex[i] = templateIndex;
        this.faction[i] = faction;
    }
}
