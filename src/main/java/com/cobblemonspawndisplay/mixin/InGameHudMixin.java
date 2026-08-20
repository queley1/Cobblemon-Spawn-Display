package com.cobblemonspawndisplay.mixin;

import com.cobblemonspawndisplay.SpawnHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = InGameHud.class, priority = 2000)
public abstract class InGameHudMixin {
	@Inject(method = "render", at = @At("HEAD"))
	private void cobblemonSpawnDisplay$renderFirst(
			DrawContext context,
			RenderTickCounter tickCounter,
			CallbackInfo callbackInfo
	) {
		SpawnHud.render(context, tickCounter);
	}
}
