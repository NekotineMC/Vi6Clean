package fr.nekotine.vi6clean.it;

import static fr.nekotine.vi6clean.it.Vi6Asserts.assertNearHorizontally;
import static fr.nekotine.vi6clean.it.Vi6TestHooks.game;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.logging.log4j.Level;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;

import fr.nekotine.core.bot.Bot;
import fr.nekotine.core.bot.BotRoster;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.map.IMapModule;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.configuration.ConfigManager;
import fr.nekotine.vi6clean.configuration.GameConfig;
import fr.nekotine.vi6clean.constant.InMapState;
import fr.nekotine.vi6clean.game.Vi6Game.ForcedKoth;
import fr.nekotine.vi6clean.game.phase.Vi6PhaseInMap;
import fr.nekotine.vi6clean.game.phase.Vi6PhaseInfiltration;
import fr.nekotine.vi6clean.game.phase.Vi6PhaseLobby;
import fr.nekotine.vi6clean.game.phase.Vi6PhasePreparation;
import fr.nekotine.vi6clean.map.ThiefSpawn;
import fr.nekotine.vi6clean.tool.ToolHandler;
import fr.nekotine.vi6clean.wrapper.InMapPhasePlayerWrapper;
import fr.xxgoldenbluexx.paperjunitserver.PaperServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * The test hooks set an infiltration up with bots, and the reset puts the game
 * back to a clean lobby, whatever ran before.
 */
class Vi6TestHooksIT {

	private static final String TOOL = "portable_teleporter";

	// Each test starts with a reset; this one keeps the next test class clean
	@AfterEach
	void resetForOtherTests() throws Exception {
		Vi6TestHooks.reset();
	}

	@RepeatedTest(3)
	void hooksSetAnInfiltrationUp() throws Exception {
		Vi6TestHooks.reset();
		try (var capture = new LogCapture()) {
			assertCleanLobby();
			var bots = Vi6TestHooks.spawnBots(2);
			var guard = bots.get(0).player();
			var thief = bots.get(1).player();
			Vi6TestHooks.putOnTeams(List.of(guard), List.of(thief));
			var forced = forcedKoths();
			Vi6TestHooks.forceKoths(forced.entrySet().stream().map(e -> new ForcedKoth(e.getKey(), e.getValue()))
					.toArray(ForcedKoth[]::new));
			Vi6TestHooks.goToInfiltration(null);

			PaperServer.onMain(() -> {
				var machine = game().getPhaseMachine();
				assertNotNull(machine.getPhase(Vi6PhaseInfiltration.class), "not in infiltration");
				var map = machine.getPhase(Vi6PhaseInMap.class).getMap();
				assertNotNull(map, "map not loaded");
				var wrapping = Ioc.resolve(WrappingModule.class);
				assertEquals(InMapState.ENTERING, wrapping.getWrapper(thief, InMapPhasePlayerWrapper.class).getState(),
						"thief state");
				var spawn = Vi6TestHooks.thiefSpawn(null).getSpawnPoint().toLocation(game().getWorld());
				assertNearHorizontally(spawn, thief.getLocation(), 4, "thief not at the first thief spawn");
				assertTrue(wrapping.getWrapper(guard, InMapPhasePlayerWrapper.class).isInside(), "guard not inside");
				var effects = new TreeMap<String, String>();
				map.getKoths().forEach((name, koth) -> {
					var effect = koth.getEffect();
					if (effect != null) {
						effects.put(name, effect.getCode());
					}
				});
				assertEquals(forced, effects, "KoTHs with an effect");
				return null;
			});

			var tool = Vi6TestHooks.giveTool(thief, TOOL);
			PaperServer.onMain(() -> {
				assertEquals(thief, tool.getOwner(), "tool owner");
				var held = Arrays.stream(thief.getInventory().getContents()).filter(item -> {
					if (item == null) {
						return false;
					}
					var pdc = item.getPersistentDataContainer();
					return TOOL.equals(pdc.get(ToolHandler.TOOL_TYPE_KEY, PersistentDataType.STRING)) && Integer
							.valueOf(tool.getId()).equals(pdc.get(ToolHandler.TOOL_ID_KEY, PersistentDataType.INTEGER));
				}).count();
				assertEquals(1, held, "registered tool stacks in the thief's inventory");
				return null;
			});

			var target = PaperServer.onMain(() -> {
				var from = guard.getLocation();
				return Vi6TestHooks.guardSpawns().stream().filter(l -> l.distance(from) > 1).findFirst()
						.orElseThrow(() -> new IllegalStateException("the map has a single guard spawn"));
			});
			Vi6TestHooks.place(guard, target);
			// Read after the ack: a refused teleport would have been rolled back
			PaperServer.onMain(() -> {
				assertNearHorizontally(target, guard.getLocation(), 0.6, "guard not placed");
				return null;
			});
			assertEquals(List.of(), capture.failures(), "an exception was logged");
			assertEquals(List.of(), capture.errors(), "an error was logged");
		}
	}

