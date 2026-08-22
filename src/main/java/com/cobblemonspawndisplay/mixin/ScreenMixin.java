package com.cobblemonspawndisplay.mixin;

import com.cobblemonspawndisplay.SpawnHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {
	@Inject(method = "renderWithTooltip", at = @At("TAIL"))
	private void cobblemonSpawnDisplay$renderHudTooltip(
			DrawContext context,
			int mouseX,
			int mouseY,
			float delta,
			CallbackInfo callbackInfo
	) {
		Screen screen = (Screen) (Object) this;
		for (Element element : screen.children()) {
			if (element.isMouseOver(mouseX, mouseY)) {
				return;
			}
		}

		SpawnHud.renderTooltip(context, mouseX, mouseY);
	}
}
