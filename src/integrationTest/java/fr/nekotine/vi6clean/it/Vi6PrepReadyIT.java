package fr.nekotine.vi6clean.it;

import static fr.nekotine.vi6clean.it.Vi6TestHooks.game;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import fr.nekotine.core.bot.Bot;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.game.phase.Vi6PhaseInfiltration;
import fr.nekotine.vi6clean.game.phase.Vi6PhaseLobby;
import fr.nekotine.vi6clean.game.phase.Vi6PhasePreparation;
import fr.nekotine.vi6clean.wrapper.LobbyPhasePlayerWrapper;
import fr.nekotine.vi6clean.wrapper.PreparationPhasePlayerWrapper;
import fr.xxgoldenbluexx.paperjunitserver.PaperServer;

/**
 * {@code /game prepready} toggles a player's preparation ready state, as the
 * "Prêt" menu toggle does, and refuses outside preparation.
 */
class Vi6PrepReadyIT {

	/** Ticks for a bot's command to reach the server and run. */
	private static final int COMMAND_TICKS = 5;

	private static final String COMMAND = "game prepready";

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
	void aThiefReadiesWhileTheGuardIsNot() throws Exception {
		try (var capture = new LogCapture()) {
			setUp(true);
			send(thief, COMMAND);
			PaperServer.onMain(() -> {
				assertTrue(ready(thief.player()), "the thief is not ready");
				assertFalse(ready(guard.player()), "the guard became ready");
				assertPreparationRuns();
				return null;
			});
			assertClean(capture);
		}
	}

	@Test
	void aGuardReadiesWhileTheThiefIsNot() throws Exception {
		try (var capture = new LogCapture()) {
			setUp(true);
			send(guard, COMMAND);
			PaperServer.onMain(() -> {
				assertTrue(ready(guard.player()), "the guard is not ready");
				assertFalse(ready(thief.player()), "the thief became ready");
				assertPreparationRuns();
				return null;
			});
			assertClean(capture);
		}
	}

	@Test
	void aSecondRunUnReadies() throws Exception {
		try (var capture = new LogCapture()) {
			setUp(true);
			send(thief, COMMAND);
			PaperServer.onMain(() -> {
				assertTrue(ready(thief.player()), "the first run did not ready the thief");
				return null;
			});
			send(thief, COMMAND);
			PaperServer.onMain(() -> {
				assertFalse(ready(thief.player()), "the thief is still ready");
				assertPreparationRuns();
				return null;
			});
			assertClean(capture);
		}
	}

	@Test
	void theLastReadyPlayerEndsPreparation() throws Exception {
		try (var capture = new LogCapture()) {
			setUp(true);
			send(guard, COMMAND);
			send(thief, COMMAND);
			PaperServer.onMain(() -> {
				assertNull(game().getPhaseMachine().getPhase(Vi6PhasePreparation.class), "preparation still runs");
				assertNotNull(game().getPhaseMachine().getPhase(Vi6PhaseInfiltration.class), "no infiltration");
				return null;
			});
			assertClean(capture);
		}
	}

	@Test
	void theMenuItemsShowTheCommandState() throws Exception {
		try (var capture = new LogCapture()) {
			setUp(true);
			send(thief, COMMAND);
			PaperServer.onMain(() -> {
				assertMenu(thief.player(), true);
				return null;
			});
			send(thief, COMMAND);
			PaperServer.onMain(() -> {
				assertMenu(thief.player(), false);
				return null;
			});
			assertClean(capture);
		}
	}

	@Test
	void gameReadyInPreparationChangesNothing() throws Exception {
		try (var capture = new LogCapture()) {
			setUp(true);
			send(thief, "game ready");
			PaperServer.onMain(() -> {
				assertFalse(ready(thief.player()), "/game ready readied the thief in preparation");
				assertPreparationRuns();
				return null;
			});
			send(thief, COMMAND);
			send(thief, "game ready");
			PaperServer.onMain(() -> {
				assertTrue(ready(thief.player()), "/game ready un-readied the thief in preparation");
				assertPreparationRuns();
				return null;
			});
			assertClean(capture);
		}
	}