	@RepeatedTest(3)
	void resetCleansAGameUp() throws Exception {
		Vi6TestHooks.reset();
		try (var capture = new LogCapture()) {
			assertCleanLobby();
			var original = PaperServer.onMain(() -> {
				var infiltration = config().infiltration();
				var durations = List.of(infiltration.lostDuration(), infiltration.endSpectatorTime());
				// The thief is lost after 10 ticks and the round would end 40 ticks later
				infiltration.lostDuration(Duration.ofMillis(500));
				infiltration.endSpectatorTime(Duration.ofSeconds(2));
				return durations;
			});
			var bots = Vi6TestHooks.spawnBots(2);
			var names = bots.stream().map(Bot::name).toList();
			var files = new ArrayList<Path>();
			for (var bot : bots) {
				files.addAll(savedFiles(bot));
			}
			Vi6TestHooks.putOnTeams(List.of(bots.get(0).player()), List.of(bots.get(1).player()));
			Vi6TestHooks.forceKoths(new ForcedKoth(forcedKoths().firstKey(), "emp"));
			Vi6TestHooks.goToInfiltration(null);
			PaperServer.awaitTicks(20);
			PaperServer.onMain(() -> {
				assertNotNull(game().getPhaseMachine().getPhase(Vi6PhaseInfiltration.class), "not in infiltration");
				bots.forEach(b -> b.player().saveData());
				return null;
			});
			assertTrue(files.stream().anyMatch(Files::exists), "no bot file saved before the reset");
			// All thieves left, so checkForCompletion scheduled the round end: it is
			// pending
			PaperServer.onMain(() -> {
				assertTrue(
						Ioc.resolve(WrappingModule.class)
								.getWrapper(bots.get(1).player(), InMapPhasePlayerWrapper.class).hasLeft(),
						"thief has not left");
				assertNotNull(game().getPhaseMachine().getPhase(Vi6PhaseInfiltration.class),
						"the round already ended before the reset");
				return null;
			});

			// During the end-spectator time: the pending round end must not survive it
			Vi6TestHooks.reset();
			PaperServer.awaitTicks(60);

			PaperServer.onMain(() -> {
				var game = game();
				var machine = game.getPhaseMachine();
				assertNotNull(machine.getPhase(Vi6PhaseLobby.class), "not back in the lobby");
				assertNull(machine.getPhase(Vi6PhaseInMap.class), "the map phase survived the reset");
				assertNull(machine.getPhase(Vi6PhasePreparation.class), "the pending round end started a round");
				assertEquals(List.of(), Ioc.resolve(BotRoster.class).list(), "bots left");
				assertTrue(game.getPlayerList().isEmpty(), "players left in the game");
				assertTrue(game.getGuards().isEmpty(), "guards left");
				assertTrue(game.getThiefs().isEmpty(), "thieves left");
				var scoreboard = game.getScoreboard();
				for (var name : names) {
					var left = scoreboard.getScores(name).stream().filter(s -> s.isScoreSet())
							.map(s -> s.getObjective().getName() + "=" + s.getScore()).toList();
					assertEquals(List.of(), left, "scores left for " + name);
					assertNull(scoreboard.getEntryTeam(name), name + " still in a scoreboard team");
				}
				var infiltration = config().infiltration();
				assertEquals(original, List.of(infiltration.lostDuration(), infiltration.endSpectatorTime()),
						"config.yml durations not reloaded");
				assertNull(game.getForcedKoths(), "KoTH override not cleared");
				return null;
			});
			for (var file : files) {
				assertFalse(Files.exists(file), "bot file left: " + file);
				assertFalse(Files.exists(file.resolveSibling(file.getFileName() + "_old")),
						"bot backup file left: " + file);
			}
			assertEquals(List.of(), capture.failures(), "an exception was logged");
			assertEquals(List.of(), capture.errors(), "an error was logged");
		}
	}

