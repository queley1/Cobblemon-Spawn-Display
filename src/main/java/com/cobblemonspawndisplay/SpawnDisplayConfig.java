package com.cobblemonspawndisplay;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Persistent, client-side settings for the spawn HUD. */
public final class SpawnDisplayConfig {
	public static final int MIN_ROW_LENGTH = 1;
	public static final int MAX_ROW_LENGTH = 16;
	public static final int MIN_TILE_SIZE = 24;
	public static final int MAX_TILE_SIZE = 64;
	public static final int MIN_SPACING = 0;
	public static final int MAX_SPACING = 16;
	public static final int MIN_UPDATE_INTERVAL_TICKS = 1;
	public static final int MAX_UPDATE_INTERVAL_TICKS = 100;
	private static final int DEFAULT_BACKGROUND_OPACITY = 192;
	private static final int BACKGROUND_BRIGHTNESS_PERCENT = 65;
	private static final int DEFAULT_COMMON_COLOR = 0x55FF55;
	private static final int DEFAULT_UNCOMMON_COLOR = 0x5555FF;
	private static final int DEFAULT_RARE_COLOR = 0xAA00AA;
	private static final int LEGACY_DEFAULT_ULTRA_RARE_COLOR = 0xFF5555;
	private static final int DEFAULT_ULTRA_RARE_COLOR = 0xFF79C6;
	private static final int DEFAULT_SHINY_COLOR = 0x8BE9FD;
	private static final int DEFAULT_ALPHA_COLOR = 0xFF5555;
	private static final int DEFAULT_TERA_COLOR = 0xFFFFFF;
	private static final int DEFAULT_LEGENDARY_COLOR = 0xFFAA00;
	private static final int DEFAULT_MYTHICAL_COLOR = 0xFFFF55;
	private static final int DEFAULT_FOSSIL_COLOR = 0xB87333;
	private static final int DEFAULT_ULTRA_BEAST_BACKGROUND_COLOR = 0x5555FF;
	private static final int DEFAULT_ULTRA_BEAST_BORDER_COLOR = 0xFFFF55;
	private static final int LEGACY_DEFAULT_PARADOX_BACKGROUND_COLOR = 0xFF5555;
	private static final int LEGACY_DEFAULT_PARADOX_BORDER_COLOR = 0xBD93F9;
	private static final int DEFAULT_PARADOX_BACKGROUND_COLOR = 0xBD93F9;
	private static final int DEFAULT_PARADOX_BORDER_COLOR = 0xFF5555;
	private static final int DEFAULT_ROW_LENGTH = 8;
	private static final int DEFAULT_TILE_SIZE = 30;
	private static final int DEFAULT_SPACING = 2;
	private static final int DEFAULT_UPDATE_INTERVAL_TICKS = 5;
	private static final boolean DEFAULT_SHOW_COMMONS = true;
	private static final boolean DEFAULT_DISABLE_SPRITE_ANIMATIONS = false;
	private static final boolean DEFAULT_HORIZONTAL_DISTANCE = false;
	private static final int UNKNOWN_RARITY_COLOR = 0xAAAAAA;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static SpawnDisplayConfig instance = new SpawnDisplayConfig();

