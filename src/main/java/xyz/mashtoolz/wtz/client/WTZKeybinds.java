package xyz.mashtoolz.wtz.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import xyz.mashtoolz.wtz.config.WTZConfig;
import xyz.mashtoolz.wtz.features.tts.ShoutTTS;
import xyz.mashtoolz.wtz.features.shoppinglist.ShoppingListRenderer;
import xyz.mashtoolz.wtz.features.mount.helper.MountHelper;
import xyz.mashtoolz.wtz.features.mount.MountUtils;
import xyz.mashtoolz.wtz.features.overlay.OverlayEditScreen;

import java.util.ArrayList;
import java.util.List;

public class WTZKeybinds {

    public static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(Identifier.of("wtz", "wtz"));

    public static final KeyBinding CONFIG = register("key.wtz.config");
    public static final KeyBinding MOUNT_HELPER = register("key.wtz.mount_helper");
    public static final KeyBinding COPY_MOUNT_STATS = register("key.wtz.copy_mount_stats");
    public static final KeyBinding EDIT_OVERLAYS = register("key.wtz.edit_overlays");
    public static final KeyBinding ADD_TO_SHOPPING_LIST = register("key.wtz.add_to_shopping_list");
    public static final KeyBinding TOGGLE_SHOPPING_LIST = register("key.wtz.toggle_shopping_list");
    public static final KeyBinding STOP_TTS = register("key.wtz.stop_tts");

    private static final List<KeyAction> keyActions = new ArrayList<>();

    public static void register() {
        onPress(CONFIG, () -> WTZClient.client().setScreen(
                WTZConfig.buildScreen(WTZClient.client().currentScreen)
        ));
        onPress(MOUNT_HELPER, MountHelper::toggle);
        onPress(COPY_MOUNT_STATS, MountUtils::copyHoveredStats);
        onPress(EDIT_OVERLAYS, OverlayEditScreen::toggle);
        onPress(TOGGLE_SHOPPING_LIST, () -> ShoppingListRenderer.getInstance().toggleVisibility());
        onPress(STOP_TTS, ShoutTTS::stopPlayback);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            for (KeyAction action : keyActions) action.tick();
        });
    }

    private static KeyBinding register(String key) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(
                key, InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY
        ));
    }

    private static void onPress(KeyBinding key, Runnable action) {
        keyActions.add(new KeyAction(key, action));
    }

    private static class KeyAction {
        private final KeyBinding key;
        private final Runnable action;
        private boolean wasPressed = false;

        KeyAction(KeyBinding key, Runnable action) {
            this.key = key;
            this.action = action;
        }

        void tick() {
            boolean isDown = key.isPressed();
            if (isDown && !wasPressed) action.run();
            wasPressed = isDown;
        }
    }
}
