package fr.nekotine.vi6clean.it;

import static fr.nekotine.vi6clean.it.Vi6Asserts.assertCleanLog;
import static fr.nekotine.vi6clean.it.Vi6TestHooks.GAME_TIMEOUT_TICKS;
import static fr.nekotine.vi6clean.it.Vi6TestHooks.awaitGameEnded;
import static fr.nekotine.vi6clean.it.Vi6TestHooks.game;
import static fr.nekotine.vi6clean.it.Vi6TestHooks.run;
import static fr.nekotine.vi6clean.it.Vi6TestHooks.shortTimersWithoutKoths;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import fr.nekotine.core.bot.Bot;
import fr.nekotine.core.bot.BotRoster;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.vi6clean.bot.BotTeamIntent;
import fr.nekotine.vi6clean.bot.Vi6Bots;
import fr.nekotine.vi6clean.bot.Vi6Run;
import fr.nekotine.vi6clean.game.phase.Vi6PhaseInfiltration;
import fr.nekotine.vi6clean.game.phase.Vi6PhaseLobby;
import fr.nekotine.vi6clean.game.phase.Vi6PhasePreparation;
import fr.xxgoldenbluexx.paperjunitserver.PaperServer;

/**
 * {@code /bot run} starts an unattended run from a clean roster; its games loop
 * and each logs one summary line once back in the lobby. {@link Vi6SoakIT}
 * checks the phases of a game, a second run's clean start and seed replay.
 */
class Vi6BotRunIT {

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
	void runsLoop() throws Exception {
		Vi6TestHooks.reset();
		shortTimersWithoutKoths();
		try (var capture = new LogCapture()) {
			var started = run("bot run 4 42");
			assertEquals(1, started.size(), "start output: " + started);
			assertTrue(started.getFirst().startsWith("Run démarré: 4 bot(s)"), started.getFirst());
			assertTrue(capture.messages().contains("Run started: requested=4, added=4, seed=42"),
					"no start line in " + capture.messages());
			PaperServer.onMain(() -> {
				var roster = Ioc.resolve(BotRoster.class).list();
				assertEquals(List.of("Bot_1", "Bot_2", "Bot_3", "Bot_4"), roster.stream().map(Bot::name).toList());
				var bots = Ioc.resolve(ModuleManager.class).get(Vi6Bots.class);
				roster.forEach(b -> assertEquals(BotTeamIntent.ANY, bots.getIntent(b), b.name() + " intent"));
				return null;
			});

			awaitGameEnded(capture, 1);
			// The games loop on their own
			awaitGameEnded(capture, 2);

			assertCleanLog(capture);
		}
	}

	@Test
	void aResetMidGameAbortsTheRunSilently() throws Exception {
		Vi6TestHooks.reset();
		shortTimersWithoutKoths();
		try (var capture = new LogCapture()) {
			run("bot run 4 42");
			PaperServer.awaitUntil(() -> game().getPhaseMachine().getPhase(Vi6PhaseInfiltration.class) != null,
					GAME_TIMEOUT_TICKS);
			Vi6TestHooks.reset();
			// Longer than the shortened lost timer and end-spectator time
			PaperServer.awaitTicks(120);
			assertTrue(capture.messages().stream().noneMatch(m -> m.startsWith("Game ended:")),
					"aborted game logged: " + capture.messages());
			PaperServer.onMain(() -> {
				assertFalse(Ioc.resolve(ModuleManager.class).get(Vi6Run.class).isActive(), "run still active");
				return null;
			});
			assertCleanLog(capture);
		}
	}

	@Test
	void wrongCountsAreRefused() throws Exception {
		Vi6TestHooks.reset();
		try (var capture = new LogCapture()) {
			for (var count : List.of(5, 2)) {
				var out = run("bot run " + count);
				assertEquals(List.of("Run refusé: " + count + " bots demandés, 4 ou 6 seulement"), out);
			}
			assertNothingStarted(capture);
			PaperServer.onMain(() -> {
				assertNotNull(game().getPhaseMachine().getPhase(Vi6PhaseLobby.class), "left the lobby");
				assertEquals(List.of(), Ioc.resolve(BotRoster.class).list(), "the roster changed");
				return null;
			});
		}
	}

	@Test
	void aRunIsRefusedPastTheLobby() throws Exception {
		Vi6TestHooks.reset();
		var hooks = Vi6TestHooks.spawnBots(2);
		Vi6TestHooks.putOnTeams(List.of(hooks.get(0).player()), List.of(hooks.get(1).player()));
		Vi6TestHooks.forceKoths();
		Vi6TestHooks.goToPreparation();
		try (var capture = new LogCapture()) {
			assertEquals(List.of("Run refusé: la partie n'est pas dans le lobby"), run("bot run 4"));
			assertNothingStarted(capture);
			PaperServer.onMain(() -> {
				assertNotNull(game().getPhaseMachine().getPhase(Vi6PhasePreparation.class), "left preparation");
				assertEquals(hooks, Ioc.resolve(BotRoster.class).list(), "the roster changed");
				return null;
			});
		}
	}

	@Test
	void gameLobbyTwiceKeepsOneHandler() throws Exception {
		Vi6TestHooks.reset();
		PaperServer.onMain(() -> {
			assertTrue(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "game lobby"));
			assertTrue(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "game lobby"));
			var game = game();
			var handlers = Arrays.stream(PlayerItemHeldEvent.getHandlerList().getRegisteredListeners())
					.filter(l -> l.getListener() == game).count();
			assertEquals(1, handlers, "Vi6Game handlers for PlayerItemHeldEvent");
			return null;
		});
	}

	private static void assertNothingStarted(LogCapture capture) throws Exception {
		assertTrue(capture.messages().stream().noneMatch(m -> m.startsWith("Run started:")),
				"a run started: " + capture.messages());
		assertCleanLog(capture);
	}
}
