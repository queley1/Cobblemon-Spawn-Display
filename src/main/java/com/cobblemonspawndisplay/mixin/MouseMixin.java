package com.cobblemonspawndisplay.mixin;

import com.cobblemonspawndisplay.SpawnHud;
import net.minecraft.client.Mouse;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

@Mixin(value = Mouse.class, priority = 100)
public abstract class MouseMixin {
	@Unique
	private static final ThreadLocal<Boolean> cobblemonSpawnDisplay$uiElementAtClick =
			ThreadLocal.withInitial(() -> false);

	@Inject(
			method = "method_1611([ZLnet/minecraft/client/gui/screen/Screen;DDI)V",
			at = @At("HEAD")
	)
	private static void cobblemonSpawnDisplay$captureCompetingUi(
			boolean[] handled,
			Screen screen,
			double mouseX,
			double mouseY,
			int button,
			CallbackInfo callbackInfo
	) {
		cobblemonSpawnDisplay$uiElementAtClick.set(hasUiElementAt(screen, mouseX, mouseY));
	}

	@Inject(
			method = "method_1611([ZLnet/minecraft/client/gui/screen/Screen;DDI)V",
			at = @At("RETURN")
	)
	private static void cobblemonSpawnDisplay$pinUnhandledEntry(
			boolean[] handled,
			Screen screen,
			double mouseX,
			double mouseY,
			int button,
			CallbackInfo callbackInfo
	) {
		boolean competingUiElement = cobblemonSpawnDisplay$uiElementAtClick.get();
		cobblemonSpawnDisplay$uiElementAtClick.remove();
		if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || handled[0] || competingUiElement) {
			return;
		}

		handled[0] = SpawnHud.handleClick(mouseX, mouseY);
	}

	@Unique
	private static boolean hasUiElementAt(Screen screen, double mouseX, double mouseY) {
		Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		if (containsUiElementAt(screen.children(), mouseX, mouseY, visited)) {
			return true;
		}

		for (Class<?> type = screen.getClass();
				type != null && Screen.class.isAssignableFrom(type);
				type = type.getSuperclass()) {
			for (Field field : type.getDeclaredFields()) {
				if (Modifier.isStatic(field.getModifiers())) {
					continue;
				}

				try {
					if (!field.canAccess(screen) && !field.trySetAccessible()) {
						continue;
					}
					if (containsUiElementAt(field.get(screen), mouseX, mouseY, visited)) {
						return true;
					}
				} catch (IllegalAccessException | RuntimeException ignored) {
					// A screen may contain inaccessible implementation fields; those are not UI candidates.
				}
			}
		}

		return false;
	}

	@Unique
	private static boolean containsUiElementAt(
			Object value,
			double mouseX,
			double mouseY,
			Set<Object> visited
	) {
		if (value == null || !visited.add(value)) {
			return false;
		}
		if (value instanceof Element element) {
			return element.isMouseOver(mouseX, mouseY);
		}
		if (value instanceof Iterable<?> elements) {
			for (Object element : elements) {
				if (containsUiElementAt(element, mouseX, mouseY, visited)) {
					return true;
				}
			}
			return false;
		}
		if (value instanceof Map<?, ?> elements) {
			return containsUiElementAt(elements.keySet(), mouseX, mouseY, visited)
					|| containsUiElementAt(elements.values(), mouseX, mouseY, visited);
		}
		if (value.getClass().isArray() && !value.getClass().getComponentType().isPrimitive()) {
			for (int index = 0; index < Array.getLength(value); index++) {
				if (containsUiElementAt(Array.get(value, index), mouseX, mouseY, visited)) {
					return true;
				}
			}
		}
		return false;
	}
}
