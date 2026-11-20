package fr.nekotine.vi6clean.it;

import static fr.nekotine.vi6clean.it.Vi6TestHooks.game;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import fr.nekotine.core.bot.Bot;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.Vi6Commands;
import fr.nekotine.vi6clean.game.phase.Vi6PhaseInfiltration;
import fr.nekotine.vi6clean.game.phase.Vi6PhaseLobby;
import fr.nekotine.vi6clean.game.phase.Vi6PhasePreparation;
import fr.nekotine.vi6clean.map.ThiefSpawn;
import fr.nekotine.vi6clean.wrapper.PreparationPhasePlayerWrapper;
import fr.xxgoldenbluexx.paperjunitserver.PaperServer;

/**
 * {@code /game entrance <name>} selects a thief's entrance in preparation, as
 * clicking its minimap ArmorStand does, and refuses everything else.
 */
class Vi6SpawnSelectIT {

	/** Ticks for a bot's command to reach the server and run. */
	private static final int COMMAND_TICKS = 5;

	private Bot guard;

	private Bot thief;

	/** A bot online but not in the game. */
	private Bot outsider;

	// Each test starts with a reset; this one keeps the next test class clean
	@AfterEach
	void resetForOtherTests() throws Exception {
		Vi6TestHooks.reset();
	}

	@Test
	void aThiefPicksAnEntranceAsTheClickDoes() throws Exception {
		try (var capture = new LogCapture()) {
			setUp(true);
			var name = PaperServer.onMain(() -> Vi6Commands.entranceNames().getFirst());
			send(thief, "game entrance " + name);
			PaperServer.onMain(() -> {
				assertSame(preparationSpawns().get(name), selected(thief.player()), "not the named entrance");
				return null;
			});
			assertClean(capture);
		}
	}

	@Test
	void aThiefRePicksAnotherEntrance() throws Exception {
		try (var capture = new LogCapture()) {
			setUp(true);
			var names = PaperServer.onMain(Vi6Commands::entranceNames);
			assertTrue(names.size() >= 2, "the map needs two entrances, it has " + names);
			send(thief, "game entrance " + names.get(0));
			send(thief, "game entrance " + names.get(1));
			PaperServer.onMain(() -> {
				assertSame(preparationSpawns().get(names.get(1)), selected(thief.player()), "the re-pick was ignored");
				return null;
			});
			assertClean(capture);
		}
	}

	@Test
	void aGuardIsRefused() throws Exception {
		try (var capture = new LogCapture()) {
			setUp(true);
			var name = PaperServer.onMain(() -> Vi6Commands.entranceNames().getFirst());
			send(guard, "game entrance " + name);
			PaperServer.onMain(() -> {
				assertNull(selected(guard.player()), "a guard picked an entrance");
				return null;
			});
			assertClean(capture);
		}
	}

	@Test
	void anUnknownNameKeepsThePreviousPick() throws Exception {
		try (var capture = new LogCapture()) {
			setUp(true);
			var name = PaperServer.onMain(() -> Vi6Commands.entranceNames().getFirst());
			send(thief, "game entrance " + name);
			send(thief, "game entrance Pas une entrée");
			PaperServer.onMain(() -> {
				assertSame(preparationSpawns().get(name), selected(thief.player()), "the previous pick was lost");
				return null;
			});
			assertClean(capture);
		}
	}

	@Test
	void aLobbyPickIsRefused() throws Exception {
		try (var capture = new LogCapture()) {
			setUp(false);
			var name = PaperServer.onMain(() -> {
				assertNotNull(game().getPhaseMachine().getPhase(Vi6PhaseLobby.class), "not in the lobby");
				assertEquals(List.of(), Vi6Commands.entranceNames(), "entrances suggested in the lobby");
				return firstMapEntrance();
			});
			send(thief, "game entrance " + name);
			Vi6TestHooks.goToPreparation();
			PaperServer.onMain(() -> {
				assertNull(selected(thief.player()), "a lobby pick carried into preparation");
				return null;
			});
			assertClean(capture);
		}
	}

	@Test
	void aPlayerOutsideTheGameIsRefused() throws Exception {
		try (var capture = new LogCapture()) {
			setUp(true);
			var name = PaperServer.onMain(Vi6SpawnSelectIT::firstMapEntrance);
			send(outsider, "game entrance " + name);
			PaperServer.onMain(() -> {
				assertTrue(
						Ioc.resolve(WrappingModule.class)
								.getWrapperOptional(outsider.player(), PreparationPhasePlayerWrapper.class).isEmpty(),
						"the outsider joined preparation");
				assertNull(selected(thief.player()), "the outsider's pick reached the thief");
				return null;
			});
			assertClean(capture);
		}
	}

