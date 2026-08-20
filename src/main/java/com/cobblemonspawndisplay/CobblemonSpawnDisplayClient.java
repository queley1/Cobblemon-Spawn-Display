package com.cobblemonspawndisplay;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CobblemonSpawnDisplayClient implements ClientModInitializer {
	public static final String MOD_ID = "cobblemon_spawn_display";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		RarityIndex.load();
		SpawnDisplayConfig.load();
		SpawnHud.initialize();
		SpawnDisplayKeybind.initialize();
	}
}