	private int backgroundOpacity = DEFAULT_BACKGROUND_OPACITY;
	private int commonColor = DEFAULT_COMMON_COLOR;
	private int uncommonColor = DEFAULT_UNCOMMON_COLOR;
	private int rareColor = DEFAULT_RARE_COLOR;
	private int ultraRareColor = DEFAULT_ULTRA_RARE_COLOR;
	private int shinyColor = DEFAULT_SHINY_COLOR;
	private int alphaColor = DEFAULT_ALPHA_COLOR;
	private int teraColor = DEFAULT_TERA_COLOR;
	private int legendaryColor = DEFAULT_LEGENDARY_COLOR;
	private int mythicalColor = DEFAULT_MYTHICAL_COLOR;
	private int fossilColor = DEFAULT_FOSSIL_COLOR;
	private int ultraBeastBackgroundColor = DEFAULT_ULTRA_BEAST_BACKGROUND_COLOR;
	private int ultraBeastBorderColor = DEFAULT_ULTRA_BEAST_BORDER_COLOR;
	private int paradoxBackgroundColor = DEFAULT_PARADOX_BACKGROUND_COLOR;
	private int paradoxBorderColor = DEFAULT_PARADOX_BORDER_COLOR;
	private int rowLength = DEFAULT_ROW_LENGTH;
	private int tileSize = DEFAULT_TILE_SIZE;
	private int spacing = DEFAULT_SPACING;
	private int updateIntervalTicks = DEFAULT_UPDATE_INTERVAL_TICKS;
	private boolean showCommons = DEFAULT_SHOW_COMMONS;
	private boolean disableSpriteAnimations = DEFAULT_DISABLE_SPRITE_ANIMATIONS;
	private boolean horizontalDistance = DEFAULT_HORIZONTAL_DISTANCE;
	private String highlightedPokemon = "";
	private Set<String> highlightedSpecies = Set.of();

	private SpawnDisplayConfig() {
	}

	public static SpawnDisplayConfig get() {
		return instance;
	}

