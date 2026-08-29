package com.cobblemonspawndisplay;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public final class SpawnDisplayConfigScreen extends Screen {
	private static final int CONTROL_HEIGHT = 20;
	private static final int CONTROL_SPACING = 24;
	private static final int RESET_BUTTON_WIDTH = 56;
	private static final int CONTROL_GAP = 4;
	private static final int CUSTOMIZE_LIST_Y = 90;
	private static final int CUSTOMIZE_ROW_HEIGHT = 34;
	private static final int CUSTOMIZE_COLUMN_GAP = 8;
	private static final int CUSTOMIZE_SLIDER_Y = 116;
	private static final int PREVIEW_TILE_SIZE = 30;
	private static final int ASPECT_PREVIEW_BACKGROUND_COLOR = 0x555555;
	private static final int ASPECT_PREVIEW_BORDER_COLOR = 0xAAAAAA;
	private static final int HIGHLIGHT_SUMMARY_Y = 66;
	private static final int HIGHLIGHT_LIST_Y = 78;
	private static final int HIGHLIGHT_LIST_BOTTOM_GAP = 6;
	private static final int HIGHLIGHT_ROW_HEIGHT = 20;
	private static final int MAX_SEARCH_LENGTH = 64;
	private static final int SAVE_DELAY_TICKS = 10;

	private final Screen parent;
	private Page page = Page.GENERAL;
	private CustomizeTarget selectedCustomizeTarget = CustomizeTarget.COMMON;
	private CustomizeRole selectedCustomizeRole = CustomizeRole.BACKGROUND;
	private CustomizeCategory customizeCategory = CustomizeCategory.RARITIES;
	private Text helperText = Text.translatable("screen.cobblemon_spawn_display.hint");
	private boolean hasError;
	private boolean dirty;
	private int saveCountdown = -1;

	private ButtonWidget generalTab;
	private ButtonWidget highlightsTab;
	private ButtonWidget customizeTab;
	private final List<ClickableWidget> generalWidgets = new ArrayList<>();
	private final List<ClickableWidget> highlightWidgets = new ArrayList<>();
	private final List<ClickableWidget> customizeWidgets = new ArrayList<>();
	private SliderRow opacityRow;
	private SliderRow rowLengthRow;
	private SliderRow tileSizeRow;
	private SliderRow spacingRow;
	private SliderRow updateIntervalRow;
	private ButtonWidget showCommonsButton;
	private ButtonWidget disableSpriteAnimationsButton;
	private ButtonWidget distanceModeButton;
	private TextFieldWidget pokemonSearchField;
	private PokemonListWidget selectedPokemonList;
	private PokemonListWidget availablePokemonList;
	private List<SpeciesOption> pokemonOptions = List.of();
	private boolean highlightListsDirty;
	private ButtonWidget raritiesButton;
	private ButtonWidget aspectsButton;
	private ButtonWidget backgroundRoleButton;
	private ButtonWidget borderRoleButton;
	private CustomizeListWidget customizeList;
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
		highlightWidgets.clear();
		customizeWidgets.clear();
		int contentWidth = Math.min(420, this.width - 24);
		int contentX = (this.width - contentWidth) / 2;
		int splitWidth = (contentWidth - 4) / 2;
		int tabWidth = (contentWidth - CONTROL_GAP * 2) / 3;
		int optionButtonWidth = (contentWidth - CONTROL_GAP * 2) * 3 / 10;

		generalTab = addDrawableChild(ButtonWidget.builder(
				Text.translatable("screen.cobblemon_spawn_display.general"),
				button -> setPage(Page.GENERAL)
		).dimensions(contentX, 40, tabWidth, CONTROL_HEIGHT).build());
		highlightsTab = addDrawableChild(ButtonWidget.builder(
				Text.translatable("screen.cobblemon_spawn_display.highlights"),
				button -> setPage(Page.HIGHLIGHTS)
		).dimensions(contentX + tabWidth + CONTROL_GAP, 40, tabWidth, CONTROL_HEIGHT).build());
		customizeTab = addDrawableChild(ButtonWidget.builder(
				Text.translatable("screen.cobblemon_spawn_display.customize"),
				button -> setPage(Page.CUSTOMIZE)
		).dimensions(
				contentX + (tabWidth + CONTROL_GAP) * 2,
				40,
				contentWidth - tabWidth * 2 - CONTROL_GAP * 2,
				CONTROL_HEIGHT
		).build());

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

		pokemonOptions = loadPokemonOptions();
		int buttonY = this.height - 28;
		int availableBottom = buttonY - HIGHLIGHT_LIST_BOTTOM_GAP;
		int rightColumnX = contentX + splitWidth + CONTROL_GAP;
		int selectedListHeight = Math.max(HIGHLIGHT_ROW_HEIGHT, availableBottom - HIGHLIGHT_LIST_Y);
		selectedPokemonList = addDrawableChild(new PokemonListWidget(
				MinecraftClient.getInstance(),
				splitWidth,
				selectedListHeight,
				HIGHLIGHT_LIST_Y,
				HIGHLIGHT_ROW_HEIGHT
		));
		selectedPokemonList.setX(contentX);
		highlightWidgets.add(selectedPokemonList);

		pokemonSearchField = addDrawableChild(new TextFieldWidget(
				textRenderer,
				rightColumnX,
				HIGHLIGHT_LIST_Y,
				splitWidth,
				CONTROL_HEIGHT,
				Text.translatable("screen.cobblemon_spawn_display.highlight_search")
		));
		pokemonSearchField.setMaxLength(MAX_SEARCH_LENGTH);
		pokemonSearchField.setPlaceholder(Text.translatable(
				"screen.cobblemon_spawn_display.highlight_search_placeholder"
		));
		highlightWidgets.add(pokemonSearchField);

		int availableListY = HIGHLIGHT_LIST_Y + CONTROL_HEIGHT + CONTROL_GAP;
		int availableListHeight = Math.max(HIGHLIGHT_ROW_HEIGHT, availableBottom - availableListY);
		availablePokemonList = addDrawableChild(new PokemonListWidget(
				MinecraftClient.getInstance(),
				splitWidth,
				availableListHeight,
				availableListY,
				HIGHLIGHT_ROW_HEIGHT
		));
		availablePokemonList.setX(rightColumnX);
		highlightWidgets.add(availablePokemonList);
		pokemonSearchField.setChangedListener(this::filterPokemonOptions);
		refreshHighlightLists(true);

		int customizeListWidth = Math.min(168, Math.max(120, contentWidth * 2 / 5));
		int editorX = contentX + customizeListWidth + CUSTOMIZE_COLUMN_GAP;
		int editorWidth = contentWidth - customizeListWidth - CUSTOMIZE_COLUMN_GAP;
		int categoryButtonWidth = (customizeListWidth - CONTROL_GAP) / 2;
		raritiesButton = addDrawableChild(ButtonWidget.builder(
				Text.translatable("screen.cobblemon_spawn_display.rarities"),
				ignored -> setCustomizeCategory(CustomizeCategory.RARITIES)
		).dimensions(contentX, rowY, categoryButtonWidth, CONTROL_HEIGHT).build());
		aspectsButton = addDrawableChild(ButtonWidget.builder(
				Text.translatable("screen.cobblemon_spawn_display.aspects"),
				ignored -> setCustomizeCategory(CustomizeCategory.ASPECTS)
		).dimensions(
				contentX + categoryButtonWidth + CONTROL_GAP,
				rowY,
				customizeListWidth - categoryButtonWidth - CONTROL_GAP,
				CONTROL_HEIGHT
		).build());
		customizeWidgets.add(raritiesButton);
		customizeWidgets.add(aspectsButton);

		int customizeListHeight = Math.max(CUSTOMIZE_ROW_HEIGHT, availableBottom - CUSTOMIZE_LIST_Y);
		customizeList = addDrawableChild(new CustomizeListWidget(
				MinecraftClient.getInstance(),
				customizeListWidth,
				customizeListHeight,
				CUSTOMIZE_LIST_Y,
				CUSTOMIZE_ROW_HEIGHT
		));
		customizeList.setX(contentX);
		customizeList.setTargets(customizeCategory);
		customizeWidgets.add(customizeList);

		int roleButtonWidth = (editorWidth - CONTROL_GAP) / 2;
		backgroundRoleButton = addDrawableChild(ButtonWidget.builder(
				Text.translatable("screen.cobblemon_spawn_display.background"),
				ignored -> selectCustomizeRole(CustomizeRole.BACKGROUND)
		).dimensions(editorX, 82, roleButtonWidth, CONTROL_HEIGHT).build());
		borderRoleButton = addDrawableChild(ButtonWidget.builder(
				Text.translatable("screen.cobblemon_spawn_display.border"),
				ignored -> selectCustomizeRole(CustomizeRole.BORDER)
		).dimensions(
				editorX + roleButtonWidth + CONTROL_GAP,
				82,
				editorWidth - roleButtonWidth - CONTROL_GAP,
				CONTROL_HEIGHT
		).build());
		customizeWidgets.add(backgroundRoleButton);
		customizeWidgets.add(borderRoleButton);

		Hsl hsl = rgbToHsl(selectedCustomizeTarget.getColor(config, selectedCustomizeRole));
		hueRow = addSliderRow(editorX, CUSTOMIZE_SLIDER_Y, editorWidth, 0, 359, hsl.hue(),
				"screen.cobblemon_spawn_display.hue", "°", value -> applySelectedColor(),
				() -> defaultSelectedHsl().hue(), customizeWidgets);
		saturationRow = addSliderRow(editorX, CUSTOMIZE_SLIDER_Y + CONTROL_SPACING, editorWidth,
				0, 100, hsl.saturation(),
				"screen.cobblemon_spawn_display.saturation", "%", value -> applySelectedColor(),
				() -> defaultSelectedHsl().saturation(), customizeWidgets);
		lightnessRow = addSliderRow(editorX, CUSTOMIZE_SLIDER_Y + CONTROL_SPACING * 2, editorWidth,
				0, 100, hsl.lightness(),
				"screen.cobblemon_spawn_display.lightness", "%", value -> applySelectedColor(),
				() -> defaultSelectedHsl().lightness(), customizeWidgets);

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
		if (highlightListsDirty) {
			refreshHighlightLists(false);
		}
		if (dirty && saveCountdown > 0 && --saveCountdown == 0) {
			saveNow();
		}
	}

	@Override
	public boolean mouseScrolled(
			double mouseX,
			double mouseY,
			double horizontalAmount,
			double verticalAmount
	) {
		if (page == Page.CUSTOMIZE
				&& customizeList != null
				&& customizeList.scrollWithWheel(mouseX, mouseY, verticalAmount)) {
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		updateResetButtonStates();
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 16, 0xFFFFFF);
		context.drawCenteredTextWithShadow(textRenderer, helperText, width / 2, 28, hasError ? 0xFF5555 : 0xC0C0C0);

		if (page == Page.CUSTOMIZE) {
			renderCustomizeSettings(context);
		} else if (page == Page.HIGHLIGHTS) {
			renderHighlightSettings(context);
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

	private void setPage(Page newPage) {
		page = newPage;
		if (newPage != Page.HIGHLIGHTS && pokemonSearchField != null) {
			pokemonSearchField.setFocused(false);
			selectedPokemonList.setFocused(false);
			availablePokemonList.setFocused(false);
		}
		updatePageVisibility();
	}

	private void updatePageVisibility() {
		boolean showGeneral = page == Page.GENERAL;
		boolean showHighlights = page == Page.HIGHLIGHTS;
		boolean showCustomize = page == Page.CUSTOMIZE;
		generalWidgets.forEach(widget -> setVisible(widget, showGeneral));
		highlightWidgets.forEach(widget -> setVisible(widget, showHighlights));
		customizeWidgets.forEach(widget -> setVisible(widget, showCustomize));
		updateCustomizeControlStates();
		generalTab.active = !showGeneral;
		highlightsTab.active = !showHighlights;
		customizeTab.active = !showCustomize;
	}

	private static void setVisible(ClickableWidget widget, boolean visible) {
		widget.visible = visible;
		widget.active = visible;
	}

	private void setCustomizeCategory(CustomizeCategory category) {
		customizeCategory = category;
		customizeList.setTargets(category);
		if (selectedCustomizeTarget.category() != category) {
			selectCustomizeTarget(category.firstTarget());
		} else {
			customizeList.selectTarget(selectedCustomizeTarget);
			updateCustomizeControlStates();
		}
	}

	private void selectCustomizeTarget(CustomizeTarget target) {
		selectedCustomizeTarget = target;
		if (!target.supportsBackground()) {
			selectedCustomizeRole = CustomizeRole.BORDER;
		}
		customizeList.selectTarget(target);
		updateCustomizeControlStates();
		loadSelectedColor();
	}

	private void selectCustomizeRole(CustomizeRole role) {
		if (role == CustomizeRole.BACKGROUND && !selectedCustomizeTarget.supportsBackground()) {
			return;
		}
		selectedCustomizeRole = role;
		updateCustomizeControlStates();
		loadSelectedColor();
	}

	private void updateCustomizeControlStates() {
		boolean showCustomize = page == Page.CUSTOMIZE;
		raritiesButton.active = showCustomize && customizeCategory != CustomizeCategory.RARITIES;
		aspectsButton.active = showCustomize && customizeCategory != CustomizeCategory.ASPECTS;
		boolean showRoleButtons = showCustomize && selectedCustomizeTarget.supportsBackground();
		backgroundRoleButton.visible = showRoleButtons;
		backgroundRoleButton.active = showRoleButtons && selectedCustomizeRole != CustomizeRole.BACKGROUND;
		borderRoleButton.visible = showRoleButtons;
		borderRoleButton.active = showRoleButtons && selectedCustomizeRole != CustomizeRole.BORDER;
	}

	private void loadSelectedColor() {
		Hsl hsl = rgbToHsl(selectedCustomizeTarget.getColor(
				SpawnDisplayConfig.get(),
				selectedCustomizeRole
		));
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
		selectedCustomizeTarget.setColor(SpawnDisplayConfig.get(), selectedCustomizeRole, color);
	}

	private Hsl defaultSelectedHsl() {
		return rgbToHsl(selectedCustomizeTarget.defaultColor(selectedCustomizeRole));
	}

	private void renderCustomizeSettings(DrawContext context) {
		int contentWidth = Math.min(420, this.width - 24);
		int contentX = (this.width - contentWidth) / 2;
		int customizeListWidth = Math.min(168, Math.max(120, contentWidth * 2 / 5));
		int editorX = contentX + customizeListWidth + CUSTOMIZE_COLUMN_GAP;
		int editorWidth = contentWidth - customizeListWidth - CUSTOMIZE_COLUMN_GAP;
		int color = selectedCustomizeTarget.getColor(SpawnDisplayConfig.get(), selectedCustomizeRole);
		String hex = SpawnDisplayConfig.colorToHex(color);
		context.drawCenteredTextWithShadow(
				textRenderer,
				selectedCustomizeTarget.label(),
				editorX + editorWidth / 2,
				68,
				0xFFFFFFFF
		);
		Text roleText = Text.translatable(selectedCustomizeRole.translationKey()).copy().append("  " + hex);
		int roleY = selectedCustomizeTarget.supportsBackground() ? 105 : 88;
		context.drawCenteredTextWithShadow(
				textRenderer,
				roleText,
				editorX + editorWidth / 2,
				roleY,
				0xFFD0D0D0
		);
	}

	private void renderCustomizePreview(
			DrawContext context,
			CustomizeTarget target,
			int x,
			int y,
			float tickDelta
	) {
		SpawnDisplayConfig config = SpawnDisplayConfig.get();
		boolean aspect = target.category() == CustomizeCategory.ASPECTS;
		int backgroundColor = aspect ? ASPECT_PREVIEW_BACKGROUND_COLOR : target.getBackgroundColor(config);
		int borderColor = target.getBorderColor(config);

		context.fill(
				x + 1,
				y + 1,
				x + PREVIEW_TILE_SIZE - 1,
				y + PREVIEW_TILE_SIZE - 1,
				config.getBackgroundColor(backgroundColor)
		);
		if (aspect) {
			renderPreviewGradientBorder(
					context,
					x,
					y,
					ASPECT_PREVIEW_BORDER_COLOR,
					borderColor,
					tickDelta
			);
		} else {
			renderPreviewBorder(context, x, y, borderColor);
		}

		if (aspect) {
			int badgeWidth = textRenderer.getWidth(target.badge());
			context.drawText(
					textRenderer,
					target.badge(),
					x + PREVIEW_TILE_SIZE - badgeWidth - 2,
					y + 2,
					0xFF000000 | borderColor,
					true
			);
		} else {
			context.drawText(textRenderer, target.badge(), x + 2, y + 2, 0xFF000000 | borderColor, true);
		}
	}

	private static void renderPreviewBorder(DrawContext context, int x, int y, int color) {
		int argb = 0xFF000000 | color;
		context.fill(x, y, x + PREVIEW_TILE_SIZE, y + 1, argb);
		context.fill(x, y + PREVIEW_TILE_SIZE - 1, x + PREVIEW_TILE_SIZE, y + PREVIEW_TILE_SIZE, argb);
		context.fill(x, y + 1, x + 1, y + PREVIEW_TILE_SIZE - 1, argb);
		context.fill(x + PREVIEW_TILE_SIZE - 1, y + 1, x + PREVIEW_TILE_SIZE, y + PREVIEW_TILE_SIZE - 1, argb);
	}

	private static void renderPreviewGradientBorder(
			DrawContext context,
			int x,
			int y,
			int baseColor,
			int aspectColor,
			float tickDelta
	) {
		MinecraftClient client = MinecraftClient.getInstance();
		long time = client.world == null ? System.currentTimeMillis() / 50L : client.world.getTime();
		float progress = SpawnDisplayConfig.get().shouldDisableSpriteAnimations()
				? 0.0F
				: ((time % 80L) + tickDelta) / 80.0F;
		int edgeLength = PREVIEW_TILE_SIZE - 1;
		int perimeter = edgeLength * 4;
		for (int index = 0; index < perimeter; index++) {
			float position = index / (float) perimeter;
			float gradientPosition = position - progress;
			gradientPosition -= (float) Math.floor(gradientPosition);
			float scaledPosition = gradientPosition * 2.0F;
			int firstColor = scaledPosition < 1.0F ? baseColor : aspectColor;
			int secondColor = scaledPosition < 1.0F ? aspectColor : baseColor;
			float blend = scaledPosition - (float) Math.floor(scaledPosition);
			int color = 0xFF000000 | blendPreviewColor(firstColor, secondColor, blend);
			int pixelX;
			int pixelY;
			if (index < PREVIEW_TILE_SIZE) {
				pixelX = x + index;
				pixelY = y;
			} else if (index < PREVIEW_TILE_SIZE + edgeLength) {
				pixelX = x + edgeLength;
				pixelY = y + index - PREVIEW_TILE_SIZE + 1;
			} else if (index < PREVIEW_TILE_SIZE + edgeLength * 2) {
				pixelX = x + edgeLength - 1 - (index - PREVIEW_TILE_SIZE - edgeLength);
				pixelY = y + edgeLength;
			} else {
				pixelX = x;
				pixelY = y + edgeLength - 1 - (index - PREVIEW_TILE_SIZE - edgeLength * 2);
			}
			context.fill(pixelX, pixelY, pixelX + 1, pixelY + 1, color);
		}
	}

	private static int blendPreviewColor(int first, int second, float amount) {
		int red = blendPreviewChannel(first >> 16, second >> 16, amount);
		int green = blendPreviewChannel(first >> 8, second >> 8, amount);
		int blue = blendPreviewChannel(first, second, amount);
		return red << 16 | green << 8 | blue;
	}

	private static int blendPreviewChannel(int first, int second, float amount) {
		int firstChannel = first & 0xFF;
		return Math.round(firstChannel + ((second & 0xFF) - firstChannel) * amount);
	}

	private void renderHighlightSettings(DrawContext context) {
		int contentWidth = Math.min(420, this.width - 24);
		int contentX = (this.width - contentWidth) / 2;
		Set<String> highlighted = SpawnDisplayConfig.get().getHighlightedSpecies();
		context.drawTextWithShadow(
				textRenderer,
				Text.translatable("screen.cobblemon_spawn_display.highlight_summary", highlighted.size()),
				contentX,
				HIGHLIGHT_SUMMARY_Y,
				0xFFFFFFFF
		);
		context.drawTextWithShadow(
				textRenderer,
				Text.translatable("screen.cobblemon_spawn_display.highlight_search"),
				contentX + (contentWidth - CONTROL_GAP) / 2 + CONTROL_GAP,
				HIGHLIGHT_SUMMARY_Y,
				0xFFFFFFFF
		);

		if (selectedPokemonList.isEmpty()) {
			context.drawCenteredTextWithShadow(
					textRenderer,
					Text.translatable("screen.cobblemon_spawn_display.highlight_none"),
					selectedPokemonList.getX() + selectedPokemonList.getWidth() / 2,
					selectedPokemonList.getY() + 8,
					0xFFFFC07A
			);
		}

		if (availablePokemonList.isEmpty()) {
			Text emptyText;
			if (pokemonOptions.isEmpty()) {
				emptyText = Text.translatable("screen.cobblemon_spawn_display.highlight_unavailable");
			} else if (pokemonSearchField.getText().isBlank()
					&& selectedPokemonOptions().size() >= pokemonOptions.size()) {
				emptyText = Text.translatable("screen.cobblemon_spawn_display.highlight_all_selected");
			} else {
				emptyText = Text.translatable("screen.cobblemon_spawn_display.highlight_no_matches");
			}
			context.drawCenteredTextWithShadow(
					textRenderer,
					emptyText,
					availablePokemonList.getX() + availablePokemonList.getWidth() / 2,
					availablePokemonList.getY() + 8,
					0xFFFFC07A
			);
		}
	}

	private List<SpeciesOption> loadPokemonOptions() {
		try {
			return PokemonSpecies.getImplemented().stream()
					.filter(species -> species.getResourceIdentifier() != null)
					.map(SpeciesOption::fromSpecies)
					.sorted(Comparator
							.comparingInt(SpeciesOption::dexSortNumber)
							.thenComparing(option -> option.identifier().toString()))
					.toList();
		} catch (RuntimeException exception) {
			CobblemonSpawnDisplayClient.LOGGER.warn("Could not populate the highlight Pokemon list", exception);
			return List.of();
		}
	}

	private void filterPokemonOptions(String search) {
		String query = search.trim().toLowerCase(Locale.ROOT);
		availablePokemonList.setOptions(pokemonOptions.stream()
				.filter(option -> !SpawnDisplayConfig.get().shouldHighlight(option.identifier()))
				.filter(option -> option.matches(query))
				.toList(), true);
	}

	private List<SpeciesOption> selectedPokemonOptions() {
		Set<String> highlighted = SpawnDisplayConfig.get().getHighlightedSpecies();
		List<SpeciesOption> selected = new ArrayList<>();
		Set<String> knownSpecies = new HashSet<>();
		for (SpeciesOption option : pokemonOptions) {
			String speciesPath = option.identifier().getPath();
			if (knownSpecies.add(speciesPath) && highlighted.contains(speciesPath)) {
				selected.add(option);
			}
		}
		highlighted.stream()
				.filter(species -> !knownSpecies.contains(species))
				.sorted()
				.map(species -> new SpeciesOption(
						Identifier.of("cobblemon", species),
						0,
						Text.literal(species.replace('_', ' ')),
						species.replace('_', ' ').toLowerCase(Locale.ROOT)
				))
				.forEach(selected::add);
		return selected;
	}

	private void refreshHighlightLists(boolean resetScroll) {
		selectedPokemonList.setOptions(selectedPokemonOptions(), resetScroll);
		String query = pokemonSearchField.getText().trim().toLowerCase(Locale.ROOT);
		availablePokemonList.setOptions(pokemonOptions.stream()
				.filter(option -> !SpawnDisplayConfig.get().shouldHighlight(option.identifier()))
				.filter(option -> option.matches(query))
				.toList(), resetScroll);
		highlightListsDirty = false;
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
		config.setHighlightedPokemon("");
		highlightListsDirty = true;
		for (Rarity rarity : Rarity.values()) {
			config.setRarityBackgroundColor(rarity, SpawnDisplayConfig.defaultRarityBackgroundColor(rarity));
			config.setRarityBorderColor(rarity, SpawnDisplayConfig.defaultRarityBorderColor(rarity));
		}
		config.setShinyColor(SpawnDisplayConfig.defaultShinyColor());
		config.setAlphaColor(SpawnDisplayConfig.defaultAlphaColor());
		config.setTeraColor(SpawnDisplayConfig.defaultTeraColor());
		config.setLegendaryBackgroundColor(SpawnDisplayConfig.defaultLegendaryBackgroundColor());
		config.setLegendaryBorderColor(SpawnDisplayConfig.defaultLegendaryBorderColor());
		config.setMythicalBackgroundColor(SpawnDisplayConfig.defaultMythicalBackgroundColor());
		config.setMythicalBorderColor(SpawnDisplayConfig.defaultMythicalBorderColor());
		config.setFossilAspectColor(SpawnDisplayConfig.defaultFossilAspectColor());
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
		HIGHLIGHTS,
		CUSTOMIZE
	}

	private enum CustomizeCategory {
		RARITIES,
		ASPECTS;

		private CustomizeTarget firstTarget() {
			return this == RARITIES ? CustomizeTarget.COMMON : CustomizeTarget.SHINY;
		}
	}

	private enum CustomizeRole {
		BACKGROUND("screen.cobblemon_spawn_display.background"),
		BORDER("screen.cobblemon_spawn_display.border");

		private final String translationKey;

		CustomizeRole(String translationKey) {
			this.translationKey = translationKey;
		}

		private String translationKey() {
			return translationKey;
		}
	}

	private enum CustomizeTarget {
		COMMON(CustomizeCategory.RARITIES, "screen.cobblemon_spawn_display.common", "C"),
		UNCOMMON(CustomizeCategory.RARITIES, "screen.cobblemon_spawn_display.uncommon", "U"),
		RARE(CustomizeCategory.RARITIES, "screen.cobblemon_spawn_display.rare", "R"),
		ULTRA_RARE(CustomizeCategory.RARITIES, "screen.cobblemon_spawn_display.ultra_rare", "UR"),
		PARADOX(CustomizeCategory.RARITIES, "screen.cobblemon_spawn_display.paradox", "P"),
		ULTRA_BEAST(CustomizeCategory.RARITIES, "screen.cobblemon_spawn_display.ultra_beast", "UB"),
		LEGENDARY(CustomizeCategory.RARITIES, "screen.cobblemon_spawn_display.legendary", "L"),
		MYTHICAL(CustomizeCategory.RARITIES, "screen.cobblemon_spawn_display.mythical", "M"),
		SHINY(CustomizeCategory.ASPECTS, "screen.cobblemon_spawn_display.shiny", "S"),
		ALPHA(CustomizeCategory.ASPECTS, "screen.cobblemon_spawn_display.alpha", "A"),
		TERA(CustomizeCategory.ASPECTS, "screen.cobblemon_spawn_display.tera", "T"),
		FOSSIL_ASPECT(CustomizeCategory.ASPECTS, "screen.cobblemon_spawn_display.fossil", "F");

		private final CustomizeCategory category;
		private final String labelKey;
		private final String badge;

		CustomizeTarget(CustomizeCategory category, String labelKey, String badge) {
			this.category = category;
			this.labelKey = labelKey;
			this.badge = badge;
		}

		private CustomizeCategory category() {
			return category;
		}

		private boolean supportsBackground() {
			return category == CustomizeCategory.RARITIES;
		}

		private Text label() {
			return Text.translatable(labelKey);
		}

		private String badge() {
			return badge;
		}

		private int getColor(SpawnDisplayConfig config, CustomizeRole role) {
			return role == CustomizeRole.BACKGROUND ? getBackgroundColor(config) : getBorderColor(config);
		}

		private void setColor(SpawnDisplayConfig config, CustomizeRole role, int color) {
			if (role == CustomizeRole.BACKGROUND) {
				setBackgroundColor(config, color);
			} else {
				setBorderColor(config, color);
			}
		}

		private int defaultColor(CustomizeRole role) {
			return role == CustomizeRole.BACKGROUND ? defaultBackgroundColor() : defaultBorderColor();
		}

		private int getBackgroundColor(SpawnDisplayConfig config) {
			return switch (this) {
				case COMMON, UNCOMMON, RARE, ULTRA_RARE -> config.getRarityBackgroundColor(standardRarity());
				case LEGENDARY -> config.getLegendaryBackgroundColor();
				case MYTHICAL -> config.getMythicalBackgroundColor();
				case ULTRA_BEAST -> config.getUltraBeastBackgroundColor();
				case PARADOX -> config.getParadoxBackgroundColor();
				case SHINY, ALPHA, TERA, FOSSIL_ASPECT ->
						throw new IllegalStateException(this + " does not have a background color");
			};
		}

		private int getBorderColor(SpawnDisplayConfig config) {
			return switch (this) {
				case COMMON, UNCOMMON, RARE, ULTRA_RARE -> config.getRarityBorderColor(standardRarity());
				case LEGENDARY -> config.getLegendaryBorderColor();
				case MYTHICAL -> config.getMythicalBorderColor();
				case ULTRA_BEAST -> config.getUltraBeastBorderColor();
				case PARADOX -> config.getParadoxBorderColor();
				case SHINY -> config.getShinyColor();
				case ALPHA -> config.getAlphaColor();
				case TERA -> config.getTeraColor();
				case FOSSIL_ASPECT -> config.getFossilAspectColor();
			};
		}

		private void setBackgroundColor(SpawnDisplayConfig config, int color) {
			switch (this) {
				case COMMON, UNCOMMON, RARE, ULTRA_RARE -> config.setRarityBackgroundColor(standardRarity(), color);
				case LEGENDARY -> config.setLegendaryBackgroundColor(color);
				case MYTHICAL -> config.setMythicalBackgroundColor(color);
				case ULTRA_BEAST -> config.setUltraBeastBackgroundColor(color);
				case PARADOX -> config.setParadoxBackgroundColor(color);
				case SHINY, ALPHA, TERA, FOSSIL_ASPECT ->
						throw new IllegalStateException(this + " does not have a background color");
			}
		}

		private void setBorderColor(SpawnDisplayConfig config, int color) {
			switch (this) {
				case COMMON, UNCOMMON, RARE, ULTRA_RARE -> config.setRarityBorderColor(standardRarity(), color);
				case LEGENDARY -> config.setLegendaryBorderColor(color);
				case MYTHICAL -> config.setMythicalBorderColor(color);
				case ULTRA_BEAST -> config.setUltraBeastBorderColor(color);
				case PARADOX -> config.setParadoxBorderColor(color);
				case SHINY -> config.setShinyColor(color);
				case ALPHA -> config.setAlphaColor(color);
				case TERA -> config.setTeraColor(color);
				case FOSSIL_ASPECT -> config.setFossilAspectColor(color);
			}
		}

		private int defaultBackgroundColor() {
			return switch (this) {
				case COMMON, UNCOMMON, RARE, ULTRA_RARE ->
						SpawnDisplayConfig.defaultRarityBackgroundColor(standardRarity());
				case LEGENDARY -> SpawnDisplayConfig.defaultLegendaryBackgroundColor();
				case MYTHICAL -> SpawnDisplayConfig.defaultMythicalBackgroundColor();
				case ULTRA_BEAST -> SpawnDisplayConfig.defaultUltraBeastBackgroundColor();
				case PARADOX -> SpawnDisplayConfig.defaultParadoxBackgroundColor();
				case SHINY, ALPHA, TERA, FOSSIL_ASPECT ->
						throw new IllegalStateException(this + " does not have a background color");
			};
		}

		private int defaultBorderColor() {
			return switch (this) {
				case COMMON, UNCOMMON, RARE, ULTRA_RARE ->
						SpawnDisplayConfig.defaultRarityBorderColor(standardRarity());
				case LEGENDARY -> SpawnDisplayConfig.defaultLegendaryBorderColor();
				case MYTHICAL -> SpawnDisplayConfig.defaultMythicalBorderColor();
				case ULTRA_BEAST -> SpawnDisplayConfig.defaultUltraBeastBorderColor();
				case PARADOX -> SpawnDisplayConfig.defaultParadoxBorderColor();
				case SHINY -> SpawnDisplayConfig.defaultShinyColor();
				case ALPHA -> SpawnDisplayConfig.defaultAlphaColor();
				case TERA -> SpawnDisplayConfig.defaultTeraColor();
				case FOSSIL_ASPECT -> SpawnDisplayConfig.defaultFossilAspectColor();
			};
		}

		private Rarity standardRarity() {
			return switch (this) {
				case COMMON -> Rarity.COMMON;
				case UNCOMMON -> Rarity.UNCOMMON;
				case RARE -> Rarity.RARE;
				case ULTRA_RARE -> Rarity.ULTRA_RARE;
				case LEGENDARY, MYTHICAL, ULTRA_BEAST, PARADOX,
						SHINY, ALPHA, TERA, FOSSIL_ASPECT ->
						throw new IllegalStateException(this + " is not a standard rarity");
			};
		}
	}

	private record Hsl(int hue, int saturation, int lightness) {
	}

	private record SpeciesOption(
			Identifier identifier,
			int dexNumber,
			Text displayName,
			String searchText
	) {
		private static SpeciesOption fromSpecies(Species species) {
			Identifier identifier = species.getResourceIdentifier();
			int dexNumber = species.getNationalPokedexNumber();
			Text displayName = species.getTranslatedName();
			String searchText = (displayName.getString()
					+ " " + identifier.getPath().replace('_', ' ')
					+ " " + dexNumber
					+ " #" + dexNumber).toLowerCase(Locale.ROOT);
			return new SpeciesOption(identifier, dexNumber, displayName, searchText);
		}

		private int dexSortNumber() {
			return dexNumber > 0 ? dexNumber : Integer.MAX_VALUE;
		}

		private String dexLabel() {
			return dexNumber > 0 ? "#" + dexNumber : "#?";
		}

		private boolean matches(String query) {
			return query.isEmpty() || searchText.contains(query);
		}
	}

	private final class CustomizeListWidget extends AlwaysSelectedEntryListWidget<CustomizeEntry> {
		private CustomizeListWidget(MinecraftClient client, int width, int height, int y, int itemHeight) {
			super(client, width, height, y, itemHeight);
			centerListVertically = false;
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			return isInteractive() && super.mouseClicked(mouseX, mouseY, button);
		}

		@Override
		public boolean mouseReleased(double mouseX, double mouseY, int button) {
			return isInteractive() && super.mouseReleased(mouseX, mouseY, button);
		}

		@Override
		public boolean mouseDragged(
				double mouseX,
				double mouseY,
				int button,
				double deltaX,
				double deltaY
		) {
			return isInteractive() && super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
		}

		@Override
		public boolean mouseScrolled(
				double mouseX,
				double mouseY,
				double horizontalAmount,
				double verticalAmount
		) {
			return scrollWithWheel(mouseX, mouseY, verticalAmount);
		}

		private boolean scrollWithWheel(double mouseX, double mouseY, double verticalAmount) {
			if (!isInteractive() || !isMouseOver(mouseX, mouseY) || verticalAmount == 0.0) {
				return false;
			}
			setScrollAmount(getScrollAmount() - verticalAmount * CUSTOMIZE_ROW_HEIGHT);
			return true;
		}

		@Override
		public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
			return isInteractive() && super.keyPressed(keyCode, scanCode, modifiers);
		}

		private boolean isInteractive() {
			return page == Page.CUSTOMIZE && visible && active;
		}

		private void setTargets(CustomizeCategory category) {
			replaceEntries(List.of(CustomizeTarget.values()).stream()
					.filter(target -> target.category() == category)
					.map(CustomizeEntry::new)
					.toList());
			setScrollAmount(0.0);
			selectTarget(selectedCustomizeTarget);
		}

		private void selectTarget(CustomizeTarget target) {
			for (CustomizeEntry entry : children()) {
				if (entry.target == target) {
					setSelected(entry);
					return;
				}
			}
			setSelected(null);
		}

		@Override
		protected boolean isSelectedEntry(int index) {
			return false;
		}

		@Override
		public int getRowWidth() {
			return getWidth() - 12;
		}

		@Override
		protected int getScrollbarX() {
			return getRight() - 6;
		}
	}

	private final class CustomizeEntry extends AlwaysSelectedEntryListWidget.Entry<CustomizeEntry> {
		private final CustomizeTarget target;

		private CustomizeEntry(CustomizeTarget target) {
			this.target = target;
		}

		@Override
		public void render(
				DrawContext context,
				int index,
				int y,
				int x,
				int entryWidth,
				int entryHeight,
				int mouseX,
				int mouseY,
				boolean hovered,
				float tickDelta
		) {
			boolean selected = target == selectedCustomizeTarget;
			if (selected) {
				context.fill(x - 2, y, x + entryWidth + 2, y + entryHeight - 1, 0x50000000);
			} else if (hovered) {
				context.fill(x - 2, y, x + entryWidth + 2, y + entryHeight - 1, 0x30000000);
			}
			context.fill(x, y + entryHeight - 1, x + entryWidth, y + entryHeight, 0x30FFFFFF);
			renderCustomizePreview(context, target, x + 1, y + 1, tickDelta);

			int nameX = x + PREVIEW_TILE_SIZE + 7;
			int availableNameWidth = Math.max(0, x + entryWidth - nameX - 3);
			String visibleName = textRenderer.trimToWidth(target.label().getString(), availableNameWidth);
			context.drawText(
					textRenderer,
					visibleName,
					nameX,
					y + 12,
					0xFFFFFFFF,
					false
			);
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
				return false;
			}
			selectCustomizeTarget(target);
			return true;
		}

		@Override
		public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
			if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_SPACE) {
				selectCustomizeTarget(target);
				return true;
			}
			return false;
		}

		@Override
		public Text getNarration() {
			return target.label().copy().append(target == selectedCustomizeTarget ? ". Selected" : "");
		}
	}

	private final class PokemonListWidget extends AlwaysSelectedEntryListWidget<PokemonEntry> {
		private PokemonListWidget(MinecraftClient client, int width, int height, int y, int itemHeight) {
			super(client, width, height, y, itemHeight);
			centerListVertically = false;
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			return isInteractive() && super.mouseClicked(mouseX, mouseY, button);
		}

		@Override
		public boolean mouseReleased(double mouseX, double mouseY, int button) {
			return isInteractive() && super.mouseReleased(mouseX, mouseY, button);
		}

		@Override
		public boolean mouseDragged(
				double mouseX,
				double mouseY,
				int button,
				double deltaX,
				double deltaY
		) {
			return isInteractive() && super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
		}

		@Override
		public boolean mouseScrolled(
				double mouseX,
				double mouseY,
				double horizontalAmount,
				double verticalAmount
		) {
			return isInteractive()
					&& super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
		}

		@Override
		public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
			return isInteractive() && super.keyPressed(keyCode, scanCode, modifiers);
		}

		private boolean isInteractive() {
			return page == Page.HIGHLIGHTS && visible && active;
		}

		private void setOptions(List<SpeciesOption> options, boolean resetScroll) {
			double scrollAmount = getScrollAmount();
			replaceEntries(options.stream().map(PokemonEntry::new).toList());
			setScrollAmount(resetScroll ? 0.0 : scrollAmount);
		}

		private boolean isEmpty() {
			return children().isEmpty();
		}

		@Override
		public int getRowWidth() {
			return getWidth() - 12;
		}

		@Override
		protected int getScrollbarX() {
			return getRight() - 6;
		}
	}

	private final class PokemonEntry extends AlwaysSelectedEntryListWidget.Entry<PokemonEntry> {
		private final SpeciesOption option;

		private PokemonEntry(SpeciesOption option) {
			this.option = option;
		}

		@Override
		public void render(
				DrawContext context,
				int index,
				int y,
				int x,
				int entryWidth,
				int entryHeight,
				int mouseX,
				int mouseY,
				boolean hovered,
				float tickDelta
		) {
			boolean highlighted = SpawnDisplayConfig.get().shouldHighlight(option.identifier());
			if (highlighted) {
				context.fill(x - 2, y, x + entryWidth + 2, y + entryHeight - 1, 0xA04A2500);
			} else if (hovered) {
				context.fill(x - 2, y, x + entryWidth + 2, y + entryHeight - 1, 0x60000000);
			}
			context.fill(x, y + entryHeight - 1, x + entryWidth, y + entryHeight, 0x30FFFFFF);

			String action = Text.translatable(highlighted
					? "screen.cobblemon_spawn_display.highlight_remove"
					: "screen.cobblemon_spawn_display.highlight_add").getString();
			int actionX = x + entryWidth - textRenderer.getWidth(action) - 4;
			int nameX = x + textRenderer.getWidth("#0000") + 10;
			int availableNameWidth = Math.max(0, actionX - nameX - 6);
			String visibleName = textRenderer.trimToWidth(option.displayName().getString(), availableNameWidth);

			context.drawText(
					textRenderer,
					option.dexLabel(),
					x + 4,
					y + 6,
					highlighted ? 0xFFFFC07A : 0xFFB8B8B8,
					false
			);
			context.drawText(textRenderer, visibleName, nameX, y + 6, 0xFFFFFFFF, false);
			context.drawText(
					textRenderer,
					action,
					actionX,
					y + 6,
					highlighted ? 0xFFFFC07A : 0xFFFFFFFF,
					false
			);
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
				return false;
			}
			toggleHighlight();
			return true;
		}

		@Override
		public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
			if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_SPACE) {
				toggleHighlight();
				return true;
			}
			return false;
		}

		@Override
		public Text getNarration() {
			boolean highlighted = SpawnDisplayConfig.get().shouldHighlight(option.identifier());
			return Text.literal(option.dexLabel() + " ")
					.append(option.displayName().copy())
					.append(". ")
					.append(Text.translatable(highlighted
							? "screen.cobblemon_spawn_display.highlight_remove"
							: "screen.cobblemon_spawn_display.highlight_add"));
		}

		private void toggleHighlight() {
			SpawnDisplayConfig.get().toggleHighlightedPokemon(option.identifier());
			highlightListsDirty = true;
			settingsChanged();
		}
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