	@Test
	void aLobbyRunIsRefused() throws Exception {
		try (var capture = new LogCapture()) {
			setUp(false);
			PaperServer.onMain(() -> {
				assertNotNull(game().getPhaseMachine().getPhase(Vi6PhaseLobby.class), "not in the lobby");
				return null;
			});
			send(guard, COMMAND);
			send(thief, COMMAND);
			PaperServer.onMain(() -> {
				assertFalse(lobbyReady(guard.player()), "a lobby run readied the guard in the lobby");
				assertFalse(lobbyReady(thief.player()), "a lobby run readied the thief in the lobby");
				return null;
			});
			Vi6TestHooks.goToPreparation();
			PaperServer.onMain(() -> {
				assertFalse(ready(guard.player()), "a lobby run carried into preparation for the guard");
				assertFalse(ready(thief.player()), "a lobby run carried into preparation for the thief");
				assertPreparationRuns();
				return null;
			});
			assertClean(capture);
		}
	}

	@Test
	void anInfiltrationRunIsRefused() throws Exception {
		try (var capture = new LogCapture()) {
			setUp(false);
			Vi6TestHooks.goToInfiltration(null);
			send(thief, COMMAND);
			PaperServer.onMain(() -> {
				assertNotNull(game().getPhaseMachine().getPhase(Vi6PhaseInfiltration.class), "infiltration ended");
				return null;
			});
			assertClean(capture);
		}
	}

	@Test
	void aPlayerOutsideTheGameIsRefused() throws Exception {
		try (var capture = new LogCapture()) {
			setUp(true);
			send(guard, COMMAND);
			send(outsider, COMMAND);
			PaperServer.onMain(() -> {
				assertTrue(
						Ioc.resolve(WrappingModule.class)
								.getWrapperOptional(outsider.player(), PreparationPhasePlayerWrapper.class).isEmpty(),
						"the outsider joined preparation");
				assertTrue(ready(guard.player()), "the outsider changed the guard");
				assertFalse(ready(thief.player()), "the outsider readied the thief");
				assertPreparationRuns();
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

	/** Main thread only. */
	private static boolean ready(Player player) {
		return Ioc.resolve(WrappingModule.class).getWrapper(player, PreparationPhasePlayerWrapper.class)
				.isReadyForNextPhase();
	}

	/** Main thread only. */
	private static boolean lobbyReady(Player player) {
		return Ioc.resolve(WrappingModule.class).getWrapper(player, LobbyPhasePlayerWrapper.class)
				.isReadyForNextPhase();
	}

	/**
	 * Asserts the shop menu's drawn inventory holds the "Prêt" item and not the "En
	 * attente" one if {@code ready}, the reverse otherwise; the inventory is only
	 * filled by a redraw. Main thread only.
	 */
	private static void assertMenu(Player player, boolean ready) {
		var inventory = Ioc.resolve(WrappingModule.class).getWrapper(player, PreparationPhasePlayerWrapper.class)
				.getMenu().getInventory();
		assertEquals(ready, inventory.contains(Material.EMERALD_BLOCK), "the \"Prêt\" item is not drawn as expected");
		assertEquals(!ready, inventory.contains(Material.REDSTONE_BLOCK),
				"the \"En attente\" item is not drawn as expected");
	}

	/** Main thread only. */
	private static void assertPreparationRuns() {
		assertNotNull(game().getPhaseMachine().getPhase(Vi6PhasePreparation.class), "preparation ended");
	}

	private static void assertClean(LogCapture capture) {
		assertEquals(List.of(), capture.failures(), "an exception was logged");
		assertEquals(List.of(), capture.errors(), "an error was logged");
	}
}
