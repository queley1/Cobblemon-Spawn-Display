package com.cobblemonspawndisplay;

import com.cobblemon.mod.common.client.gui.PokemonGuiUtilsKt;
import com.cobblemon.mod.common.client.gui.ProfileTransformType;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SpawnHud {
	private static final int MARGIN = 4;
	private static final int BASE_TILE_SIZE = 30;
	private static final int BASE_FOOTER_Y_OFFSET = 20;
	private static final int SETTINGS_BACKGROUND_COLOR = 0xAAAAAA;
	private static final int SETTINGS_BORDER_COLOR = 0xFFAAAAAA;
	private static final int SETTINGS_HOVER_BORDER_COLOR = 0xFFFFFFFF;
	private static final int BORDER_ANIMATION_PERIOD_TICKS = 80;
	private static final int[] PINNED_BORDER_COLORS = {0xFFFF6A00, 0xFFFFB347, 0xFFFF8C00};
	private static final char SPECIAL_SKIN_BADGE = '\uE000';
	private static final int SPECIAL_SKIN_BADGE_SIZE = 9;
	private static final Identifier SPECIAL_SKIN_BADGE_TEXTURE = Identifier.of(
			"cobblemon_spawn_display",
			"textures/gui/special_skin_star.png"
	);
	private static final String[] DIRECTION_ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
	private static final Map<UUID, FloatingState> AVATAR_STATES = new HashMap<>();
	private static final Map<UUID, Long> PINNED_ENTRIES = new HashMap<>();
	private static final Set<Identifier> REPORTED_AVATAR_FAILURES = new HashSet<>();
	private static List<Entry> entries = List.of();
	private static long nextPinnedOrder;
	private static int refreshCountdown;

	private SpawnHud() {
	}

	public static void initialize() {
		ClientTickEvents.END_CLIENT_TICK.register(SpawnHud::tick);
	}

	public static void requestRefresh() {
		refreshCountdown = 0;
	}

	private static void tick(MinecraftClient client) {
		if (client.world == null || client.player == null) {
			entries = List.of();
			AVATAR_STATES.clear();
			PINNED_ENTRIES.clear();
			nextPinnedOrder = 0;
			refreshCountdown = 0;
			return;
		}

		if (refreshCountdown-- > 0) {
			return;
		}
		SpawnDisplayConfig config = SpawnDisplayConfig.get();
		refreshCountdown = config.getUpdateIntervalTicks() - 1;

		List<Entry> discovered = new ArrayList<>();
		Set<UUID> visiblePokemon = new HashSet<>();
		for (Entity entity : client.world.getEntities()) {
			if (!(entity instanceof PokemonEntity pokemonEntity) || pokemonEntity.isRemoved()) {
				continue;
			}

			try {
				Pokemon pokemon = pokemonEntity.getPokemon();
				Identifier species = pokemon.getSpecies().getResourceIdentifier();
				Rarity rarity = RarityIndex.findLowest(
						pokemon.getSpecies().getNationalPokedexNumber(),
						species
				).orElse(null);
				boolean shiny = pokemon.getShiny();
				boolean alpha = pokemon.isAlpha();
				boolean tera = SpecialAppearance.isTera(pokemon);
				boolean specialSkin = SpecialAppearance.hasSpecialSkin(pokemon);
				boolean fossil = SpecialClassification.isFossil(pokemon);
				boolean highlighted = config.shouldHighlight(species);
				SpecialClassification specialClassification = rarity == null
						? SpecialClassification.fromPokemon(pokemon)
						: null;
				if (rarity == null && specialClassification == null) {
					rarity = RarityIndex.findEvolutionFallback(species).orElse(null);
				}
				if (rarity == null && specialClassification == null && fossil) {
					specialClassification = SpecialClassification.FOSSIL;
				}
				boolean fossilStatus = fossil && specialClassification != SpecialClassification.FOSSIL;
				if (rarity == Rarity.COMMON
						&& !shiny
						&& !alpha
						&& !tera
						&& !specialSkin
						&& !highlighted
						&& !config.shouldShowCommons()) {
					continue;
				}

				UUID entityId = pokemonEntity.getUuid();
				visiblePokemon.add(entityId);
				FloatingState avatarState = AVATAR_STATES.computeIfAbsent(entityId, ignored -> new FloatingState());
				double distanceSquared = distanceSquared(
						client.player,
						pokemonEntity,
						config.shouldUseHorizontalDistance()
				);

				discovered.add(new Entry(
						pokemonEntity,
						pokemon.asRenderablePokemon(),
						avatarState,
						species,
						highlighted,
						rarity,
						specialClassification,
						shiny,
						alpha,
						tera,
						specialSkin,
						fossilStatus,
						distanceSquared
				));
			} catch (RuntimeException exception) {
				CobblemonSpawnDisplayClient.LOGGER.debug(
						"Skipping a Pokemon entity whose client data is not ready yet",
						exception
				);
			}
		}

		AVATAR_STATES.keySet().retainAll(visiblePokemon);
		PINNED_ENTRIES.keySet().retainAll(visiblePokemon);
		discovered.sort(entryComparator());
		entries = List.copyOf(discovered);
	}

	private static Comparator<Entry> entryComparator() {
		return Comparator.comparing(
				(Entry entry) -> isPinned(entry),
				Comparator.reverseOrder()
		)
				.thenComparing(Comparator.comparingLong(
						(Entry entry) -> PINNED_ENTRIES.getOrDefault(entry.entity().getUuid(), Long.MIN_VALUE)
				).reversed())
				.thenComparing(Comparator.comparing(
						(Entry entry) -> entry.hasClassification(SpecialClassification.MYTHICAL),
						Comparator.reverseOrder()
				)
						.thenComparing(
								entry -> entry.hasClassification(SpecialClassification.LEGENDARY),
								Comparator.reverseOrder()
						)
						.thenComparing(
								entry -> entry.hasClassification(SpecialClassification.ULTRA_BEAST),
								Comparator.reverseOrder()
						)
						.thenComparing(
								entry -> entry.hasClassification(SpecialClassification.PARADOX),
								Comparator.reverseOrder()
						)
						.thenComparing(Entry::alpha, Comparator.reverseOrder())
						.thenComparing(Entry::shiny, Comparator.reverseOrder())
						.thenComparing(Entry::isFossil, Comparator.reverseOrder())
						.thenComparing(Entry::tera, Comparator.reverseOrder())
						.thenComparing(Comparator.comparingInt(Entry::rarityRank).reversed())
						.thenComparingDouble(Entry::distanceSquared));
	}

	public static boolean handleClick(double mouseX, double mouseY) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.player == null || client.options.hudHidden) {
			return false;
		}

		SpawnDisplayConfig config = SpawnDisplayConfig.get();
		GridSlot settingsSlot = gridSlot(entries.size(), config);
		int settingsButtonSize = settingsButtonSize(config.getTileSize());
		if (contains(settingsSlot, settingsButtonSize, mouseX, mouseY)) {
			if (!(client.currentScreen instanceof SpawnDisplayConfigScreen)) {
				client.setScreen(new SpawnDisplayConfigScreen(client.currentScreen));
			}
			return true;
		}

		Entry entry = entryAt(mouseX, mouseY, config);
		if (entry == null) {
			return false;
		}

		if (entry.highlighted()) {
			return true;
		}

		UUID entityId = entry.entity().getUuid();
		if (PINNED_ENTRIES.remove(entityId) == null) {
			PINNED_ENTRIES.put(entityId, nextPinnedOrder++);
		}

		List<Entry> reordered = new ArrayList<>(entries);
		reordered.sort(entryComparator());
		entries = List.copyOf(reordered);
		return true;
	}

	public static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.player == null || client.options.hudHidden) {
			return;
		}

		float tickDelta = tickCounter.getTickDelta(false);
		SpawnDisplayConfig config = SpawnDisplayConfig.get();
		for (int index = 0; index < entries.size(); index++) {
			GridSlot slot = gridSlot(index, config);
			renderTile(context, client, entries.get(index), tickDelta, config, slot.x(), slot.y());
		}

		GridSlot settingsSlot = gridSlot(entries.size(), config);
		renderSettingsButton(context, client, config, settingsSlot);
	}

	public static void renderTooltip(DrawContext context, int mouseX, int mouseY) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.player == null || client.options.hudHidden) {
			return;
		}

		SpawnDisplayConfig config = SpawnDisplayConfig.get();
		Entry entry = entryAt(mouseX, mouseY, config);
		if (entry != null) {
			context.drawTooltip(client.textRenderer, entry.entity().getName(), mouseX, mouseY);
			return;
		}

		GridSlot settingsSlot = gridSlot(entries.size(), config);
		if (contains(settingsSlot, settingsButtonSize(config.getTileSize()), mouseX, mouseY)) {
			context.drawTooltip(
					client.textRenderer,
					Text.translatable("key.cobblemon_spawn_display.open_settings"),
					mouseX,
					mouseY
			);
		}
	}

	private static void renderSettingsButton(
			DrawContext context,
			MinecraftClient client,
			SpawnDisplayConfig config,
			GridSlot slot
	) {
		int buttonSize = settingsButtonSize(config.getTileSize());
		boolean hovered = false;
		if (client.currentScreen != null) {
			double mouseX = client.mouse.getX() * client.getWindow().getScaledWidth()
					/ client.getWindow().getWidth();
			double mouseY = client.mouse.getY() * client.getWindow().getScaledHeight()
					/ client.getWindow().getHeight();
			hovered = contains(slot, buttonSize, mouseX, mouseY);
		}

		context.fill(
				slot.x() + 1,
				slot.y() + 1,
				slot.x() + buttonSize - 1,
				slot.y() + buttonSize - 1,
				config.getBackgroundColor(SETTINGS_BACKGROUND_COLOR)
		);
		renderBorder(
				context,
				slot.x(),
				slot.y(),
				buttonSize,
				hovered ? SETTINGS_HOVER_BORDER_COLOR : SETTINGS_BORDER_COLOR
		);
		renderSettingsIcon(context, slot.x(), slot.y(), buttonSize, hovered);
	}

	private static void renderSettingsIcon(
			DrawContext context,
			int x,
			int y,
			int buttonSize,
			boolean hovered
	) {
		int iconWidth = Math.max(6, Math.min(10, buttonSize - 4));
		int iconX = x + (buttonSize - iconWidth) / 2;
		int iconY = y + (buttonSize - 7) / 2;
		int lineColor = hovered ? 0xFFFFFFFF : 0xFFD0D0D0;
		int[] knobOffsets = {1, iconWidth - 3, Math.max(1, iconWidth / 2 - 1)};

		for (int index = 0; index < knobOffsets.length; index++) {
			int lineY = iconY + index * 3;
			context.fill(iconX, lineY + 1, iconX + iconWidth, lineY + 2, lineColor);
			int knobX = iconX + knobOffsets[index];
			context.fill(knobX, lineY, knobX + 2, lineY + 3, 0xFFFFFFFF);
		}
	}

	private static Entry entryAt(double mouseX, double mouseY, SpawnDisplayConfig config) {
		if (mouseX < MARGIN || mouseY < MARGIN) {
			return null;
		}

		int tileSize = config.getTileSize();
		int tileStride = tileSize + config.getSpacing();
		int column = (int) ((mouseX - MARGIN) / tileStride);
		int row = (int) ((mouseY - MARGIN) / tileStride);
		if (column >= config.getRowLength()
				|| (mouseX - MARGIN) % tileStride >= tileSize
				|| (mouseY - MARGIN) % tileStride >= tileSize) {
			return null;
		}

		int entryIndex = row * config.getRowLength() + column;
		return entryIndex >= 0 && entryIndex < entries.size() ? entries.get(entryIndex) : null;
	}

	private static GridSlot gridSlot(int index, SpawnDisplayConfig config) {
		int tileStride = config.getTileSize() + config.getSpacing();
		int column = index % config.getRowLength();
		int row = index / config.getRowLength();
		return new GridSlot(MARGIN + column * tileStride, MARGIN + row * tileStride);
	}

	private static int settingsButtonSize(int tileSize) {
		return Math.max(1, tileSize / 2);
	}

	private static boolean contains(GridSlot slot, int size, double mouseX, double mouseY) {
		return mouseX >= slot.x()
				&& mouseX < slot.x() + size
				&& mouseY >= slot.y()
				&& mouseY < slot.y() + size;
	}

	private static void renderTile(
			DrawContext context,
			MinecraftClient client,
			Entry entry,
			float tickDelta,
			SpawnDisplayConfig config,
			int x,
			int y
	) {
		TextRenderer textRenderer = client.textRenderer;
		int tileSize = config.getTileSize();
		float tileScale = tileSize / (float) BASE_TILE_SIZE;
		int footerYOffset = Math.round(BASE_FOOTER_Y_OFFSET * tileScale);
		TileStyle tileStyle = tileStyle(entry, config);
		context.fill(
				x + 1,
				y + 1,
				x + tileSize - 1,
				y + tileSize - 1,
				config.getBackgroundColor(tileStyle.backgroundColor())
		);
		if (isPinned(entry)) {
			float borderAnimationProgress = config.shouldDisableSpriteAnimations()
					? 0.0F
					: ((client.world.getTime() % BORDER_ANIMATION_PERIOD_TICKS) + tickDelta)
							/ BORDER_ANIMATION_PERIOD_TICKS;
			renderAnimatedGradientBorder(
					context,
					x,
					y,
					tileSize,
					borderAnimationProgress,
					PINNED_BORDER_COLORS,
					PINNED_BORDER_COLORS.length
			);
		} else if (entry.shiny() || entry.alpha() || entry.tera() || entry.fossilStatus()) {
			float borderAnimationProgress = config.shouldDisableSpriteAnimations()
					? 0.0F
					: ((client.world.getTime() % BORDER_ANIMATION_PERIOD_TICKS) + tickDelta)
							/ BORDER_ANIMATION_PERIOD_TICKS;
			int[] borderColors = new int[5];
			int borderColorCount = 0;
			borderColors[borderColorCount++] = tileStyle.borderColor();
			if (entry.shiny()) {
				borderColors[borderColorCount++] = config.getShinyColor();
			}
			if (entry.alpha()) {
				borderColors[borderColorCount++] = config.getAlphaColor();
			}
			if (entry.tera()) {
				borderColors[borderColorCount++] = config.getTeraColor();
			}
			if (entry.fossilStatus()) {
				borderColors[borderColorCount++] = config.getFossilColor();
			}
			renderAnimatedGradientBorder(
					context,
					x,
					y,
					tileSize,
					borderAnimationProgress,
					borderColors,
					borderColorCount
			);
		} else {
			renderBorder(context, x, y, tileSize, 0xFF000000 | tileStyle.borderColor());
		}
		float animationDelta = config.shouldDisableSpriteAnimations() ? 0.0F : tickDelta;
		renderAvatar(context, entry, animationDelta, tileSize, tileScale, footerYOffset, x, y);

		MatrixStack matrices = context.getMatrices();
		matrices.push();
		matrices.translate(x, y, 200.0F);
		matrices.scale(tileScale, tileScale, 1.0F);
		try {
			int classificationBadgeWidth = renderClassificationBadge(
					context,
					textRenderer,
					entry,
					tileStyle
			);
			renderStatusBadges(context, textRenderer, entry, config, classificationBadgeWidth);

			String arrow = directionArrow(client.player, entry.entity());
			int arrowWidth = textRenderer.getWidth(arrow);
			int availableDistanceWidth = BASE_TILE_SIZE - arrowWidth - 6;
			double distance = Math.sqrt(distanceSquared(
					client.player,
					entry.entity(),
					config.shouldUseHorizontalDistance()
			));
			String distanceLabel = distanceLabel(textRenderer, distance, availableDistanceWidth);
			int distanceWidth = textRenderer.getWidth(distanceLabel);
			context.drawText(textRenderer, arrow, 2, BASE_FOOTER_Y_OFFSET, 0xFFFFFFFF, true);
			context.drawText(
					textRenderer,
					distanceLabel,
					BASE_TILE_SIZE - distanceWidth - 2,
					BASE_FOOTER_Y_OFFSET,
					0xFFE0E0E0,
					true
			);
		} finally {
			matrices.pop();
		}
	}

	private static int renderClassificationBadge(
			DrawContext context,
			TextRenderer textRenderer,
			Entry entry,
			TileStyle tileStyle
	) {
		if (entry.specialClassification() == SpecialClassification.ULTRA_BEAST) {
			Text u = badgeText("U", tileStyle.badgePrimaryColor());
			Text b = badgeText("B", tileStyle.badgeSecondaryColor());
			context.drawText(textRenderer, u, 2, 2, 0xFF000000 | tileStyle.badgePrimaryColor(), true);
			int uWidth = textRenderer.getWidth(u);
			context.drawText(
					textRenderer,
					b,
					2 + uWidth,
					2,
					0xFF000000 | tileStyle.badgeSecondaryColor(),
					true
			);
			return uWidth + textRenderer.getWidth(b);
		}

		Text badge = badgeText(entry.classificationBadge(), tileStyle.badgePrimaryColor());
		context.drawText(
				textRenderer,
				badge,
				2,
				2,
				0xFF000000 | tileStyle.badgePrimaryColor(),
				true
		);
		return textRenderer.getWidth(badge);
	}

	private static Text badgeText(String badge, int color) {
		return Text.literal(badge).styled(style -> style
				.withBold(true)
				.withColor(color & 0xFFFFFF));
	}

	private static void renderStatusBadges(
			DrawContext context,
			TextRenderer textRenderer,
			Entry entry,
			SpawnDisplayConfig config,
			int classificationBadgeWidth
	) {
		String badges = entry.statusBadges();
		if (badges.isEmpty()) {
			return;
		}

		int topRowEnd = badges.length();
		while (topRowEnd > 1
				&& classificationBadgeWidth + statusRowWidth(textRenderer, badges, 0, topRowEnd) + 6
				> BASE_TILE_SIZE) {
			topRowEnd--;
		}

		renderStatusRow(context, textRenderer, badges, 0, topRowEnd, 2, config);
		if (topRowEnd < badges.length()) {
			renderStatusRow(context, textRenderer, badges, topRowEnd, badges.length(), 10, config);
		}
	}

	private static int statusRowWidth(
			TextRenderer textRenderer,
			String badges,
			int start,
			int end
	) {
		int width = 0;
		for (int index = start; index < end; index++) {
			width += statusBadgeWidth(textRenderer, badges.charAt(index));
		}
		return width;
	}

	private static int statusBadgeWidth(TextRenderer textRenderer, char badge) {
		return switch (badge) {
			case SPECIAL_SKIN_BADGE -> SPECIAL_SKIN_BADGE_SIZE;
			default -> textRenderer.getWidth(statusBadgeText(badge, null));
		};
	}

	private static void renderStatusRow(
			DrawContext context,
			TextRenderer textRenderer,
			String badges,
			int start,
			int end,
			int y,
			SpawnDisplayConfig config
	) {
		int x = BASE_TILE_SIZE - statusRowWidth(textRenderer, badges, start, end) - 2;
		for (int index = start; index < end; index++) {
			char badge = badges.charAt(index);
			if (badge == SPECIAL_SKIN_BADGE) {
				renderSpecialSkinBadge(context, x, y);
			} else {
				Text text = statusBadgeText(badge, config);
				context.drawText(textRenderer, text, x, y, 0xFFFFFFFF, true);
			}
			x += statusBadgeWidth(textRenderer, badge);
		}
	}

	private static Text statusBadgeText(char badge, SpawnDisplayConfig config) {
		int color = switch (badge) {
			case 'S' -> config == null ? 0xFFFFFF : config.getShinyColor();
			case 'A' -> config == null ? 0xFFFFFF : config.getAlphaColor();
			case 'T' -> config == null ? 0xFFFFFF : config.getTeraColor();
			case 'F' -> config == null ? 0xFFFFFF : config.getFossilColor();
			default -> 0xFFFFFF;
		};
		return Text.literal(Character.toString(badge)).styled(style -> style
				.withBold(true)
				.withColor(color & 0xFFFFFF));
	}

	private static void renderSpecialSkinBadge(DrawContext context, int x, int y) {
		context.drawTexture(
				SPECIAL_SKIN_BADGE_TEXTURE,
				x,
				y,
				0.0F,
				0.0F,
				SPECIAL_SKIN_BADGE_SIZE,
				SPECIAL_SKIN_BADGE_SIZE,
				SPECIAL_SKIN_BADGE_SIZE,
				SPECIAL_SKIN_BADGE_SIZE
		);
	}

	private static void renderBorder(DrawContext context, int x, int y, int tileSize, int color) {
		context.fill(x, y, x + tileSize, y + 1, color);
		context.fill(x, y + tileSize - 1, x + tileSize, y + tileSize, color);
		context.fill(x, y + 1, x + 1, y + tileSize - 1, color);
		context.fill(x + tileSize - 1, y + 1, x + tileSize, y + tileSize - 1, color);
	}

	private static void renderAnimatedGradientBorder(
			DrawContext context,
			int x,
			int y,
			int tileSize,
			float animationProgress,
			int[] colors,
			int colorCount
	) {
		int edgeLength = tileSize - 1;
		int perimeter = edgeLength * 4;
		for (int index = 0; index < perimeter; index++) {
			float position = index / (float) perimeter;
			float gradientPosition = position - animationProgress;
			gradientPosition -= (float) Math.floor(gradientPosition);
			float scaledPosition = gradientPosition * colorCount;
			int colorIndex = (int) scaledPosition;
			float blend = scaledPosition - colorIndex;
			int color = 0xFF000000 | blendColor(
					colors[colorIndex],
					colors[(colorIndex + 1) % colorCount],
					blend
			);

			int pixelX;
			int pixelY;
			if (index < tileSize) {
				pixelX = x + index;
				pixelY = y;
			} else if (index < tileSize + edgeLength) {
				pixelX = x + edgeLength;
				pixelY = y + index - tileSize + 1;
			} else if (index < tileSize + edgeLength * 2) {
				pixelX = x + edgeLength - 1 - (index - tileSize - edgeLength);
				pixelY = y + edgeLength;
			} else {
				pixelX = x;
				pixelY = y + edgeLength - 1 - (index - tileSize - edgeLength * 2);
			}

			context.fill(pixelX, pixelY, pixelX + 1, pixelY + 1, color);
		}
	}

	private static int blendColor(int first, int second, float amount) {
		int red = blendChannel(first >> 16, second >> 16, amount);
		int green = blendChannel(first >> 8, second >> 8, amount);
		int blue = blendChannel(first, second, amount);
		return red << 16 | green << 8 | blue;
	}

	private static int blendChannel(int first, int second, float amount) {
		int firstChannel = first & 0xFF;
		return Math.round(firstChannel + ((second & 0xFF) - firstChannel) * amount);
	}

	private static void renderAvatar(
			DrawContext context,
			Entry entry,
			float tickDelta,
			int tileSize,
			float tileScale,
			int footerYOffset,
			int x,
			int y
	) {
		MatrixStack matrices = context.getMatrices();
		context.enableScissor(x + 1, y + 1, x + tileSize - 1, y + footerYOffset);
		matrices.push();
		try {
			matrices.translate(x + tileSize / 2.0F, y + tileScale, 300.0F);
			Quaternionf rotation = new Quaternionf().rotationXYZ(
					(float) Math.toRadians(13.0),
					(float) Math.toRadians(35.0),
					0.0F
			);
			PokemonGuiUtilsKt.drawProfilePokemon(
					entry.renderablePokemon(),
					matrices,
					rotation,
					PoseType.PROFILE,
					entry.avatarState(),
					tickDelta,
					9.0F * tileScale,
					ProfileTransformType.PROFILE,
					false,
					1.0F,
					1.0F,
					1.0F,
					1.0F,
					0.0F,
					0.0F,
					13
			);
		} catch (RuntimeException exception) {
			if (REPORTED_AVATAR_FAILURES.add(entry.species())) {
				CobblemonSpawnDisplayClient.LOGGER.warn(
						"Could not render the HUD avatar for {}",
						entry.species(),
						exception
				);
			}
		} finally {
			matrices.pop();
			context.disableScissor();
		}
	}

	private static String directionArrow(PlayerEntity player, Entity target) {
		double deltaX = target.getX() - player.getX();
		double deltaZ = target.getZ() - player.getZ();
		if (Math.abs(deltaX) < 0.001 && Math.abs(deltaZ) < 0.001) {
			return DIRECTION_ARROWS[0];
		}

		float targetYaw = (float) Math.toDegrees(Math.atan2(-deltaX, deltaZ));
		float relativeYaw = MathHelper.wrapDegrees(targetYaw - player.getYaw());
		int sector = Math.floorMod((int) Math.floor((relativeYaw + 22.5F) / 45.0F), DIRECTION_ARROWS.length);
		return DIRECTION_ARROWS[sector];
	}

	private static double distanceSquared(PlayerEntity player, Entity target, boolean horizontal) {
		if (!horizontal) {
			return player.squaredDistanceTo(target);
		}

		double deltaX = target.getX() - player.getX();
		double deltaZ = target.getZ() - player.getZ();
		return deltaX * deltaX + deltaZ * deltaZ;
	}

	private static String distanceLabel(TextRenderer textRenderer, double distance, int availableWidth) {
		long blocks = Math.round(distance);
		String label = blocks + "m";
		if (textRenderer.getWidth(label) <= availableWidth) {
			return label;
		}
		if (blocks >= 1000) {
			return (blocks / 1000) + "k";
		}
		return Long.toString(blocks);
	}

	private static TileStyle tileStyle(Entry entry, SpawnDisplayConfig config) {
		if (entry.rarity() != null) {
			return TileStyle.uniform(config.getRarityColor(entry.rarity()));
		}

		if (entry.specialClassification() == null) {
			return TileStyle.uniform(config.getRarityColor(null));
		}

		return switch (entry.specialClassification()) {
			case LEGENDARY -> TileStyle.uniform(config.getLegendaryColor());
			case MYTHICAL -> TileStyle.uniform(config.getMythicalColor());
			case FOSSIL -> TileStyle.uniform(config.getFossilColor());
			case PARADOX -> new TileStyle(
					config.getParadoxBackgroundColor(),
					config.getParadoxBorderColor(),
					config.getParadoxBorderColor(),
					config.getParadoxBorderColor()
			);
			case ULTRA_BEAST -> new TileStyle(
					config.getUltraBeastBackgroundColor(),
					config.getUltraBeastBorderColor(),
					config.getUltraBeastBorderColor(),
					config.getUltraBeastBorderColor()
			);
		};
	}

	private static boolean isPinned(Entry entry) {
		return entry.highlighted() || PINNED_ENTRIES.containsKey(entry.entity().getUuid());
	}

	private record Entry(
			PokemonEntity entity,
			RenderablePokemon renderablePokemon,
			FloatingState avatarState,
			Identifier species,
			boolean highlighted,
			Rarity rarity,
			SpecialClassification specialClassification,
			boolean shiny,
			boolean alpha,
			boolean tera,
			boolean specialSkin,
			boolean fossilStatus,
			double distanceSquared
	) {
		private boolean hasClassification(SpecialClassification classification) {
			return specialClassification == classification;
		}

		private boolean isFossil() {
			return fossilStatus || hasClassification(SpecialClassification.FOSSIL);
		}

		private int rarityRank() {
			return rarity == null ? -1 : rarity.ordinal();
		}

		private String classificationBadge() {
			if (rarity != null) {
				return rarity.badge();
			}
			return specialClassification == null ? "?" : specialClassification.badge();
		}

		private String statusBadges() {
			StringBuilder badges = new StringBuilder(5);
			if (shiny) {
				badges.append('S');
			}
			if (alpha) {
				badges.append('A');
			}
			if (tera) {
				badges.append('T');
			}
			if (specialSkin) {
				badges.append(SPECIAL_SKIN_BADGE);
			}
			if (fossilStatus) {
				badges.append('F');
			}
			return badges.toString();
		}
	}

	private record TileStyle(
			int backgroundColor,
			int borderColor,
			int badgePrimaryColor,
			int badgeSecondaryColor
	) {
		private static TileStyle uniform(int color) {
			return new TileStyle(color, color, color, color);
		}
	}

	private record GridSlot(int x, int y) {
	}
}