	@RepeatedTest(3)
	void forcedKothsAreCheckedAndCanBeEmpty() throws Exception {
		Vi6TestHooks.reset();
		try (var capture = new LogCapture()) {
			assertCleanLobby();
			var bots = Vi6TestHooks.spawnBots(2);
			Vi6TestHooks.putOnTeams(List.of(bots.get(0).player()), List.of(bots.get(1).player()));
			var koth = forcedKoths().firstKey();
			var bad = List.of(List.of(new ForcedKoth("no_such_koth", "emp")),
					List.of(new ForcedKoth(koth, "no_such_effect")),
					List.of(new ForcedKoth(koth, "emp"), new ForcedKoth(koth, "regen")));
			for (var override : bad) {
				Vi6TestHooks.forceKoths(override.toArray(ForcedKoth[]::new));
				var thrown = PaperServer.onMain(() -> {
					var spawns = new HashMap<Player, ThiefSpawn>();
					var spawn = Vi6TestHooks.thiefSpawn(null);
					game().getThiefs().forEach(t -> spawns.put(t, spawn));
					try {
						game().getPhaseMachine().goTo(Vi6PhaseInfiltration.class, spawns);
						return null;
					} catch (RuntimeException e) {
						return e;
					}
				});
				// The machine builds the map phase by reflection, which wraps the exception
				Throwable cause = thrown;
				while (cause != null && !(cause instanceof IllegalArgumentException)) {
					cause = cause.getCause();
				}
				assertNotNull(cause, "override " + override + " threw " + thrown + ", not IllegalArgumentException");
				PaperServer.onMain(() -> {
					var machine = game().getPhaseMachine();
					assertNotNull(machine.getPhase(Vi6PhaseLobby.class), "left the lobby after " + override);
					assertNull(machine.getPhase(Vi6PhaseInMap.class), "map phase built for " + override);
					return null;
				});
			}

			Vi6TestHooks.forceKoths();
			Vi6TestHooks.goToInfiltration(null);
			PaperServer.onMain(() -> {
				var map = game().getPhaseMachine().getPhase(Vi6PhaseInMap.class).getMap();
				var withEffect = map.getKoths().values().stream().filter(k -> k.getEffect() != null)
						.map(k -> k.getName()).toList();
				assertEquals(List.of(), withEffect, "an empty override spawned KoTHs");
				return null;
			});
			assertEquals(List.of(), capture.failures(), "an exception was logged");
			assertEquals(List.of(), capture.errors(), "an error was logged");
		}
	}

	@RepeatedTest(3)
	void hooksGoToPreparationAndANamedThiefSpawn() throws Exception {
		Vi6TestHooks.reset();
		try (var capture = new LogCapture()) {
			assertCleanLobby();
			var bots = Vi6TestHooks.spawnBots(2);
			Vi6TestHooks.putOnTeams(List.of(bots.get(0).player()), List.of(bots.get(1).player()));
			Vi6TestHooks.goToPreparation();
			PaperServer.onMain(() -> {
				assertNotNull(game().getPhaseMachine().getPhase(Vi6PhasePreparation.class), "not in preparation");
				return null;
			});

			Vi6TestHooks.reset();
			assertCleanLobby();
			bots = Vi6TestHooks.spawnBots(2);
			var thief = bots.get(1).player();
			Vi6TestHooks.putOnTeams(List.of(bots.get(0).player()), List.of(thief));
			var spawnName = PaperServer.onMain(() -> {
				var names = Vi6TestHooks.currentMap().getThiefSpawns().keySet().stream().sorted().toList();
				assertTrue(names.size() >= 2, "the map needs two thief spawns");
				return names.get(1);
			});
			Vi6TestHooks.goToInfiltration(spawnName);
			PaperServer.onMain(() -> {
				var spawn = Vi6TestHooks.thiefSpawn(spawnName).getSpawnPoint().toLocation(game().getWorld());
				assertNearHorizontally(spawn, thief.getLocation(), 4, "thief not at thief spawn " + spawnName);
				return null;
			});

			var thrown = PaperServer.onMain(() -> {
				try {
					Vi6TestHooks.thiefSpawn("no_such_spawn");
					return null;
				} catch (RuntimeException e) {
					return e;
				}
			});
			assertInstanceOf(IllegalArgumentException.class, thrown, "thiefSpawn(\"no_such_spawn\")");
			assertEquals(List.of(), capture.failures(), "an exception was logged");
			assertEquals(List.of(), capture.errors(), "an error was logged");
		}
	}

	@RepeatedTest(3)
	void mapChangeClearsTheKothOverride() throws Exception {
		Vi6TestHooks.reset();
		try (var capture = new LogCapture()) {
			assertCleanLobby();
			var koth = forcedKoths().firstKey();
			Vi6TestHooks.forceKoths(new ForcedKoth(koth, "emp"));
			PaperServer.onMain(() -> {
				var game = game();
				var original = game.getMapName();
				try {
					game.setMapName(original);
					assertEquals(List.of(new ForcedKoth(koth, "emp")), game.getForcedKoths(),
							"override cleared on the same map");
					var other = otherLoadableMap(original);
					assertNull(game.getForcedKoths(), "override kept on map " + other);
				} finally {
					game.setMapName(original);
				}
				return null;
			});
			var cleared = capture.lines().stream()
					.filter(l -> l.message().startsWith("KoTH override cleared") && l.level() == Level.INFO).count();
			assertEquals(1, cleared, "KoTH override cleared info lines: " + capture.messages());
			assertEquals(List.of(), capture.failures(), "an exception was logged");
			assertEquals(List.of(), capture.errors(), "an error was logged");
		}
	}

