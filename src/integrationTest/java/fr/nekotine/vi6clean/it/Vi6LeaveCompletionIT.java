package fr.nekotine.vi6clean.it;

import static fr.nekotine.vi6clean.it.Vi6Asserts.assertCleanLog;
import static fr.nekotine.vi6clean.it.Vi6TestHooks.game;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import fr.nekotine.core.bot.BotRoster;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.game.phase.Vi6PhaseInfiltration;
import fr.nekotine.vi6clean.game.phase.Vi6PhaseLobby;
import fr.nekotine.vi6clean.game.phase.Vi6PhasePreparation;
import fr.nekotine.vi6clean.wrapper.LobbyPhasePlayerWrapper;
import fr.nekotine.vi6clean.wrapper.PreparationPhasePlayerWrapper;
import fr.xxgoldenbluexx.paperjunitserver.PaperServer;

/**
 * A player leaving a lobby or preparation where everyone else is ready lets
 * that phase complete on the next tick; an emptied lobby stays.
 */
class Vi6LeaveCompletionIT {

	@BeforeAll
	static void waitForAsyncConfigLoad() throws Exception {
		// ConfigManager.setupAsync() loads config.yml off the main thread
		PaperServer.awaitTicks(40);
	}

	@AfterEach
	void reset() throws Exception {
		Vi6TestHooks.reset();
	}

	@Test
	void aLeaveUnblocksTheLobby() throws Exception {
		Vi6TestHooks.reset();
		var bots = Vi6TestHooks.spawnBots(3);
		Vi6TestHooks.putOnTeams(List.of(bots.get(0).player()), List.of(bots.get(1).player(), bots.get(2).player()));
		Vi6TestHooks.forceKoths();
		try (var capture = new LogCapture()) {
			PaperServer.onMain(() -> {
				var wrapping = Ioc.resolve(WrappingModule.class);
				for (var bot : bots.subList(0, 2)) {
					wrapping.getWrapper(bot.player(), LobbyPhasePlayerWrapper.class).setReadyForNextPhase(true);
				}
				assertNotNull(game().getPhaseMachine().getPhase(Vi6PhaseLobby.class), "the lobby completed early");
				Ioc.resolve(BotRoster.class).remove(bots.get(2));
				assertNotNull(game().getPhaseMachine().getPhase(Vi6PhaseLobby.class),
						"the lobby completed in the leave's tick");
				return null;
			});
			Vi6TestHooks.awaitPhase(Vi6PhasePreparation.class, 5);
			assertCleanLog(capture);
		}
	}

	@Test
	void aLeaveUnblocksPreparation() throws Exception {
		Vi6TestHooks.reset();
		var bots = Vi6TestHooks.spawnBots(3);
		Vi6TestHooks.putOnTeams(List.of(bots.get(0).player()), List.of(bots.get(1).player(), bots.get(2).player()));
		Vi6TestHooks.forceKoths();
		Vi6TestHooks.goToPreparation();
		try (var capture = new LogCapture()) {
			PaperServer.onMain(() -> {
				var wrapping = Ioc.resolve(WrappingModule.class);
				for (var bot : bots.subList(0, 2)) {
					wrapping.getWrapper(bot.player(), PreparationPhasePlayerWrapper.class).setReadyForNextPhase(true);
				}
				assertNotNull(game().getPhaseMachine().getPhase(Vi6PhasePreparation.class),
						"preparation completed early");
				Ioc.resolve(BotRoster.class).remove(bots.get(2));
				assertNotNull(game().getPhaseMachine().getPhase(Vi6PhasePreparation.class),
						"preparation completed in the leave's tick");
				return null;
			});
			Vi6TestHooks.awaitPhase(Vi6PhaseInfiltration.class, 5);
			assertCleanLog(capture);
		}
	}

	@Test
	void aLeaveChecksOnlyThePhaseItLeft() throws Exception {
		Vi6TestHooks.reset();
		var bots = Vi6TestHooks.spawnBots(3);
		Vi6TestHooks.putOnTeams(List.of(bots.get(0).player()), List.of(bots.get(1).player(), bots.get(2).player()));
		Vi6TestHooks.forceKoths();
		try (var capture = new LogCapture()) {
			PaperServer.onMain(() -> {
				var wrapping = Ioc.resolve(WrappingModule.class);
				for (var bot : bots.subList(0, 2)) {
					wrapping.getWrapper(bot.player(), LobbyPhasePlayerWrapper.class).setReadyForNextPhase(true);
				}
				// The lobby ends in the leave's tick: its deferred check must not run
				Ioc.resolve(BotRoster.class).remove(bots.get(2));
				var machine = game().getPhaseMachine();
				machine.goTo(Vi6PhasePreparation.class, null);
				assertNotNull(machine.getPhase(Vi6PhasePreparation.class), "preparation did not start");
				return null;
			});
			PaperServer.awaitTicks(5);
			PaperServer.onMain(() -> {
				assertNotNull(game().getPhaseMachine().getPhase(Vi6PhasePreparation.class),
						"the old lobby's check moved the game on");
				return null;
			});
			assertCleanLog(capture);
		}
	}

	@Test
	void anEmptiedLobbyStays() throws Exception {
		Vi6TestHooks.reset();
		var bots = Vi6TestHooks.spawnBots(2);
		Vi6TestHooks.putOnTeams(List.of(bots.get(0).player()), List.of(bots.get(1).player()));
		Vi6TestHooks.forceKoths();
		try (var capture = new LogCapture()) {
			PaperServer.onMain(() -> {
				Ioc.resolve(WrappingModule.class).getWrapper(bots.get(0).player(), LobbyPhasePlayerWrapper.class)
						.setReadyForNextPhase(true);
				var roster = Ioc.resolve(BotRoster.class);
				bots.forEach(roster::remove);
				return null;
			});
			PaperServer.awaitTicks(5);
			PaperServer.onMain(() -> {
				var machine = game().getPhaseMachine();
				assertNotNull(machine.getPhase(Vi6PhaseLobby.class), "left the empty lobby");
				assertNull(machine.getPhase(Vi6PhasePreparation.class), "an empty game went to preparation");
				assertTrue(game().getPlayerList().isEmpty(), "players left: " + game().getPlayerList());
				return null;
			});
			assertCleanLog(capture);
		}
	}
}