	@Test
	void anInfiltrationPickIsRefused() throws Exception {
		try (var capture = new LogCapture()) {
			setUp(false);
			var name = PaperServer.onMain(Vi6SpawnSelectIT::firstMapEntrance);
			Vi6TestHooks.goToInfiltration(null);
			send(thief, "game entrance " + name);
			PaperServer.onMain(() -> {
				assertNotNull(game().getPhaseMachine().getPhase(Vi6PhaseInfiltration.class), "infiltration ended");
				assertEquals(List.of(), Vi6Commands.entranceNames(), "entrances suggested in infiltration");
				return null;
			});
			assertClean(capture);
		}
	}

	@Test
	void suggestionsAreTheMapsEntrancesInPreparation() throws Exception {
		try (var capture = new LogCapture()) {
			setUp(true);
			PaperServer.onMain(() -> {
				var expected = preparationSpawns().keySet().stream().sorted().toList();
				assertTrue(!expected.isEmpty(), "the map has no entrance");
				assertEquals(expected, Vi6Commands.entranceNames(), "not the map's entrances, sorted");
				// The command's own tab completion, as a client asks for it
				assertEquals(expected, Bukkit.getCommandMap().tabComplete(thief.player(), "game entrance "),
						"the command does not suggest the map's entrances");
				return null;
			});
			assertClean(capture);
		}
	}

	@Test
	void withoutAPickPreparationEndsOnARandomEntrance() throws Exception {
		try (var capture = new LogCapture()) {
			setUp(true);
			PaperServer.onMain(() -> {
				var spawns = preparationSpawns();
				var wrapper = Ioc.resolve(WrappingModule.class).getWrapper(thief.player(),
						PreparationPhasePlayerWrapper.class);
				assertNull(wrapper.getSelectedSpawn(), "picked before any command");
				game().getPhaseMachine().getPhase(Vi6PhasePreparation.class).complete();
				assertNotNull(game().getPhaseMachine().getPhase(Vi6PhaseInfiltration.class), "no infiltration");
				assertTrue(spawns.containsValue(wrapper.getSelectedSpawn()), "no random entrance");
				return null;
			});
			assertClean(capture);
		}
	}

	/**
	 * Resets, then joins a guard, a thief and an outsider removed from the game,
	 * with no KoTH; then goes to preparation if {@code preparation}.
	 */
	private void setUp(boolean preparation) throws Exception {
		Vi6TestHooks.reset();
		var bots = Vi6TestHooks.spawnBots(3);
		guard = bots.get(0);
		thief = bots.get(1);
		outsider = bots.get(2);
		Vi6TestHooks.putOnTeams(List.of(guard.player()), List.of(thief.player()));
		PaperServer.onMain(() -> {
			game().removePlayer(outsider.player());
			return null;
		});
		Vi6TestHooks.forceKoths();
		if (preparation) {
			Vi6TestHooks.goToPreparation();
		}
	}

	private static void send(Bot bot, String command) throws Exception {
		PaperServer.onMain(() -> {
			bot.sendCommand(command);
			return null;
		});
		PaperServer.awaitTicks(COMMAND_TICKS);
	}

	/** @return the current map's first entrance name; main thread only */
	private static String firstMapEntrance() {
		return Vi6TestHooks.currentMap().getThiefSpawns().keySet().stream().sorted().findFirst().orElseThrow();
	}

	/** Main thread only. */
	private static ThiefSpawn selected(Player player) {
		return Ioc.resolve(WrappingModule.class).getWrapper(player, PreparationPhasePlayerWrapper.class)
				.getSelectedSpawn();
	}

	/** @return the running preparation's map entrances; main thread only */
	private static Map<String, ThiefSpawn> preparationSpawns() {
		return game().getPhaseMachine().getPhase(Vi6PhasePreparation.class).getParent().getMap().getThiefSpawns();
	}

	private static void assertClean(LogCapture capture) {
		assertEquals(List.of(), capture.failures(), "an exception was logged");
		assertEquals(List.of(), capture.errors(), "an error was logged");
	}
}
