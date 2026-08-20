package com.cobblemonspawndisplay;

import java.util.Locale;
import java.util.Optional;

public enum Rarity {
	COMMON("C"),
	UNCOMMON("U"),
	RARE("R"),
	ULTRA_RARE("UR");

	private final String badge;

	Rarity(String badge) {
		this.badge = badge;
	}

	public String badge() {
		return badge;
	}

	public static Optional<Rarity> fromBucket(String bucket) {
		if (bucket == null) {
			return Optional.empty();
		}

		return switch (bucket.toLowerCase(Locale.ROOT)) {
			case "common" -> Optional.of(COMMON);
			case "uncommon" -> Optional.of(UNCOMMON);
			case "rare" -> Optional.of(RARE);
			case "ultra-rare", "ultra_rare", "ultrarare" -> Optional.of(ULTRA_RARE);
			default -> Optional.empty();
		};
	}
}
