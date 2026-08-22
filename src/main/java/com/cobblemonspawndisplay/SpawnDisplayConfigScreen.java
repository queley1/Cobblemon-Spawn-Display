package com.cobblemonspawndisplay;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public final class SpawnDisplayConfigScreen extends Screen {
	private static final int CONTROL_HEIGHT = 20;
	private static final int CONTROL_SPACING = 24;
	private static final int RESET_BUTTON_WIDTH = 56;
	private static final int CONTROL_GAP = 4;
	private static final int COLOR_TARGET_COLUMNS = 7;
	private static final int COLOR_PREVIEW_Y = 114;
	private static final int COLOR_PREVIEW_HEIGHT = 20;
	private static final int COLOR_SLIDER_Y = 140;
	private static final int SAVE_DELAY_TICKS = 10;

	private final Screen parent;
	private Page page = Page.GENERAL;
	private ColorTarget selectedColorTarget = ColorTarget.COMMON;
	private Text helperText = Text.translatable("screen.cobblemon_spawn_display.hint");
	private boolean hasError;
	private boolean dirty;
	private int saveCountdown = -1;

	private ButtonWidget generalTab;
	private ButtonWidget colorsTab;
	private final List<ClickableWidget> generalWidgets = new ArrayList<>();
	private final List<ClickableWidget> colorWidgets = new ArrayList<>();
	private final List<ButtonWidget> colorTargetButtons = new ArrayList<>();
	private SliderRow opacityRow;
	private SliderRow rowLengthRow;
	private SliderRow tileSizeRow;
	private SliderRow spacingRow;
	private SliderRow updateIntervalRow;
	private ButtonWidget showCommonsButton;
	private ButtonWidget disableSpriteAnimationsButton;
	private ButtonWidget distanceModeButton;
	private SliderRow hueRow;
	private SliderRow saturationRow;
	private SliderRow lightnessRow;

	public SpawnDisplayConfigScreen(Screen parent) {
		super(Text.translatable("screen.cobblemon_spawn_display.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		SpawnDisplayConfig config = SpawnDisplayConfig.get();
		generalWidgets.clear();
		colorWidgets.clear();
		colorTargetButtons.clear();
		int contentWidth = Math.min(300, this.width - 24);
		int contentX = (this.width - contentWidth) / 2;
		int splitWidth = (contentWidth - 4) / 2;
		int optionButtonWidth = (contentWidth - CONTROL_GAP * 2) * 3 / 10;

		generalTab = addDrawableChild(ButtonWidget.builder(
				Text.translatable("screen.cobblemon_spawn_display.general"),
				button -> setPage(Page.GENERAL)
		).dimensions(contentX, 40, splitWidth, CONTROL_HEIGHT).build());
		colorsTab = addDrawableChild(ButtonWidget.builder(
				Text.translatable("screen.cobblemon_spawn_display.colors"),
				button -> setPage(Page.COLORS)
		).dimensions(contentX + splitWidth + 4, 40, splitWidth, CONTROL_HEIGHT).build());

		int rowY = 66;
		opacityRow = addSliderRow(contentX, rowY, contentWidth, 0, 100, config.getBackgroundOpacityPercent(),
				"screen.cobblemon_spawn_display.opacity", "%", config::setBackgroundOpacityPercent,
				SpawnDisplayConfig::defaultBackgroundOpacityPercent, generalWidgets);
		rowLengthRow = addSliderRow(contentX, rowY + CONTROL_SPACING, contentWidth,
				SpawnDisplayConfig.MIN_ROW_LENGTH, SpawnDisplayConfig.MAX_ROW_LENGTH, config.getRowLength(),
				"screen.cobblemon_spawn_display.row_length", "", config::setRowLength,
				SpawnDisplayConfig::defaultRowLength, generalWidgets);
		tileSizeRow = addSliderRow(contentX, rowY + CONTROL_SPACING * 2, contentWidth,
				SpawnDisplayConfig.MIN_TILE_SIZE, SpawnDisplayConfig.MAX_TILE_SIZE, config.getTileSize(),
				"screen.cobblemon_spawn_display.tile_size", " px", config::setTileSize,
				SpawnDisplayConfig::defaultTileSize, generalWidgets);
		spacingRow = addSliderRow(contentX, rowY + CONTROL_SPACING * 3, contentWidth,
				SpawnDisplayConfig.MIN_SPACING, SpawnDisplayConfig.MAX_SPACING, config.getSpacing(),
				"screen.cobblemon_spawn_display.spacing", " px", config::setSpacing,
				SpawnDisplayConfig::defaultSpacing, generalWidgets);
		updateIntervalRow = addSliderRow(contentX, rowY + CONTROL_SPACING * 4, contentWidth,
				SpawnDisplayConfig.MIN_UPDATE_INTERVAL_TICKS, SpawnDisplayConfig.MAX_UPDATE_INTERVAL_TICKS,
				config.getUpdateIntervalTicks(), "screen.cobblemon_spawn_display.update_ticks", " ticks",
				config::setUpdateIntervalTicks, SpawnDisplayConfig::defaultUpdateIntervalTicks, generalWidgets);
		showCommonsButton = addDrawableChild(ButtonWidget.builder(showCommonsText(), button -> {
			config.setShowCommons(!config.shouldShowCommons());
			updateShowCommonsButton();
			settingsChanged();
		}).dimensions(contentX, rowY + CONTROL_SPACING * 5, optionButtonWidth, CONTROL_HEIGHT).build());
		generalWidgets.add(showCommonsButton);
		disableSpriteAnimationsButton = addDrawableChild(ButtonWidget.builder(disableSpriteAnimationsText(), button -> {
			config.setDisableSpriteAnimations(!config.shouldDisableSpriteAnimations());
			updateDisableSpriteAnimationsButton();
			settingsChanged();
		}).dimensions(
				contentX + optionButtonWidth + CONTROL_GAP,
				rowY + CONTROL_SPACING * 5,
				optionButtonWidth,
				CONTROL_HEIGHT
		).build());
		generalWidgets.add(disableSpriteAnimationsButton);
		distanceModeButton = addDrawableChild(ButtonWidget.builder(distanceModeText(), button -> {
			config.setHorizontalDistance(!config.shouldUseHorizontalDistance());
			updateDistanceModeButton();
			settingsChanged();
		}).dimensions(
				contentX + (optionButtonWidth + CONTROL_GAP) * 2,
				rowY + CONTROL_SPACING * 5,
				contentWidth - optionButtonWidth * 2 - CONTROL_GAP * 2,
				CONTROL_HEIGHT
		).build());
		generalWidgets.add(distanceModeButton);

		addColorTargetButtons(contentX, rowY, contentWidth);
		Hsl hsl = rgbToHsl(selectedColorTarget.getColor(config));
		hueRow = addSliderRow(contentX, COLOR_SLIDER_Y, contentWidth, 0, 359, hsl.hue(),
				"screen.cobblemon_spawn_display.hue", "°", value -> applySelectedColor(),
				() -> defaultSelectedHsl().hue(), colorWidgets);
		saturationRow = addSliderRow(contentX, COLOR_SLIDER_Y + CONTROL_SPACING, contentWidth,
				0, 100, hsl.saturation(),
				"screen.cobblemon_spawn_display.saturation", "%", value -> applySelectedColor(),
				() -> defaultSelectedHsl().saturation(), colorWidgets);
		lightnessRow = addSliderRow(contentX, COLOR_SLIDER_Y + CONTROL_SPACING * 2, contentWidth,
				0, 100, hsl.lightness(),
				"screen.cobblemon_spawn_display.lightness", "%", value -> applySelectedColor(),
				() -> defaultSelectedHsl().lightness(), colorWidgets);

		int buttonY = this.height - 28;
		addDrawableChild(ButtonWidget.builder(Text.translatable("screen.cobblemon_spawn_display.reset"), button -> resetAll())
				.dimensions(contentX, buttonY, splitWidth, CONTROL_HEIGHT)
				.build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("screen.cobblemon_spawn_display.close"), button -> close())
				.dimensions(contentX + splitWidth + 4, buttonY, splitWidth, CONTROL_HEIGHT)
				.build());

		updatePageVisibility();
	}

	@Override
	protected void applyBlur(float delta) {
		// Keep the world and HUD sharp so changes can be previewed behind this screen.
	}

	@Override
	public void tick() {
		super.tick();
		if (dirty && saveCountdown > 0 && --saveCountdown == 0) {
			saveNow();
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		updateResetButtonStates();
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 16, 0xFFFFFF);
		context.drawCenteredTextWithShadow(textRenderer, helperText, width / 2, 28, hasError ? 0xFF5555 : 0xC0C0C0);

		if (page == Page.COLORS) {
			renderColorTargetButtons(context);
			renderColorPreview(context);
		}
	}

	@Override
	public void close() {
		saveNow();
		MinecraftClient.getInstance().setScreen(parent);
	}

	@Override
	public void removed() {
		saveNow();
		super.removed();
	}

	private SliderRow addSliderRow(
			int x,
			int y,
			int width,
			int minimum,
			int maximum,
			int current,
			String labelKey,
			String suffix,
			IntConsumer setter,
			IntSupplier resetValue,
			List<ClickableWidget> pageWidgets
	) {
		int sliderWidth = width - RESET_BUTTON_WIDTH - CONTROL_GAP;
		IntConsumer applyValue = value -> {
			setter.accept(value);
			settingsChanged();
		};
		IntSlider slider = addDrawableChild(new IntSlider(
				x, y, sliderWidth, minimum, maximum, current, labelKey, suffix, applyValue
		));
		ButtonWidget resetButton = addDrawableChild(ButtonWidget.builder(
				Text.translatable("screen.cobblemon_spawn_display.reset_one"),
				button -> {
					int defaultValue = resetValue.getAsInt();
					slider.setIntValue(defaultValue);
					applyValue.accept(defaultValue);
				}
		).dimensions(x + sliderWidth + CONTROL_GAP, y, RESET_BUTTON_WIDTH, CONTROL_HEIGHT).build());
		pageWidgets.add(slider);
		pageWidgets.add(resetButton);
		return new SliderRow(slider, resetButton, resetValue);
	}

	private void addColorTargetButtons(int contentX, int y, int contentWidth) {
		ColorTarget[] targets = ColorTarget.values();
		int buttonWidth = (contentWidth - CONTROL_GAP * (COLOR_TARGET_COLUMNS - 1)) / COLOR_TARGET_COLUMNS;

		for (int index = 0; index < targets.length; index++) {
			ColorTarget target = targets[index];
			int row = index / COLOR_TARGET_COLUMNS;
			int column = index % COLOR_TARGET_COLUMNS;
			int rowStart = row * COLOR_TARGET_COLUMNS;
			int buttonsInRow = Math.min(COLOR_TARGET_COLUMNS, targets.length - rowStart);
			int rowWidth = buttonsInRow * buttonWidth + (buttonsInRow - 1) * CONTROL_GAP;
			int rowX = contentX + (contentWidth - rowWidth) / 2;
			ButtonWidget button = addDrawableChild(ButtonWidget.builder(
					target.buttonLabel(),
					ignored -> selectColorTarget(target)
			).dimensions(
					rowX + column * (buttonWidth + CONTROL_GAP),
					y + row * (CONTROL_HEIGHT + CONTROL_GAP),
					buttonWidth,
					CONTROL_HEIGHT
			).build());
			colorTargetButtons.add(button);
			colorWidgets.add(button);
		}
	}

	private void setPage(Page newPage) {
		page = newPage;
		updatePageVisibility();
	}

	private void updatePageVisibility() {
		boolean showGeneral = page == Page.GENERAL;
		generalWidgets.forEach(widget -> setVisible(widget, showGeneral));
		colorWidgets.forEach(widget -> setVisible(widget, !showGeneral));
		updateColorTargetButtonStates();
		generalTab.active = !showGeneral;
		colorsTab.active = showGeneral;
	}

	private static void setVisible(ClickableWidget widget, boolean visible) {
		widget.visible = visible;
		widget.active = visible;
	}

	private void selectColorTarget(ColorTarget target) {
		selectedColorTarget = target;
		updateColorTargetButtonStates();
		loadSelectedColor();
	}

	private void updateColorTargetButtonStates() {
		colorTargetButtons.forEach(button -> button.active = page == Page.COLORS);
	}

	private void loadSelectedColor() {
		Hsl hsl = rgbToHsl(selectedColorTarget.getColor(SpawnDisplayConfig.get()));
		hueRow.slider().setIntValue(hsl.hue());
		saturationRow.slider().setIntValue(hsl.saturation());
		lightnessRow.slider().setIntValue(hsl.lightness());
	}

	private void applySelectedColor() {
		if (hueRow == null || saturationRow == null || lightnessRow == null) {
			return;
		}
		int color = hslToRgb(
				hueRow.slider().getIntValue(),
				saturationRow.slider().getIntValue(),
				lightnessRow.slider().getIntValue()
		);
		selectedColorTarget.setColor(SpawnDisplayConfig.get(), color);
	}

	private Hsl defaultSelectedHsl() {
		return rgbToHsl(selectedColorTarget.defaultColor());
	}

	private void renderColorTargetButtons(DrawContext context) {
		ColorTarget[] targets = ColorTarget.values();
		SpawnDisplayConfig config = SpawnDisplayConfig.get();
		for (int index = 0; index < colorTargetButtons.size(); index++) {
			ButtonWidget button = colorTargetButtons.get(index);
			int color = targets[index].getColor(config);
			boolean selected = targets[index] == selectedColorTarget;
			int borderWidth = 1;
			int borderColor = selected ? 0xFFFFFFFF : 0xFF000000;

			context.fill(
					button.getX(),
					button.getY(),
					button.getX() + button.getWidth(),
					button.getY() + button.getHeight(),
					borderColor
			);
			context.fill(
					button.getX() + borderWidth,
					button.getY() + borderWidth,
					button.getX() + button.getWidth() - borderWidth,
					button.getY() + button.getHeight() - borderWidth,
					0xFF000000 | color
			);
			drawCenteredWhiteTextWithBlackShadow(
					context,
					button.getMessage(),
					button.getX() + button.getWidth() / 2,
					button.getY() + 6
			);
		}
	}

	private void drawCenteredWhiteTextWithBlackShadow(
			DrawContext context,
			Text text,
			int centerX,
			int y
	) {
		int textX = centerX - textRenderer.getWidth(text) / 2;
		context.drawText(textRenderer, text, textX + 1, y + 1, 0xFF000000, false);
		context.drawText(textRenderer, text, textX, y, 0xFFFFFFFF, false);
	}

	private void renderColorPreview(DrawContext context) {
		int contentWidth = Math.min(300, this.width - 24);
		int contentX = (this.width - contentWidth) / 2;
		int color = selectedColorTarget.getColor(SpawnDisplayConfig.get());
		context.fill(
				contentX,
				COLOR_PREVIEW_Y,
				contentX + contentWidth,
				COLOR_PREVIEW_Y + COLOR_PREVIEW_HEIGHT,
				0xFF000000
		);
		context.fill(
				contentX + 1,
				COLOR_PREVIEW_Y + 1,
				contentX + contentWidth - 1,
				COLOR_PREVIEW_Y + COLOR_PREVIEW_HEIGHT - 1,
				0xFF000000 | color
		);

		String hex = SpawnDisplayConfig.colorToHex(color);
		Text previewText = selectedColorTarget.label().copy().append("  " + hex);
		int foreground = contrastingTextColor(color);
		int textX = width / 2 - textRenderer.getWidth(previewText) / 2;
		context.drawText(textRenderer, previewText, textX, COLOR_PREVIEW_Y + 6, foreground, false);
	}

	private static int contrastingTextColor(int color) {
		double luminance = 0.2126 * linearColorChannel(color >> 16 & 0xFF)
				+ 0.7152 * linearColorChannel(color >> 8 & 0xFF)
				+ 0.0722 * linearColorChannel(color & 0xFF);
		double whiteContrast = 1.05 / (luminance + 0.05);
		double blackContrast = (luminance + 0.05) / 0.05;
		return whiteContrast >= blackContrast ? 0xFFFFFFFF : 0xFF000000;
	}

	private static double linearColorChannel(int channel) {
		double normalized = channel / 255.0;
		return normalized <= 0.04045
				? normalized / 12.92
				: Math.pow((normalized + 0.055) / 1.055, 2.4);
	}

	private void resetAll() {
		SpawnDisplayConfig config = SpawnDisplayConfig.get();
		config.setBackgroundOpacityPercent(SpawnDisplayConfig.defaultBackgroundOpacityPercent());
		config.setRowLength(SpawnDisplayConfig.defaultRowLength());
		config.setTileSize(SpawnDisplayConfig.defaultTileSize());
		config.setSpacing(SpawnDisplayConfig.defaultSpacing());
		config.setUpdateIntervalTicks(SpawnDisplayConfig.defaultUpdateIntervalTicks());
		config.setShowCommons(SpawnDisplayConfig.defaultShowCommons());
		config.setDisableSpriteAnimations(SpawnDisplayConfig.defaultDisableSpriteAnimations());
		config.setHorizontalDistance(SpawnDisplayConfig.defaultHorizontalDistance());
		for (Rarity rarity : Rarity.values()) {
			config.setRarityColor(rarity, SpawnDisplayConfig.defaultRarityColor(rarity));
		}
		config.setShinyColor(SpawnDisplayConfig.defaultShinyColor());
		config.setAlphaColor(SpawnDisplayConfig.defaultAlphaColor());
		config.setTeraColor(SpawnDisplayConfig.defaultTeraColor());
		config.setLegendaryColor(SpawnDisplayConfig.defaultLegendaryColor());
		config.setMythicalColor(SpawnDisplayConfig.defaultMythicalColor());
		config.setFossilColor(SpawnDisplayConfig.defaultFossilColor());
		config.setUltraBeastBackgroundColor(SpawnDisplayConfig.defaultUltraBeastBackgroundColor());
		config.setUltraBeastBorderColor(SpawnDisplayConfig.defaultUltraBeastBorderColor());
		config.setParadoxBackgroundColor(SpawnDisplayConfig.defaultParadoxBackgroundColor());
		config.setParadoxBorderColor(SpawnDisplayConfig.defaultParadoxBorderColor());

		opacityRow.slider().setIntValue(config.getBackgroundOpacityPercent());
		rowLengthRow.slider().setIntValue(config.getRowLength());
		tileSizeRow.slider().setIntValue(config.getTileSize());
		spacingRow.slider().setIntValue(config.getSpacing());
		updateIntervalRow.slider().setIntValue(config.getUpdateIntervalTicks());
		updateShowCommonsButton();
		updateDisableSpriteAnimationsButton();
		updateDistanceModeButton();
		loadSelectedColor();
		settingsChanged();
	}

	private void updateResetButtonStates() {
		opacityRow.updateResetState();
		rowLengthRow.updateResetState();
		tileSizeRow.updateResetState();
		spacingRow.updateResetState();
		updateIntervalRow.updateResetState();
		hueRow.updateResetState();
		saturationRow.updateResetState();
		lightnessRow.updateResetState();
	}

	private void settingsChanged() {
		SpawnHud.requestRefresh();
		dirty = true;
		saveCountdown = SAVE_DELAY_TICKS;
		setHint();
	}

	private void saveNow() {
		if (!dirty) {
			return;
		}
		dirty = false;
		saveCountdown = -1;
		if (!SpawnDisplayConfig.get().save()) {
			setError("Could not save settings. Check the game log.");
		}
	}

	private Text showCommonsText() {
		Text value = Text.translatable(SpawnDisplayConfig.get().shouldShowCommons()
				? "screen.cobblemon_spawn_display.on"
				: "screen.cobblemon_spawn_display.off");
		return Text.translatable("screen.cobblemon_spawn_display.show_commons_value", value);
	}

	private void updateShowCommonsButton() {
		showCommonsButton.setMessage(showCommonsText());
	}

	private Text disableSpriteAnimationsText() {
		Text value = Text.translatable(SpawnDisplayConfig.get().shouldDisableSpriteAnimations()
				? "screen.cobblemon_spawn_display.off"
				: "screen.cobblemon_spawn_display.on");
		return Text.translatable("screen.cobblemon_spawn_display.disable_sprite_animations_value", value);
	}

	private void updateDisableSpriteAnimationsButton() {
		disableSpriteAnimationsButton.setMessage(disableSpriteAnimationsText());
	}

	private Text distanceModeText() {
		Text value = Text.translatable(SpawnDisplayConfig.get().shouldUseHorizontalDistance()
				? "screen.cobblemon_spawn_display.distance_horizontal"
				: "screen.cobblemon_spawn_display.distance_absolute");
		return Text.translatable("screen.cobblemon_spawn_display.distance_mode_value", value);
	}

	private void updateDistanceModeButton() {
		distanceModeButton.setMessage(distanceModeText());
	}

	private void setError(String message) {
		helperText = Text.literal(message);
		hasError = true;
	}

	private void setHint() {
		helperText = Text.translatable("screen.cobblemon_spawn_display.hint");
		hasError = false;
	}

	private static Hsl rgbToHsl(int color) {
		double red = (color >> 16 & 0xFF) / 255.0;
		double green = (color >> 8 & 0xFF) / 255.0;
		double blue = (color & 0xFF) / 255.0;
		double maximum = Math.max(red, Math.max(green, blue));
		double minimum = Math.min(red, Math.min(green, blue));
		double delta = maximum - minimum;
		double lightness = (maximum + minimum) / 2.0;
		double hue = 0.0;
		double saturation = 0.0;

		if (delta > 0.0) {
			saturation = delta / (1.0 - Math.abs(2.0 * lightness - 1.0));
			if (maximum == red) {
				hue = 60.0 * (((green - blue) / delta) % 6.0);
			} else if (maximum == green) {
				hue = 60.0 * (((blue - red) / delta) + 2.0);
			} else {
				hue = 60.0 * (((red - green) / delta) + 4.0);
			}
			if (hue < 0.0) {
				hue += 360.0;
			}
		}

		return new Hsl(
				Math.floorMod((int) Math.round(hue), 360),
				clamp((int) Math.round(saturation * 100.0), 0, 100),
				clamp((int) Math.round(lightness * 100.0), 0, 100)
		);
	}

	private static int hslToRgb(int hue, int saturation, int lightness) {
		double normalizedSaturation = saturation / 100.0;
		double normalizedLightness = lightness / 100.0;
		double chroma = (1.0 - Math.abs(2.0 * normalizedLightness - 1.0)) * normalizedSaturation;
		double hueSection = Math.floorMod(hue, 360) / 60.0;
		double secondary = chroma * (1.0 - Math.abs(hueSection % 2.0 - 1.0));
		double red;
		double green;
		double blue;

		switch ((int) Math.floor(hueSection)) {
			case 0 -> {
				red = chroma;
				green = secondary;
				blue = 0.0;
			}
			case 1 -> {
				red = secondary;
				green = chroma;
				blue = 0.0;
			}
			case 2 -> {
				red = 0.0;
				green = chroma;
				blue = secondary;
			}
			case 3 -> {
				red = 0.0;
				green = secondary;
				blue = chroma;
			}
			case 4 -> {
				red = secondary;
				green = 0.0;
				blue = chroma;
			}
			default -> {
				red = chroma;
				green = 0.0;
				blue = secondary;
			}
		}

		double match = normalizedLightness - chroma / 2.0;
		return toColorByte(red + match) << 16 | toColorByte(green + match) << 8 | toColorByte(blue + match);
	}

	private static int toColorByte(double value) {
		return clamp((int) Math.round(value * 255.0), 0, 255);
	}

	private static int clamp(int value, int minimum, int maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	private enum Page {
		GENERAL,
		COLORS
	}

	private enum ColorTarget {
		COMMON,
		UNCOMMON,
		RARE,
		ULTRA_RARE,
		LEGENDARY,
		MYTHICAL,
		FOSSIL,
		ULTRA_BEAST_BACKGROUND,
		ULTRA_BEAST_BORDER,
		PARADOX_BACKGROUND,
		PARADOX_BORDER,
		SHINY,
		ALPHA,
		TERA;

		private int getColor(SpawnDisplayConfig config) {
			return switch (this) {
				case COMMON, UNCOMMON, RARE, ULTRA_RARE -> config.getRarityColor(rarity());
				case SHINY -> config.getShinyColor();
				case ALPHA -> config.getAlphaColor();
				case TERA -> config.getTeraColor();
				case LEGENDARY -> config.getLegendaryColor();
				case MYTHICAL -> config.getMythicalColor();
				case FOSSIL -> config.getFossilColor();
				case ULTRA_BEAST_BACKGROUND -> config.getUltraBeastBackgroundColor();
				case ULTRA_BEAST_BORDER -> config.getUltraBeastBorderColor();
				case PARADOX_BACKGROUND -> config.getParadoxBackgroundColor();
				case PARADOX_BORDER -> config.getParadoxBorderColor();
			};
		}

		private void setColor(SpawnDisplayConfig config, int color) {
			switch (this) {
				case COMMON, UNCOMMON, RARE, ULTRA_RARE -> config.setRarityColor(rarity(), color);
				case SHINY -> config.setShinyColor(color);
				case ALPHA -> config.setAlphaColor(color);
				case TERA -> config.setTeraColor(color);
				case LEGENDARY -> config.setLegendaryColor(color);
				case MYTHICAL -> config.setMythicalColor(color);
				case FOSSIL -> config.setFossilColor(color);
				case ULTRA_BEAST_BACKGROUND -> config.setUltraBeastBackgroundColor(color);
				case ULTRA_BEAST_BORDER -> config.setUltraBeastBorderColor(color);
				case PARADOX_BACKGROUND -> config.setParadoxBackgroundColor(color);
				case PARADOX_BORDER -> config.setParadoxBorderColor(color);
			}
		}

		private int defaultColor() {
			return switch (this) {
				case COMMON, UNCOMMON, RARE, ULTRA_RARE -> SpawnDisplayConfig.defaultRarityColor(rarity());
				case SHINY -> SpawnDisplayConfig.defaultShinyColor();
				case ALPHA -> SpawnDisplayConfig.defaultAlphaColor();
				case TERA -> SpawnDisplayConfig.defaultTeraColor();
				case LEGENDARY -> SpawnDisplayConfig.defaultLegendaryColor();
				case MYTHICAL -> SpawnDisplayConfig.defaultMythicalColor();
				case FOSSIL -> SpawnDisplayConfig.defaultFossilColor();
				case ULTRA_BEAST_BACKGROUND -> SpawnDisplayConfig.defaultUltraBeastBackgroundColor();
				case ULTRA_BEAST_BORDER -> SpawnDisplayConfig.defaultUltraBeastBorderColor();
				case PARADOX_BACKGROUND -> SpawnDisplayConfig.defaultParadoxBackgroundColor();
				case PARADOX_BORDER -> SpawnDisplayConfig.defaultParadoxBorderColor();
			};
		}

		private Rarity rarity() {
			return switch (this) {
				case COMMON -> Rarity.COMMON;
				case UNCOMMON -> Rarity.UNCOMMON;
				case RARE -> Rarity.RARE;
				case ULTRA_RARE -> Rarity.ULTRA_RARE;
				case SHINY, ALPHA, TERA, LEGENDARY, MYTHICAL, FOSSIL, ULTRA_BEAST_BACKGROUND, ULTRA_BEAST_BORDER,
						PARADOX_BACKGROUND, PARADOX_BORDER ->
						throw new IllegalStateException(this + " is not a rarity");
			};
		}

		private Text label() {
			return Text.translatable(switch (this) {
				case COMMON -> "screen.cobblemon_spawn_display.common";
				case UNCOMMON -> "screen.cobblemon_spawn_display.uncommon";
				case RARE -> "screen.cobblemon_spawn_display.rare";
				case ULTRA_RARE -> "screen.cobblemon_spawn_display.ultra_rare";
				case SHINY -> "screen.cobblemon_spawn_display.shiny";
				case ALPHA -> "screen.cobblemon_spawn_display.alpha";
				case TERA -> "screen.cobblemon_spawn_display.tera";
				case LEGENDARY -> "screen.cobblemon_spawn_display.legendary";
				case MYTHICAL -> "screen.cobblemon_spawn_display.mythical";
				case FOSSIL -> "screen.cobblemon_spawn_display.fossil";
				case ULTRA_BEAST_BACKGROUND -> "screen.cobblemon_spawn_display.ultra_beast_background";
				case ULTRA_BEAST_BORDER -> "screen.cobblemon_spawn_display.ultra_beast_border";
				case PARADOX_BACKGROUND -> "screen.cobblemon_spawn_display.paradox_background";
				case PARADOX_BORDER -> "screen.cobblemon_spawn_display.paradox_border";
			});
		}

		private Text buttonLabel() {
			return Text.literal(switch (this) {
				case COMMON -> "C";
				case UNCOMMON -> "U";
				case RARE -> "R";
				case ULTRA_RARE -> "UR";
				case LEGENDARY -> "L";
				case MYTHICAL -> "M";
				case FOSSIL -> "F";
				case ULTRA_BEAST_BACKGROUND -> "UB-1";
				case ULTRA_BEAST_BORDER -> "UB-2";
				case PARADOX_BACKGROUND -> "P-1";
				case PARADOX_BORDER -> "P-2";
				case SHINY -> "S";
				case ALPHA -> "A";
				case TERA -> "T";
			});
		}
	}

	private record Hsl(int hue, int saturation, int lightness) {
	}

	private record SliderRow(IntSlider slider, ButtonWidget resetButton, IntSupplier resetValue) {
		private void updateResetState() {
			resetButton.active = resetButton.visible && slider.getIntValue() != resetValue.getAsInt();
		}
	}

	private static final class IntSlider extends SliderWidget {
		private final int minimum;
		private final int maximum;
		private final String labelKey;
		private final String suffix;
		private final IntConsumer onChanged;
		private int lastAppliedValue;

		private IntSlider(
				int x,
				int y,
				int width,
				int minimum,
				int maximum,
				int current,
				String labelKey,
				String suffix,
				IntConsumer onChanged
		) {
			super(x, y, width, CONTROL_HEIGHT, Text.empty(), normalize(current, minimum, maximum));
			this.minimum = minimum;
			this.maximum = maximum;
			this.labelKey = labelKey;
			this.suffix = suffix;
			this.onChanged = onChanged;
			this.lastAppliedValue = getIntValue();
			updateMessage();
		}

		private int getIntValue() {
			return minimum + (int) Math.round(value * (maximum - minimum));
		}

		private void setIntValue(int newValue) {
			int clamped = clamp(newValue, minimum, maximum);
			value = normalize(clamped, minimum, maximum);
			lastAppliedValue = clamped;
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(Text.translatable(labelKey).append(": " + getIntValue() + suffix));
		}

		@Override
		protected void applyValue() {
			int current = getIntValue();
			value = normalize(current, minimum, maximum);
			if (current != lastAppliedValue) {
				lastAppliedValue = current;
				onChanged.accept(current);
			}
		}

		@Override
		public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
			if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT) {
				int direction = keyCode == GLFW.GLFW_KEY_LEFT ? -1 : 1;
				int next = clamp(getIntValue() + direction, minimum, maximum);
				if (next != getIntValue()) {
					setIntValue(next);
					onChanged.accept(next);
				}
				return true;
			}
			return super.keyPressed(keyCode, scanCode, modifiers);
		}

		private static double normalize(int value, int minimum, int maximum) {
			return (clamp(value, minimum, maximum) - minimum) / (double) (maximum - minimum);
		}
	}
}
