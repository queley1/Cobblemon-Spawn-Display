package com.cobblemonspawndisplay;

import com.cobblemon.mod.common.pokemon.Pokemon;

import java.util.Set;

public enum SpecialClassification {
	MYTHICAL("M"),
	LEGENDARY("L"),
	ULTRA_BEAST("UB"),
	PARADOX("P"),
	FOSSIL("F");

	// Cobblemon does not include form labels in its dedicated-server species sync,
	// so known fossils also need a species-based fallback on multiplayer clients.
	private static final Set<String> KNOWN_FOSSIL_SPECIES = Set.of(
			"omanyte",
			"omastar",
			"kabuto",
			"kabutops",
			"aerodactyl",
			"lileep",
			"cradily",
			"anorith",
			"armaldo",
			"cranidos",
			"rampardos",
			"shieldon",
			"bastiodon",
			"tirtouga",
			"carracosta",
			"archen",
			"archeops",
			"tyrunt",
			"tyrantrum",
			"amaura",
			"aurorus",
			"dracozolt",
			"arctozolt",
			"dracovish",
			"arctovish"
	);

	private final String badge;

	SpecialClassification(String badge) {
		this.badge = badge;
	}

	public String badge() {
		return badge;
	}

	public static SpecialClassification fromPokemon(Pokemon pokemon) {
		if (hasLabel(pokemon, "mythical")) {
			return MYTHICAL;
		}
		if (hasLabel(pokemon, "legendary")) {
			return LEGENDARY;
		}
		if (hasLabel(pokemon, "ultra_beast")) {
			return ULTRA_BEAST;
		}
		if (hasLabel(pokemon, "paradox")) {
			return PARADOX;
		}
		return null;
	}

	public static boolean isFossil(Pokemon pokemon) {
		return hasLabel(pokemon, "fossil") || KNOWN_FOSSIL_SPECIES.contains(
				pokemon.getSpecies().getResourceIdentifier().getPath()
		);
	}

	private static boolean hasLabel(Pokemon pokemon, String label) {
		return pokemon.hasLabels(label) || RarityIndex.hasSpeciesLabel(
				pokemon.getSpecies().getResourceIdentifier(),
				label
		);
	}
}
