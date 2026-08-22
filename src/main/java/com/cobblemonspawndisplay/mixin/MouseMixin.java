package com.cobblemonspawndisplay.mixin;

import com.cobblemonspawndisplay.SpawnHud;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class MouseMixin {
	@Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
	private void cobblemonSpawnDisplay$pinEntry(
			long windowHandle,
			int button,
			int action,
			int modifiers,
			CallbackInfo callbackInfo
	) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT
				|| action != GLFW.GLFW_PRESS
				|| client.currentScreen == null) {
			return;
		}

		Window window = client.getWindow();
		if (windowHandle != window.getHandle() || window.getWidth() == 0 || window.getHeight() == 0) {
			return;
		}

		double scaledMouseX = client.mouse.getX() * window.getScaledWidth() / window.getWidth();
		double scaledMouseY = client.mouse.getY() * window.getScaledHeight() / window.getHeight();
		if (SpawnHud.handleClick(scaledMouseX, scaledMouseY)) {
			callbackInfo.cancel();
		}
	}
}
