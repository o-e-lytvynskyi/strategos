package dev.strategos.mod.client.rts;

import com.mojang.blaze3d.platform.InputConstants;
import dev.strategos.core.client.input.Action;
import dev.strategos.core.client.input.ActionContext;
import dev.strategos.core.client.input.ActionMap;
import dev.strategos.core.client.input.Binding;
import dev.strategos.core.client.input.DefaultBindings;
import dev.strategos.core.client.input.KeyCode;
import dev.strategos.core.client.input.Modifiers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;

/**
 * The rebindable default bindings as vanilla key mappings (UI-01, 04 §13.3), so players rebind them in the Controls
 * screen: the RTS ones under a context live only while the RTS screen is open, which conflicts with no vanilla
 * context but {@code UNIVERSAL}'s, and the RTS toggle in game. The RTS screen reads their current keys into an action
 * map, so a rebinding takes effect when the screen next opens.
 */
public final class RtsKeys {
    /** Live only while the RTS screen owns input; conflicts with itself only. */
    private enum RtsContext implements IKeyConflictContext {
        RTS;

        @Override
        public boolean isActive() {
            return Minecraft.getInstance().screen instanceof RtsScreen;
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return other == RTS;
        }
    }

    private static final String CATEGORY = "key.categories.strategos";

    private final List<Binding> bindings = new ArrayList<>();
    private final List<KeyMapping> mappings = new ArrayList<>();

    RtsKeys() {
        for (Binding b : DefaultBindings.all()) {
            if (!b.rebindable()) {
                continue;
            }
            IKeyConflictContext context =
                    b.context() == ActionContext.IN_GAME ? KeyConflictContext.IN_GAME : RtsContext.RTS;
            InputConstants.Type type =
                    b.key().kind() == KeyCode.Kind.MOUSE ? InputConstants.Type.MOUSE : InputConstants.Type.KEYSYM;
            bindings.add(b);
            mappings.add(new KeyMapping(
                    b.mappingName(),
                    context,
                    modifier(b.modifiers()),
                    type,
                    b.key().code(),
                    CATEGORY));
        }
    }

    void register(RegisterKeyMappingsEvent event) {
        mappings.forEach(event::register);
    }

    /** NeoForge's modifier for a binding's (UI-08's card cells take Alt); none for a plain key. */
    private static KeyModifier modifier(int mods) {
        if ((mods & Modifiers.ALT) != 0) {
            return KeyModifier.ALT;
        }
        if ((mods & Modifiers.CTRL) != 0) {
            return KeyModifier.CONTROL;
        }
        return (mods & Modifiers.SHIFT) != 0 ? KeyModifier.SHIFT : KeyModifier.NONE;
    }

    private static int modifiers(KeyModifier mod) {
        return switch (mod) {
            case ALT -> Modifiers.ALT;
            case CONTROL -> Modifiers.CTRL;
            case SHIFT -> Modifiers.SHIFT;
            case NONE -> Modifiers.NONE;
        };
    }

    /** The mapping of a rebindable binding's action in its context. */
    KeyMapping mapping(Action action) {
        for (int k = 0; k < bindings.size(); k++) {
            if (bindings.get(k).action() == action) {
                return mappings.get(k);
            }
        }
        throw new IllegalArgumentException("no key mapping for " + action);
    }

    /** The defaults with every rebindable binding on its mapping's current key. */
    ActionMap current() {
        ActionMap map = ActionMap.defaults();
        for (int k = 0; k < bindings.size(); k++) {
            Binding b = bindings.get(k);
            InputConstants.Key key = mappings.get(k).getKey();
            KeyCode code = key.getType() == InputConstants.Type.MOUSE
                    ? KeyCode.mouse(key.getValue())
                    : KeyCode.key(key.getValue());
            map = map.rebind(
                    b.context(), b.action(), code, modifiers(mappings.get(k).getKeyModifier()));
        }
        return map;
    }
}