	public static void load() {
		Path path = configPath();
		if (!Files.exists(path)) {
			instance.save();
			return;
		}

		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonElement root = JsonParser.parseReader(reader);
			if (!root.isJsonObject()) {
				throw new IllegalArgumentException("Config root must be an object");
			}
			instance = fromJson(root.getAsJsonObject());
		} catch (IOException | RuntimeException exception) {
			CobblemonSpawnDisplayClient.LOGGER.warn(
					"Could not load spawn display settings from {}; using defaults",
					path,
					exception
			);
			instance = new SpawnDisplayConfig();
		}
	}

	public boolean save() {
		Path path = configPath();
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(toJson()), StandardCharsets.UTF_8);
			return true;
		} catch (IOException exception) {
			CobblemonSpawnDisplayClient.LOGGER.error("Could not save spawn display settings to {}", path, exception);
			return false;
		}
	}

	public int getBackgroundOpacityPercent() {
		return Math.round(backgroundOpacity * 100.0F / 255.0F);
	}

	public int getBackgroundColor(int color) {
		return backgroundOpacity << 24 | darkenForBackground(color);
	}

	public int getRarityColor(Rarity rarity) {
		if (rarity == null) {
			return UNKNOWN_RARITY_COLOR;
		}

		return switch (rarity) {
			case COMMON -> commonColor;
			case UNCOMMON -> uncommonColor;
			case RARE -> rareColor;
			case ULTRA_RARE -> ultraRareColor;
		};
	}

	public int getShinyColor() {
		return shinyColor;
	}

	public int getAlphaColor() {
		return alphaColor;
	}

	public int getTeraColor() {
		return teraColor;
	}

	public int getLegendaryColor() {
		return legendaryColor;
	}

	public int getMythicalColor() {
		return mythicalColor;
	}

	public int getFossilColor() {
		return fossilColor;
	}

	public int getUltraBeastBackgroundColor() {
		return ultraBeastBackgroundColor;
	}

	public int getUltraBeastBorderColor() {
		return ultraBeastBorderColor;
	}

	public int getParadoxBackgroundColor() {
		return paradoxBackgroundColor;
	}

	public int getParadoxBorderColor() {
		return paradoxBorderColor;
	}

	public int getRowLength() {
		return rowLength;
	}

	public int getTileSize() {
		return tileSize;
	}

	public int getSpacing() {
		return spacing;
	}

	public int getUpdateIntervalTicks() {
		return updateIntervalTicks;
	}

	public boolean shouldShowCommons() {
		return showCommons;
	}

	public boolean shouldDisableSpriteAnimations() {
		return disableSpriteAnimations;
	}

	public boolean shouldUseHorizontalDistance() {
		return horizontalDistance;
	}

	public boolean shouldHighlight(Identifier species) {
		return species != null && highlightedSpecies.contains(species.getPath());
	}

	public Set<String> getHighlightedSpecies() {
		return Set.copyOf(highlightedSpecies);
	}

	public void setBackgroundOpacityPercent(int percent) {
		backgroundOpacity = Math.round(clamp(percent, 0, 100) * 255.0F / 100.0F);
	}

	public void setRarityColor(Rarity rarity, int color) {
		int rgb = sanitizeColor(color, UNKNOWN_RARITY_COLOR);
		switch (rarity) {
			case COMMON -> commonColor = rgb;
			case UNCOMMON -> uncommonColor = rgb;
			case RARE -> rareColor = rgb;
			case ULTRA_RARE -> ultraRareColor = rgb;
		}
	}

	public void setShinyColor(int color) {
		shinyColor = sanitizeColor(color, DEFAULT_SHINY_COLOR);
	}

	public void setAlphaColor(int color) {
		alphaColor = sanitizeColor(color, DEFAULT_ALPHA_COLOR);
	}

	public void setTeraColor(int color) {
		teraColor = sanitizeColor(color, DEFAULT_TERA_COLOR);
	}

	public void setLegendaryColor(int color) {
		legendaryColor = sanitizeColor(color, DEFAULT_LEGENDARY_COLOR);
	}

	public void setMythicalColor(int color) {
		mythicalColor = sanitizeColor(color, DEFAULT_MYTHICAL_COLOR);
	}

	public void setFossilColor(int color) {
		fossilColor = sanitizeColor(color, DEFAULT_FOSSIL_COLOR);
	}

	public void setUltraBeastBackgroundColor(int color) {
		ultraBeastBackgroundColor = sanitizeColor(color, DEFAULT_ULTRA_BEAST_BACKGROUND_COLOR);
	}

	public void setUltraBeastBorderColor(int color) {
		ultraBeastBorderColor = sanitizeColor(color, DEFAULT_ULTRA_BEAST_BORDER_COLOR);
	}

	public void setParadoxBackgroundColor(int color) {
		paradoxBackgroundColor = sanitizeColor(color, DEFAULT_PARADOX_BACKGROUND_COLOR);
	}

	public void setParadoxBorderColor(int color) {
		paradoxBorderColor = sanitizeColor(color, DEFAULT_PARADOX_BORDER_COLOR);
	}

	public void setRowLength(int value) {
		rowLength = clamp(value, MIN_ROW_LENGTH, MAX_ROW_LENGTH);
	}

	public void setTileSize(int value) {
		tileSize = clamp(value, MIN_TILE_SIZE, MAX_TILE_SIZE);
	}

	public void setSpacing(int value) {
		spacing = clamp(value, MIN_SPACING, MAX_SPACING);
	}

	public void setUpdateIntervalTicks(int value) {
		updateIntervalTicks = clamp(value, MIN_UPDATE_INTERVAL_TICKS, MAX_UPDATE_INTERVAL_TICKS);
	}

	public void setShowCommons(boolean value) {
		showCommons = value;
	}

	public void setDisableSpriteAnimations(boolean value) {
		disableSpriteAnimations = value;
	}

	public void setHorizontalDistance(boolean value) {
		horizontalDistance = value;
	}

	public void setHighlightedPokemon(String value) {
		String input = value == null ? "" : value;
		Set<String> names = new LinkedHashSet<>();
		for (String name : input.split(",")) {
			String normalized = normalizeSpeciesName(name);
			if (!normalized.isEmpty()) {
				names.add(normalized);
			}
		}
		highlightedPokemon = input.trim();
		highlightedSpecies = Set.copyOf(names);
	}

	public void toggleHighlightedPokemon(Identifier species) {
		if (species == null) {
			return;
		}

		Set<String> names = new LinkedHashSet<>(highlightedSpecies);
		if (!names.remove(species.getPath())) {
			names.add(species.getPath());
		}
		setHighlightedPokemon(String.join(", ", names));
	}

	public static String colorToHex(int color) {
		return String.format("#%06X", color & 0xFFFFFF);
	}

	public static int defaultBackgroundOpacityPercent() {
		return Math.round(DEFAULT_BACKGROUND_OPACITY * 100.0F / 255.0F);
	}

	public static int defaultRowLength() {
		return DEFAULT_ROW_LENGTH;
	}

	public static int defaultTileSize() {
		return DEFAULT_TILE_SIZE;
	}

	public static int defaultSpacing() {
		return DEFAULT_SPACING;
	}

	public static int defaultUpdateIntervalTicks() {
		return DEFAULT_UPDATE_INTERVAL_TICKS;
	}

	public static boolean defaultShowCommons() {
		return DEFAULT_SHOW_COMMONS;
	}

	public static boolean defaultDisableSpriteAnimations() {
		return DEFAULT_DISABLE_SPRITE_ANIMATIONS;
	}

	public static boolean defaultHorizontalDistance() {
		return DEFAULT_HORIZONTAL_DISTANCE;
	}

	public static int defaultRarityColor(Rarity rarity) {
		return switch (rarity) {
			case COMMON -> DEFAULT_COMMON_COLOR;
			case UNCOMMON -> DEFAULT_UNCOMMON_COLOR;
			case RARE -> DEFAULT_RARE_COLOR;
			case ULTRA_RARE -> DEFAULT_ULTRA_RARE_COLOR;
		};
	}

	public static int defaultShinyColor() {
		return DEFAULT_SHINY_COLOR;
	}

	public static int defaultAlphaColor() {
		return DEFAULT_ALPHA_COLOR;
	}

	public static int defaultTeraColor() {
		return DEFAULT_TERA_COLOR;
	}

	public static int defaultLegendaryColor() {
		return DEFAULT_LEGENDARY_COLOR;
	}

	public static int defaultMythicalColor() {
		return DEFAULT_MYTHICAL_COLOR;
	}

	public static int defaultFossilColor() {
		return DEFAULT_FOSSIL_COLOR;
	}

	public static int defaultUltraBeastBackgroundColor() {
		return DEFAULT_ULTRA_BEAST_BACKGROUND_COLOR;
	}

	public static int defaultUltraBeastBorderColor() {
		return DEFAULT_ULTRA_BEAST_BORDER_COLOR;
	}

	public static int defaultParadoxBackgroundColor() {
		return DEFAULT_PARADOX_BACKGROUND_COLOR;
	}

	public static int defaultParadoxBorderColor() {
		return DEFAULT_PARADOX_BORDER_COLOR;
	}

	private static SpawnDisplayConfig fromJson(JsonObject json) {
		SpawnDisplayConfig config = new SpawnDisplayConfig();
		config.backgroundOpacity = readInt(json, "backgroundOpacity", config.backgroundOpacity);
		config.commonColor = readColor(json, "commonColor", config.commonColor);
		config.uncommonColor = readColor(json, "uncommonColor", config.uncommonColor);
		config.rareColor = readColor(json, "rareColor", config.rareColor);
		config.ultraRareColor = readColor(json, "ultraRareColor", config.ultraRareColor);
		config.shinyColor = readColor(json, "shinyColor", config.shinyColor);
		config.alphaColor = readColor(json, "alphaColor", config.alphaColor);
		config.teraColor = readColor(json, "teraColor", config.teraColor);
		config.legendaryColor = readColor(json, "legendaryColor", config.legendaryColor);
		config.mythicalColor = readColor(json, "mythicalColor", config.mythicalColor);
		config.fossilColor = readColor(json, "fossilColor", config.fossilColor);
		config.ultraBeastBackgroundColor = readColor(
				json,
				"ultraBeastBackgroundColor",
				config.ultraBeastBackgroundColor
		);
		config.ultraBeastBorderColor = readColor(
				json,
				"ultraBeastBorderColor",
				config.ultraBeastBorderColor
		);
		config.paradoxBackgroundColor = readColor(
				json,
				"paradoxBackgroundColor",
				config.paradoxBackgroundColor
		);
		config.paradoxBorderColor = readColor(json, "paradoxBorderColor", config.paradoxBorderColor);
		if (config.paradoxBackgroundColor == LEGACY_DEFAULT_PARADOX_BACKGROUND_COLOR
				&& config.paradoxBorderColor == LEGACY_DEFAULT_PARADOX_BORDER_COLOR) {
			config.paradoxBackgroundColor = DEFAULT_PARADOX_BACKGROUND_COLOR;
			config.paradoxBorderColor = DEFAULT_PARADOX_BORDER_COLOR;
		}
		if (!json.has("alphaColor") && config.ultraRareColor == LEGACY_DEFAULT_ULTRA_RARE_COLOR) {
			config.ultraRareColor = DEFAULT_ULTRA_RARE_COLOR;
		}
		config.rowLength = readInt(json, "rowLength", config.rowLength);
		config.tileSize = readInt(json, "tileSize", config.tileSize);
		config.spacing = readInt(json, "spacing", config.spacing);
		config.updateIntervalTicks = readInt(json, "updateIntervalTicks", config.updateIntervalTicks);
		config.showCommons = readBoolean(json, "showCommons", config.showCommons);
		config.disableSpriteAnimations = readBoolean(
				json,
				"disableSpriteAnimations",
				config.disableSpriteAnimations
		);
		config.horizontalDistance = readBoolean(json, "horizontalDistance", config.horizontalDistance);
		config.setHighlightedPokemon(readString(json, "highlightedPokemon", config.highlightedPokemon));
		config.sanitize();
		return config;
	}

	private JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("backgroundOpacity", backgroundOpacity);
		json.addProperty("commonColor", colorToHex(commonColor));
		json.addProperty("uncommonColor", colorToHex(uncommonColor));
		json.addProperty("rareColor", colorToHex(rareColor));
		json.addProperty("ultraRareColor", colorToHex(ultraRareColor));
		json.addProperty("shinyColor", colorToHex(shinyColor));
		json.addProperty("alphaColor", colorToHex(alphaColor));
		json.addProperty("teraColor", colorToHex(teraColor));
		json.addProperty("legendaryColor", colorToHex(legendaryColor));
		json.addProperty("mythicalColor", colorToHex(mythicalColor));
		json.addProperty("fossilColor", colorToHex(fossilColor));
		json.addProperty("ultraBeastBackgroundColor", colorToHex(ultraBeastBackgroundColor));
		json.addProperty("ultraBeastBorderColor", colorToHex(ultraBeastBorderColor));
		json.addProperty("paradoxBackgroundColor", colorToHex(paradoxBackgroundColor));
		json.addProperty("paradoxBorderColor", colorToHex(paradoxBorderColor));
		json.addProperty("rowLength", rowLength);
		json.addProperty("tileSize", tileSize);
		json.addProperty("spacing", spacing);
		json.addProperty("updateIntervalTicks", updateIntervalTicks);
		json.addProperty("showCommons", showCommons);
		json.addProperty("disableSpriteAnimations", disableSpriteAnimations);
		json.addProperty("horizontalDistance", horizontalDistance);
		json.addProperty("highlightedPokemon", highlightedPokemon);
		return json;
	}

	private void sanitize() {
		backgroundOpacity = clamp(backgroundOpacity, 0, 255);
		commonColor = sanitizeColor(commonColor, DEFAULT_COMMON_COLOR);
		uncommonColor = sanitizeColor(uncommonColor, DEFAULT_UNCOMMON_COLOR);
		rareColor = sanitizeColor(rareColor, DEFAULT_RARE_COLOR);
		ultraRareColor = sanitizeColor(ultraRareColor, DEFAULT_ULTRA_RARE_COLOR);
		shinyColor = sanitizeColor(shinyColor, DEFAULT_SHINY_COLOR);
		alphaColor = sanitizeColor(alphaColor, DEFAULT_ALPHA_COLOR);
		teraColor = sanitizeColor(teraColor, DEFAULT_TERA_COLOR);
		legendaryColor = sanitizeColor(legendaryColor, DEFAULT_LEGENDARY_COLOR);
		mythicalColor = sanitizeColor(mythicalColor, DEFAULT_MYTHICAL_COLOR);
		fossilColor = sanitizeColor(fossilColor, DEFAULT_FOSSIL_COLOR);
		ultraBeastBackgroundColor = sanitizeColor(
				ultraBeastBackgroundColor,
				DEFAULT_ULTRA_BEAST_BACKGROUND_COLOR
		);
		ultraBeastBorderColor = sanitizeColor(ultraBeastBorderColor, DEFAULT_ULTRA_BEAST_BORDER_COLOR);
		paradoxBackgroundColor = sanitizeColor(paradoxBackgroundColor, DEFAULT_PARADOX_BACKGROUND_COLOR);
		paradoxBorderColor = sanitizeColor(paradoxBorderColor, DEFAULT_PARADOX_BORDER_COLOR);
		rowLength = clamp(rowLength, MIN_ROW_LENGTH, MAX_ROW_LENGTH);
		tileSize = clamp(tileSize, MIN_TILE_SIZE, MAX_TILE_SIZE);
		spacing = clamp(spacing, MIN_SPACING, MAX_SPACING);
		updateIntervalTicks = clamp(updateIntervalTicks, MIN_UPDATE_INTERVAL_TICKS, MAX_UPDATE_INTERVAL_TICKS);
		setHighlightedPokemon(highlightedPokemon);
	}

	private static int readInt(JsonObject json, String key, int fallback) {
		JsonElement value = json.get(key);
		if (value == null || value.isJsonNull()) {
			return fallback;
		}
		try {
			return value.getAsInt();
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private static int readColor(JsonObject json, String key, int fallback) {
		JsonElement value = json.get(key);
		if (value == null || value.isJsonNull()) {
			return fallback;
		}
		try {
			if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
				return sanitizeColor(value.getAsInt(), fallback);
			}
			return parseColor(value.getAsString());
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private static boolean readBoolean(JsonObject json, String key, boolean fallback) {
		JsonElement value = json.get(key);
		if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
			return fallback;
		}
		try {
			return value.getAsBoolean();
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private static String readString(JsonObject json, String key, String fallback) {
		JsonElement value = json.get(key);
		if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
			return fallback;
		}
		try {
			return value.getAsString();
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private static String normalizeSpeciesName(String value) {
		String normalized = value.trim().toLowerCase(Locale.ROOT);
		int namespaceSeparator = normalized.lastIndexOf(':');
		if (namespaceSeparator >= 0) {
			normalized = normalized.substring(namespaceSeparator + 1);
		}
		normalized = normalized.replace("♀", "_f").replace("♂", "_m");
		normalized = normalized.replaceAll("[\\s-]+", "_");
		normalized = normalized.replaceAll("[^a-z0-9_]", "");
		normalized = normalized.replaceAll("_+", "_");
		return normalized.replaceAll("^_|_$", "");
	}

	private static int parseColor(String value) {
		String hex = value.trim();
		if (hex.startsWith("#")) {
			hex = hex.substring(1);
		}
		if (!hex.matches("(?i)[0-9a-f]{6}")) {
			throw new IllegalArgumentException("Expected a six-digit hex color");
		}
		return Integer.parseInt(hex, 16);
	}

	private static int sanitizeColor(int value, int fallback) {
		return value < 0 || value > 0xFFFFFF ? fallback : value;
	}

	private static int darkenForBackground(int color) {
		int red = (color >> 16 & 0xFF) * BACKGROUND_BRIGHTNESS_PERCENT / 100;
		int green = (color >> 8 & 0xFF) * BACKGROUND_BRIGHTNESS_PERCENT / 100;
		int blue = (color & 0xFF) * BACKGROUND_BRIGHTNESS_PERCENT / 100;
		return red << 16 | green << 8 | blue;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static Path configPath() {
		return FabricLoader.getInstance().getConfigDir().resolve(CobblemonSpawnDisplayClient.MOD_ID + ".json");
	}
}
