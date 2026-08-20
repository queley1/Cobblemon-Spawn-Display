package com.cobblemonspawndisplay;

import com.cobblemon.mod.common.pokemon.Pokemon;

import java.util.Set;

final class SpecialAppearance {
	private static final Set<String> NON_SKIN_MYTHICAL_ASPECTS = Set.of(
			"mythical_alpha",
			"mythical_raid",
			"mythical_raidboss",
			"mythical_wildtera"
	);
	private static final Set<String> NAMED_SKIN_ASPECTS = Set.of(
			"deluxe_easter",
			"gojoxmas",
			"grandmaster",
			"lumiose",
			"maskless",
			"radar_spawned",
			"red-leaf",
			"samurai_appa"
	);
	private static final String OVERWORLD_TERA_ASPECT = "mythical_wildtera";
	private static final String OVERWORLD_TERA_EFFECT_ASPECT = "mythical_radiant";
	private static final String TERA_ASPECT_PREFIX = "msd:tera_";

	private SpecialAppearance() {
	}

	static boolean hasSpecialSkin(Pokemon pokemon) {
		boolean overworldTera = pokemon.getAspects().contains(OVERWORLD_TERA_ASPECT);
		return pokemon.getAspects().stream()
				.filter(aspect -> !overworldTera || !aspect.equals(OVERWORLD_TERA_EFFECT_ASPECT))
				.anyMatch(SpecialAppearance::isSkinAspect);
	}

	static boolean isTera(Pokemon pokemon) {
		return pokemon.getAspects().stream().anyMatch(SpecialAppearance::isTeraAspect);
	}

	private static boolean isSkinAspect(String aspect) {
		if (NON_SKIN_MYTHICAL_ASPECTS.contains(aspect)) {
			return false;
		}

		return aspect.startsWith("mythical_")
				|| aspect.startsWith("unite_")
				|| aspect.startsWith("smp_")
				|| aspect.startsWith("cosmetic_item-")
				|| NAMED_SKIN_ASPECTS.contains(aspect);
	}

	private static boolean isTeraAspect(String aspect) {
		return aspect.equals(OVERWORLD_TERA_ASPECT)
				|| aspect.startsWith(TERA_ASPECT_PREFIX)
				|| aspect.equals("play_tera");
	}
}
