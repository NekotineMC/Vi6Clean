package fr.nekotine.vi6clean.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Files;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.logging.Level;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import fr.nekotine.core.bot.Bot;
import fr.nekotine.core.bot.BotRoster;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.resourcepack.ResourcePackModule;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.game.Vi6Game;
import fr.nekotine.vi6clean.game.phase.Vi6PhaseLobby;
import fr.nekotine.vi6clean.wrapper.LobbyPhasePlayerWrapper;
import fr.xxgoldenbluexx.paperjunitserver.PaperServer;
import net.kyori.adventure.resource.ResourcePackInfo;

/**
 * A bot joins and leaves the Vi6 lobby through the same listeners as a human:
 * the lobby adds it to the game, the global phase removes it on quit.
 */
class Vi6BotLobbyIT {

	private static final String NAME = "Bot_Lobby";

	@AfterEach
	void removeBots() throws Exception {
		PaperServer.onMain(() -> {
			var roster = Ioc.resolve(BotRoster.class);
			if (roster == null || !roster.isAvailable()) {
				return null;
			}
			for (var bot : roster.list()) {
				roster.remove(bot);
			}
			return null;
		});
	}

	@Test
	void selfTestPassedAtEnable() throws Exception {
		// A lazy resolve from a test would also log the pass line, but after "Done"
		var log = Files.readString(PaperServer.plugin().getDataFolder().toPath().toAbsolutePath().getParent()
				.getParent().resolve("logs/latest.log"));
		var passed = log.indexOf("Bots self-test passed");
		var done = log.indexOf("Done (");
		assertTrue(passed >= 0, "no self-test pass line in the server log");
		assertTrue(done >= 0, "no Done line in the server log");
		assertTrue(passed < done, "the self-test ran after startup: Vi6Main did not resolve the roster at enable");
		assertTrue(PaperServer.onMain(() -> Ioc.resolve(BotRoster.class).isAvailable()),
				"bots roster unavailable: the self-test failed at enable");
	}

	@Test
	void botJoinsAndLeavesTheLobbyLikeAPlayer() throws Exception {
		try (var capture = new LogCapture()) {
			PaperServer.onMain(() -> {
				Bukkit.getLogger().log(Level.SEVERE, "Vi6BotLobbyIT capture check", new IllegalStateException());
				return null;
			});
			assertEquals(1, capture.failures().size(), "the log capture does not see server errors");
			capture.clear();

			// The real pack is fetched from GitHub in the background: add one so the
			// join listener sends a pack even offline.
			PaperServer.onMain(() -> {
				Ioc.resolve(ModuleManager.class).get(ResourcePackModule.class)
						.addMandatoryResourcePack(ResourcePackInfo.resourcePackInfo(UUID.randomUUID(),
								URI.create("http://localhost/vi6-it-pack.zip"), "0".repeat(40)));
				return null;
			});

			// JOIN
			var bot = PaperServer.onMain(() -> Ioc.resolve(BotRoster.class).spawn(NAME,
					Bukkit.getWorlds().getFirst().getSpawnLocation().add(0.5, 0, 0.5)));
			PaperServer.awaitTicks(5);
			PaperServer.onMain(() -> {
				var game = Ioc.resolve(Vi6Game.class);
				var player = bot.player();
				assertTrue(player.isOnline(), "bot was disconnected after joining");
				assertNotNull(game.getPhaseMachine().getPhase(Vi6PhaseLobby.class), "game is not in the lobby");
				assertTrue(game.getPlayerList().contains(player), "bot not in Vi6Game.getPlayerList()");
				assertTrue(game.getGuards().contains(player) || game.getThiefs().contains(player), "bot is in no team");
				assertNotNull(game.getScoreboard().getEntryTeam(NAME), "bot has no scoreboard team");
				assertTrue(sidebar().getScore(NAME).isScoreSet(), "bot not on the lobby sidebar");
				assertTrue(Ioc.resolve(WrappingModule.class).getWrapperOptional(player, LobbyPhasePlayerWrapper.class)
						.isPresent(), "bot has no lobby wrapper");
				assertTrue(player.getInventory().contains(Material.BEACON), "bot did not get the lobby menu item");
				assertEquals(game.getWorld(), player.getWorld(), "bot was not teleported to the game world");
				return null;
			});
			assertEquals(List.of(), capture.failures(), "a listener threw while the bot joined");

			// QUIT
			PaperServer.onMain(() -> {
				Ioc.resolve(BotRoster.class).remove(bot);
				return null;
			});
			PaperServer.awaitTicks(2);
			PaperServer.onMain(() -> {
				var game = Ioc.resolve(Vi6Game.class);
				var player = bot.player();
				assertFalse(player.isOnline(), "bot still online after removal");
				assertFalse(game.getPlayerList().contains(player), "bot still in Vi6Game.getPlayerList()");
				assertFalse(game.getGuards().contains(player), "bot still a guard");
				assertFalse(game.getThiefs().contains(player), "bot still a thief");
				assertNull(game.getScoreboard().getEntryTeam(NAME), "bot still in a scoreboard team");
				assertFalse(sidebar().getScore(NAME).isScoreSet(), "bot still on the lobby sidebar");
				assertTrue(Ioc.resolve(BotRoster.class).list().stream().map(Bot::name).noneMatch(NAME::equals),
						"bot still listed by the roster");
				return null;
			});
			assertEquals(List.of(), capture.failures(), "a listener threw while the bot left");
		}
	}

	private static org.bukkit.scoreboard.Objective sidebar() {
		return Ioc.resolve(Vi6Game.class).getPhaseMachine().getPhase(Vi6PhaseLobby.class).getSidebarObjective();
	}

	/** Server log lines that carry an exception, captured while open. */
	private static final class LogCapture extends AbstractAppender implements AutoCloseable {
		private final Queue<String> failures = new ConcurrentLinkedQueue<>();
		private final LoggerContext context;

		LogCapture() {
			super("Vi6BotLobbyIT-capture", null, null, true, Property.EMPTY_ARRAY);
			context = (LoggerContext) LogManager.getContext(Bukkit.class.getClassLoader(), false);
			start();
			context.getConfiguration().getRootLogger().addAppender(this, null, null);
			context.updateLoggers();
		}

		@Override
		public void append(LogEvent evt) {
			var thrown = evt.getThrown();
			if (thrown == null) {
				return;
			}
			failures.add(evt.getMessage().getFormattedMessage() + " " + thrown);
		}

		List<String> failures() {
			return List.copyOf(failures);
		}

		void clear() {
			failures.clear();
		}

		@Override
		public void close() {
			context.getConfiguration().getRootLogger().removeAppender(getName());
			context.updateLoggers();
			stop();
		}
	}
}