	@RepeatedTest(3)
	void mapPhaseWarnsOnlyWithATestOverride() throws Exception {
		Vi6TestHooks.reset();
		try (var capture = new LogCapture()) {
			assertCleanLobby();
			var bots = Vi6TestHooks.spawnBots(2);
			Vi6TestHooks.putOnTeams(List.of(bots.get(0).player()), List.of(bots.get(1).player()));
			Vi6TestHooks.goToInfiltration(null);
			assertEquals(List.of(), overrideWarnings(capture), "override warning with config.yml defaults");

			Vi6TestHooks.reset();
			capture.clear();
			bots = Vi6TestHooks.spawnBots(2);
			Vi6TestHooks.putOnTeams(List.of(bots.get(0).player()), List.of(bots.get(1).player()));
			Vi6TestHooks.forceKoths();
			PaperServer.onMain(() -> {
				config().infiltration().lostDuration(Duration.ofSeconds(7));
				return null;
			});
			Vi6TestHooks.goToInfiltration(null);
			var warnings = overrideWarnings(capture);
			assertEquals(1, warnings.size(), "override warnings: " + warnings);
			assertTrue(warnings.get(0).contains("forced KoTHs []"), "no forced KoTHs in: " + warnings.get(0));
			assertTrue(warnings.get(0).contains("lost-duration-seconds 240.0s -> 7.0s"),
					"no changed duration in: " + warnings.get(0));
			assertEquals(List.of(), capture.failures(), "an exception was logged");
			assertEquals(List.of(), capture.errors(), "an error was logged");
		}
	}

	private static List<String> overrideWarnings(LogCapture capture) {
		return capture.lines().stream().filter(l -> l.level() == Level.WARN)
				.filter(l -> l.message().startsWith("Map phase started with test overrides active: "))
				.map(LogCapture.Line::message).toList();
	}

	/**
	 * Switches the game to the first other map by name that loads; main thread
	 * only.
	 *
	 * @return its name
	 */
	private static String otherLoadableMap(String current) {
		var names = Ioc.resolve(IMapModule.class).listMaps().stream().map(m -> m.getName())
				.filter(n -> !n.equals(current)).sorted().toList();
		for (var name : names) {
			try {
				game().setMapName(name);
				return name;
			} catch (RuntimeException e) {
				// Its world is not in the bundle: try the next one
			}
		}
		throw new IllegalStateException("no other loadable map than " + current + " in " + names);
	}

	private static void assertCleanLobby() throws Exception {
		PaperServer.onMain(() -> {
			var game = game();
			assertNotNull(game.getPhaseMachine().getPhase(Vi6PhaseLobby.class), "game is not in the lobby");
			assertEquals(List.of(), Ioc.resolve(BotRoster.class).list(), "leftover bots");
			assertTrue(game.getGuards().isEmpty(), "leftover guards");
			assertTrue(game.getThiefs().isEmpty(), "leftover thieves");
			assertNull(game.getForcedKoths(), "leftover KoTH override");
			return null;
		});
	}

	/** @return the first two KoTHs of the map by name, forced to emp and regen */
	private static TreeMap<String, String> forcedKoths() throws Exception {
		var names = PaperServer
				.onMain(() -> Vi6TestHooks.currentMap().getKoths().keySet().stream().sorted().limit(2).toList());
		assertEquals(2, names.size(), "the map needs two KoTHs");
		return new TreeMap<>(Map.of(names.get(0), "emp", names.get(1), "regen"));
	}

	/** @return the bot's data, stats and advancements files */
	private static List<Path> savedFiles(Bot bot) throws Exception {
		return PaperServer.onMain(() -> {
			var server = MinecraftServer.getServer();
			var uuid = bot.player().getUniqueId().toString();
			return List.of(server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(uuid + ".dat"),
					server.getWorldPath(LevelResource.PLAYER_STATS_DIR).resolve(uuid + ".json"),
					server.getWorldPath(LevelResource.PLAYER_ADVANCEMENTS_DIR).resolve(uuid + ".json"));
		});
	}

	private static GameConfig config() {
		return Ioc.resolve(ConfigManager.class).getConfig().game();
	}
}
