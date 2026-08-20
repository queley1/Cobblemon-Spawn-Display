package com.cobblemonspawndisplay;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public final class RarityIndex {
	private static final String SHEET_RESOURCE =
			"/assets/cobblemon_spawn_display/rarity_buckets.json";
	private static volatile Map<Integer, List<Rarity>> sheetRarities = Map.of();
	private static volatile Map<String, Rarity> modFileRarities = Map.of();
	private static volatile Map<String, Rarity> evolutionFamilyRarities = Map.of();
	private static volatile Map<String, Set<String>> speciesLabels = Map.of();

	private RarityIndex() {
	}

	public static void load() {
		sheetRarities = loadSheetRarities();
		modFileRarities = loadModFileRarities();
		evolutionFamilyRarities = loadEvolutionFamilyRarities();
	}

	private static Map<Integer, List<Rarity>> loadSheetRarities() {
		Map<Integer, List<Rarity>> discovered = new HashMap<>();

		try (InputStream input = RarityIndex.class.getResourceAsStream(SHEET_RESOURCE)) {
			if (input == null) {
				CobblemonSpawnDisplayClient.LOGGER.error("Missing bundled rarity sheet snapshot {}", SHEET_RESOURCE);
				return Map.of();
			}

			try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
				JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
				JsonObject dexBuckets = root.getAsJsonObject("dexBuckets");
				if (dexBuckets == null) {
					throw new IOException("Rarity sheet snapshot has no dexBuckets object");
				}

				for (Map.Entry<String, JsonElement> entry : dexBuckets.entrySet()) {
					int nationalDexNumber = Integer.parseInt(entry.getKey());
					if (!entry.getValue().isJsonArray()) {
						continue;
					}

					EnumSet<Rarity> buckets = EnumSet.noneOf(Rarity.class);
					JsonArray values = entry.getValue().getAsJsonArray();
					for (JsonElement value : values) {
						if (value.isJsonPrimitive()) {
							Rarity.fromBucket(value.getAsString()).ifPresent(buckets::add);
						}
					}

					if (!buckets.isEmpty()) {
						discovered.put(nationalDexNumber, List.copyOf(buckets));
					}
				}

				CobblemonSpawnDisplayClient.LOGGER.info(
						"Indexed sheet-backed rarities for {} National Dex entries",
						discovered.size()
				);
			}
		} catch (IOException | RuntimeException exception) {
			CobblemonSpawnDisplayClient.LOGGER.error("Could not load the bundled rarity sheet snapshot", exception);
			return Map.of();
		}

		return Map.copyOf(discovered);
	}

	private static Map<String, Rarity> loadModFileRarities() {
		Map<String, Rarity> discovered = new HashMap<>();

		for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
			for (Path root : mod.getRootPaths()) {
				Path dataRoot = root.resolve("data");
				if (!Files.isDirectory(dataRoot)) {
					continue;
				}

				try (Stream<Path> namespaces = Files.list(dataRoot)) {
					for (Path namespaceRoot : namespaces.filter(Files::isDirectory).toList()) {
						indexSpawnDirectory(namespaceRoot.resolve("spawn_pool_world"), discovered);
					}
				} catch (IOException exception) {
					CobblemonSpawnDisplayClient.LOGGER.debug(
							"Could not inspect spawn data from mod {}",
							mod.getMetadata().getId(),
							exception
					);
				}
			}
		}

		CobblemonSpawnDisplayClient.LOGGER.info(
				"Indexed lowest mod-file rarity fallbacks for {} Pokemon species",
				discovered.size()
		);
		return Map.copyOf(discovered);
	}

	private static void indexSpawnDirectory(Path spawnDirectory, Map<String, Rarity> discovered) {
		if (!Files.isDirectory(spawnDirectory)) {
			return;
		}

		try (Stream<Path> files = Files.walk(spawnDirectory)) {
			for (Path file : files.filter(Files::isRegularFile)
					.filter(path -> path.getFileName().toString().endsWith(".json"))
					.toList()) {
				indexSpawnFile(file, discovered);
			}
		} catch (IOException exception) {
			CobblemonSpawnDisplayClient.LOGGER.debug("Could not inspect spawn directory {}", spawnDirectory, exception);
		}
	}

	private static void indexSpawnFile(Path file, Map<String, Rarity> discovered) {
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			if (root.has("enabled") && !root.get("enabled").getAsBoolean()) {
				return;
			}

			JsonArray spawns = root.getAsJsonArray("spawns");
			if (spawns == null) {
				return;
			}

			for (JsonElement element : spawns) {
				if (!element.isJsonObject()) {
					continue;
				}

				JsonObject spawn = element.getAsJsonObject();
				JsonElement pokemonElement = spawn.get("pokemon");
				JsonElement bucketElement = spawn.get("bucket");
				if (pokemonElement == null || bucketElement == null
						|| !pokemonElement.isJsonPrimitive() || !bucketElement.isJsonPrimitive()) {
					continue;
				}

				String species = speciesPath(pokemonElement.getAsString());
				Rarity.fromBucket(bucketElement.getAsString()).ifPresent(rarity ->
						discovered.merge(species, rarity, RarityIndex::lowerRarity)
				);
			}
		} catch (IOException | RuntimeException exception) {
			CobblemonSpawnDisplayClient.LOGGER.debug("Could not read spawn pool {}", file, exception);
		}
	}

	private static Map<String, Rarity> loadEvolutionFamilyRarities() {
		Map<String, Integer> dexBySpecies = new HashMap<>();
		Map<String, Set<String>> evolutionGraph = new HashMap<>();
		Map<String, Set<String>> labelsBySpecies = new HashMap<>();

		for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
			for (Path root : mod.getRootPaths()) {
				Path dataRoot = root.resolve("data");
				if (!Files.isDirectory(dataRoot)) {
					continue;
				}

				try (Stream<Path> namespaces = Files.list(dataRoot)) {
					for (Path namespaceRoot : namespaces.filter(Files::isDirectory).toList()) {
						indexSpeciesDirectory(
								namespaceRoot.resolve("species"),
								dexBySpecies,
								evolutionGraph,
								labelsBySpecies
						);
					}
				} catch (IOException exception) {
					CobblemonSpawnDisplayClient.LOGGER.debug(
							"Could not inspect species data from mod {}",
							mod.getMetadata().getId(),
							exception
					);
				}
			}
		}

		Map<String, Set<String>> indexedLabels = new HashMap<>();
		labelsBySpecies.forEach((species, labels) -> indexedLabels.put(species, Set.copyOf(labels)));
		speciesLabels = Map.copyOf(indexedLabels);
		CobblemonSpawnDisplayClient.LOGGER.info(
				"Indexed local labels for {} Pokemon species",
				speciesLabels.size()
		);

		Map<String, Rarity> discovered = new HashMap<>();
		Set<String> visited = new HashSet<>();
		for (String species : evolutionGraph.keySet()) {
			if (!visited.add(species)) {
				continue;
			}

			List<String> family = new ArrayList<>();
			ArrayDeque<String> pending = new ArrayDeque<>();
			pending.add(species);
			Rarity familyRarity = null;

			while (!pending.isEmpty()) {
				String member = pending.removeFirst();
				family.add(member);
				Rarity directRarity = findDirectRarity(dexBySpecies.get(member), member);
				if (directRarity != null) {
					familyRarity = familyRarity == null
							? directRarity
							: lowerRarity(familyRarity, directRarity);
				}

				for (String relative : evolutionGraph.getOrDefault(member, Set.of())) {
					if (visited.add(relative)) {
						pending.addLast(relative);
					}
				}
			}

			if (family.size() < 2 || familyRarity == null) {
				continue;
			}

			for (String member : family) {
				if (findDirectRarity(dexBySpecies.get(member), member) == null) {
					discovered.put(member, familyRarity);
				}
			}
		}

		CobblemonSpawnDisplayClient.LOGGER.info(
				"Indexed evolution-family rarity fallbacks for {} Pokemon species",
				discovered.size()
		);
		return Map.copyOf(discovered);
	}

	private static void indexSpeciesDirectory(
			Path speciesDirectory,
			Map<String, Integer> dexBySpecies,
			Map<String, Set<String>> evolutionGraph,
			Map<String, Set<String>> labelsBySpecies
	) {
		if (!Files.isDirectory(speciesDirectory)) {
			return;
		}

		try (Stream<Path> files = Files.walk(speciesDirectory)) {
			for (Path file : files.filter(Files::isRegularFile)
					.filter(path -> path.getFileName().toString().endsWith(".json"))
					.toList()) {
				indexSpeciesFile(file, dexBySpecies, evolutionGraph, labelsBySpecies);
			}
		} catch (IOException exception) {
			CobblemonSpawnDisplayClient.LOGGER.debug("Could not inspect species directory {}", speciesDirectory, exception);
		}
	}

	private static void indexSpeciesFile(
			Path file,
			Map<String, Integer> dexBySpecies,
			Map<String, Set<String>> evolutionGraph,
			Map<String, Set<String>> labelsBySpecies
	) {
		String fileName = file.getFileName().toString();
		String species = fileName.substring(0, fileName.length() - ".json".length()).toLowerCase(Locale.ROOT);

		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			evolutionGraph.computeIfAbsent(species, ignored -> new HashSet<>());

			JsonElement dexElement = root.get("nationalPokedexNumber");
			if (dexElement != null && dexElement.isJsonPrimitive()) {
				dexBySpecies.put(species, dexElement.getAsInt());
			}

			JsonElement labelsElement = root.get("labels");
			if (labelsElement != null && labelsElement.isJsonArray()) {
				Set<String> labels = labelsBySpecies.computeIfAbsent(species, ignored -> new HashSet<>());
				for (JsonElement label : labelsElement.getAsJsonArray()) {
					if (label.isJsonPrimitive()) {
						labels.add(label.getAsString().toLowerCase(Locale.ROOT));
					}
				}
			}

			JsonElement preEvolutionElement = root.get("preEvolution");
			if (preEvolutionElement == null || !preEvolutionElement.isJsonPrimitive()) {
				return;
			}

			String preEvolution = speciesPath(preEvolutionElement.getAsString());
			if (preEvolution.isEmpty()) {
				return;
			}

			evolutionGraph.computeIfAbsent(species, ignored -> new HashSet<>()).add(preEvolution);
			evolutionGraph.computeIfAbsent(preEvolution, ignored -> new HashSet<>()).add(species);
		} catch (IOException | RuntimeException exception) {
			CobblemonSpawnDisplayClient.LOGGER.debug("Could not read species data {}", file, exception);
		}
	}

	private static String speciesPath(String pokemon) {
		String token = pokemon.strip().split("\\s+", 2)[0];
		int namespaceSeparator = token.indexOf(':');
		if (namespaceSeparator >= 0) {
			token = token.substring(namespaceSeparator + 1);
		}
		return token.toLowerCase(Locale.ROOT);
	}

	private static Rarity lowerRarity(Rarity first, Rarity second) {
		return first.ordinal() <= second.ordinal() ? first : second;
	}

	private static Rarity findDirectRarity(Integer nationalDexNumber, String species) {
		if (nationalDexNumber != null) {
			List<Rarity> sheetBuckets = sheetRarities.get(nationalDexNumber);
			if (sheetBuckets != null && !sheetBuckets.isEmpty()) {
				return sheetBuckets.getFirst();
			}
		}

		return modFileRarities.get(species.toLowerCase(Locale.ROOT));
	}

	public static Optional<Rarity> findLowest(int nationalDexNumber, Identifier speciesId) {
		return Optional.ofNullable(findDirectRarity(nationalDexNumber, speciesId.getPath()));
	}

	public static Optional<Rarity> findEvolutionFallback(Identifier speciesId) {
		return Optional.ofNullable(
				evolutionFamilyRarities.get(speciesId.getPath().toLowerCase(Locale.ROOT))
		);
	}

	public static boolean hasSpeciesLabel(Identifier speciesId, String label) {
		return speciesLabels.getOrDefault(
				speciesId.getPath().toLowerCase(Locale.ROOT),
				Set.of()
		).contains(label.toLowerCase(Locale.ROOT));
	}
}
