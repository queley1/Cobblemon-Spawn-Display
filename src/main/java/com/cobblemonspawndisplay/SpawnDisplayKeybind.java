package com.cobblemonspawndisplay;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class SpawnDisplayKeybind {
	private static final KeyBinding OPEN_SETTINGS = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.cobblemon_spawn_display.open_settings",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_UNKNOWN,
			"key.categories.cobblemon_spawn_display"
	));

	private SpawnDisplayKeybind() {
	}

	public static void initialize() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (OPEN_SETTINGS.wasPressed()) {
				if (!(client.currentScreen instanceof SpawnDisplayConfigScreen)) {
					client.setScreen(new SpawnDisplayConfigScreen(client.currentScreen));
				}
			}
		});
	}
}
