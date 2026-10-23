package dev.strategos.mod.server.body;

import dev.strategos.core.sim.UnitGear;
import dev.strategos.core.sim.econ.EconomyRules;
import dev.strategos.core.sim.econ.ResourceDef;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * What a body holds for the core's gear (ECO-05, M3 spec §2.4): iron tools, read at a distance by their heads, the
 * mace for building (§1.6 row 9), and the load as the item it comes from: a log, sweet berries, wheat, cobblestone or
 * raw iron, as the style guide's roles say (docs/art/style-guide.md §3).
 */
public final class GearItems {
    private GearItems() {}

    public static ItemStack tool(int tool) {
        return switch (tool) {
            case UnitGear.AXE -> new ItemStack(Items.IRON_AXE);
            case UnitGear.PICKAXE -> new ItemStack(Items.IRON_PICKAXE);
            case UnitGear.HOE -> new ItemStack(Items.IRON_HOE);
            case UnitGear.BUILDING -> new ItemStack(Items.MACE);
            case UnitGear.TORCH -> new ItemStack(Items.TORCH);
            default -> ItemStack.EMPTY;
        };
    }

    /** The load of {@code economy}'s resource {@code resource} and its subtype {@code subtype}; none for -1. */
    public static ItemStack load(EconomyRules economy, int resource, int subtype) {
        if (resource < 0 || resource >= economy.size()) {
            return ItemStack.EMPTY;
        }
        ResourceDef def = economy.resources().get(resource);
        String sub =
                subtype >= 0 && subtype < def.subtypes().size() ? def.subtypes().get(subtype) : "";
        return switch (def.id()) {
            case "wood" -> new ItemStack(Items.OAK_LOG);
            case "food" -> new ItemStack(sub.equals("grain") ? Items.WHEAT : Items.SWEET_BERRIES);
            case "stone" -> new ItemStack(Items.COBBLESTONE);
            case "iron" -> new ItemStack(Items.RAW_IRON);
            default -> ItemStack.EMPTY;
        };
    }
}
