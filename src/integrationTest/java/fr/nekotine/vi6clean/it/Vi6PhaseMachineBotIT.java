package fr.nekotine.vi6clean.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Level;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BlockVector;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;

import fr.nekotine.core.bot.Bot;
import fr.nekotine.core.bot.BotRoster;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.bot.Vi6Run;
import fr.nekotine.vi6clean.configuration.ConfigManager;
import fr.nekotine.vi6clean.constant.InMapState;
import fr.nekotine.vi6clean.constant.Vi6Team;
import fr.nekotine.vi6clean.game.Vi6Game;
import fr.nekotine.vi6clean.game.Vi6Game.ForcedKoth;
import fr.nekotine.vi6clean.game.phase.Vi6PhaseInMap;
import fr.nekotine.vi6clean.game.phase.Vi6PhaseInfiltration;
import fr.nekotine.vi6clean.game.phase.Vi6PhaseLobby;
import fr.nekotine.vi6clean.game.phase.Vi6PhasePreparation;
import fr.nekotine.vi6clean.map.Vi6Map;
import fr.nekotine.vi6clean.map.artefact.Artefact;
import fr.nekotine.vi6clean.map.artefact.ArtefactStealEvent;
import fr.nekotine.vi6clean.map.koth.Koth;
import fr.nekotine.vi6clean.wrapper.InMapPhasePlayerWrapper;
import fr.xxgoldenbluexx.paperjunitserver.PaperServer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Bots drive the phase machine through real client inputs: a full round ended
 * by the lost timer, a KoTH capture, and an artefact stolen then carried out,
 * the round ending when the last thief escapes.
 */
class Vi6PhaseMachineBotIT {

	private static final int TOLERANCE_TICKS = 5;

	/** Ticks for a control to reach the server and the bot to stop moving. */
	private static final int SETTLE_TICKS = 5;

	/** Ticks the forward key is held for a step. */
	private static final int STEP_TICKS = 3;

	/** {@code Koth} and {@code Artefact} capture amount, one per tick per thief. */
	private static final int CAPTURE_TICKS = 200;

	private static final int CAPTURE_MARGIN_TICKS = 20;

	/** {@code delay-between-capture} and {@code delay-before-escape} here. */
	private static final Duration SHORT_DELAY = Duration.ofMillis(500);

	private static final int SHORT_DELAY_TICKS = 10;

	private static final String RESULT_PREFIX = "Round ended: ";

	/** The run summary of a round where one artefact was stolen and carried out. */
	private static final String GAME_ENDED_PREFIX = "Game ended: game=1, reason=all-thieves-left, stolen=1, escaped=1, duration=";

	/** {@code InMapPhasePlayerWrapper}'s 3 s death delay. */
	private static final int DEATH_DELAY_TICKS = 60;

	// Each test starts with a reset; this one keeps the next test class clean
	@AfterEach
	void resetForOtherTests() throws Exception {
		Vi6TestHooks.reset();
	}

	@RepeatedTest(3)
	void fullRoundFromTheLobbyEndsOnTheLostTimer() throws Exception {
		Vi6TestHooks.reset();
		try (var capture = new LogCapture()) {
			var bots = Vi6TestHooks.spawnBots(4);
			var guards = players(bots.subList(0, 2));
			var thieves = players(bots.subList(2, 4));
			Vi6TestHooks.putOnTeams(guards, thieves);
			Vi6TestHooks.forceKoths();
			var artefactNames = PaperServer.onMain(() -> {
				var config = Ioc.resolve(ConfigManager.class).getConfig().game();
				config.preparation().duration(Duration.ofSeconds(2));
				config.infiltration().lostDuration(Duration.ofSeconds(3));
				config.infiltration().endSpectatorTime(Duration.ofMillis(500));
				return Vi6TestHooks.currentMap().getArtefacts().keySet().stream().sorted().toList();
			});

			// Only the bots act from here: each readies up, as a client does
			PaperServer.onMain(() -> {
				assertNotNull(game().getPhaseMachine().getPhase(Vi6PhaseLobby.class), "not in the lobby");
				bots.forEach(bot -> bot.sendCommand("game ready"));
				return null;
			});
			int preparationStart = awaitPhase(Vi6PhasePreparation.class, 100);
			int infiltrationStart = awaitPhase(Vi6PhaseInfiltration.class, 40 + 100);
			PaperServer.onMain(() -> {
				assertNotNull(game().getPhaseMachine().getPhase(Vi6PhaseInMap.class), "no in-map phase");
				for (var thief : thieves) {
					assertEquals(InMapState.ENTERING, inMapWrapper(thief).getState(),
							thief.getName() + " state at infiltration");
				}
				return null;
			});
			PaperServer.awaitTicks(30);
			PaperServer.onMain(() -> {
				if (game().getPhaseMachine().getPhase(Vi6PhaseInfiltration.class) != null) {
					for (var thief : thieves) {
						assertFalse(inMapWrapper(thief).isInside(), thief.getName() + " entered the map");
					}
				}
				return null;
			});
			int lobbyStart = awaitPhase(Vi6PhaseLobby.class, 70 + 100);

			assertAround(40, infiltrationStart - preparationStart, "preparation length");
			assertAround(60 + 10, lobbyStart - infiltrationStart, "infiltration lost timer + end spectator time");
			assertEquals(expectedResult("lost-timer", artefactNames, Map.of()), roundResult(capture),
					"round result line");
			// A run would log its line the tick after the lobby
			PaperServer.awaitTicks(2);
			assertTrue(capture.messages().stream().noneMatch(m -> m.startsWith("Game ended:")),
					"a summary line without a run: " + capture.messages());
			assertEquals(List.of(), capture.failures(), "an exception was logged");
			assertEquals(List.of(), capture.errors(), "an error was logged");
		}
	}

	@RepeatedTest(3)
	void thiefCapturesAForcedKoth() throws Exception {
		Vi6TestHooks.reset();
		try (var capture = new LogCapture()) {
			var bots = Vi6TestHooks.spawnBots(2);
			var thiefBot = bots.get(1);
			var thief = thiefBot.player();
			Vi6TestHooks.putOnTeams(List.of(bots.get(0).player()), List.of(thief));
			var kothName = PaperServer
					.onMain(() -> firstZoneWithoutGuard(Vi6TestHooks.currentMap().getKoths(), Koth::getBoundingBox));
			var spawnName = PaperServer.onMain(Vi6PhaseMachineBotIT::spawnWithEntranceAndExit);
			Vi6TestHooks.forceKoths(new ForcedKoth(kothName, "regen"));
			Vi6TestHooks.goToInfiltration(spawnName);

			enterMap(thiefBot, spawnName);
			var koth = PaperServer.onMain(() -> map().getKoths().get(kothName));
			PaperServer.onMain(() -> {
				assertNotNull(koth.getEffect(), "KoTH " + kothName + " not set up");
				assertEquals(Vi6Team.GUARD, koth.getOwningTeam(), "KoTH owner before the thief steps in");
				return null;
			});
			int stepStart = PaperServer.onMain(Bukkit::getCurrentTick);
			stepInto(thiefBot, koth.getBoundingBox(), null);
			PaperServer.onMain(() -> {
				assertTrue(koth.getBoundingBox().contains(thief.getLocation().toVector()), "thief not in the KoTH box "
						+ koth.getBoundingBox() + " but at " + thief.getLocation().toVector());
				assertEquals(Vi6Team.GUARD, koth.getOwningTeam(), "KoTH captured right after the step");
				return null;
			});

			var capturedAt = new int[]{-1};
			PaperServer.awaitUntil(() -> {
				if (koth.getOwningTeam() == Vi6Team.THIEF) {
					capturedAt[0] = Bukkit.getCurrentTick();
					return true;
				}
				return false;
			}, CAPTURE_TICKS + CAPTURE_MARGIN_TICKS);
			var elapsed = capturedAt[0] - stepStart;
			assertTrue(elapsed >= CAPTURE_TICKS - TOLERANCE_TICKS && elapsed <= CAPTURE_TICKS + CAPTURE_MARGIN_TICKS,
					"KoTH captured " + elapsed + " ticks after the step, expected about " + CAPTURE_TICKS);
			assertEquals(List.of(), capture.failures(), "an exception was logged");
			assertEquals(List.of(), capture.errors(), "an error was logged");
		}
	}

	@RepeatedTest(3)
	void thiefStealsAnArtefactAndBothThievesEscape() throws Exception {
		Vi6TestHooks.reset();
		try (var capture = new LogCapture()) {
			var bots = Vi6TestHooks.spawnBots(3);
			var botA = bots.get(1);
			var botB = bots.get(2);
			var thiefA = botA.player();
			var thiefB = botB.player();
			Vi6TestHooks.putOnTeams(List.of(bots.get(0).player()), List.of(thiefA, thiefB));
			Vi6TestHooks.forceKoths();
			var artefactName = PaperServer.onMain(() -> {
				var config = Ioc.resolve(ConfigManager.class).getConfig().game().infiltration();
				config.lostDuration(Duration.ofSeconds(30));
				config.delayBetweenCapture(SHORT_DELAY);
				config.delayBeforeEscape(SHORT_DELAY);
				config.endSpectatorTime(Duration.ofMillis(500));
				return firstZoneWithoutGuard(Vi6TestHooks.currentMap().getArtefacts(), Artefact::getBoundingBox);
			});
			var artefactNames = PaperServer
					.onMain(() -> Vi6TestHooks.currentMap().getArtefacts().keySet().stream().sorted().toList());
			var spawnName = PaperServer.onMain(Vi6PhaseMachineBotIT::spawnWithEntranceAndExit);
			Vi6TestHooks.goToInfiltration(spawnName);
			// A run active when the round ends logs its summary line
			PaperServer.onMain(() -> {
				Ioc.resolve(ModuleManager.class).get(Vi6Run.class).begin(3, 3, 0L);
				return null;
			});

			var steals = new ConcurrentLinkedQueue<String>();
			var recorder = new StealRecorder(steals);
			PaperServer.onMain(() -> {
				Bukkit.getPluginManager().registerEvents(recorder, Ioc.resolve(JavaPlugin.class));
				return null;
			});
			try {
				// Thief A enters, waits until it can steal, then stands in the artefact box
				enterMap(botA, spawnName);
				PaperServer.awaitUntil(() -> inMapWrapper(thiefA).canCaptureArtefact(), SHORT_DELAY_TICKS + 20);
				var artefact = PaperServer.onMain(() -> map().getArtefacts().get(artefactName));
				var box = artefact.getBoundingBox();
				int stepStart = PaperServer.onMain(Bukkit::getCurrentTick);
				stepInto(botA, box, artefact.getBlockPosition());
				PaperServer.onMain(() -> {
					assertTrue(box.contains(thiefA.getLocation().toVector()),
							"thief A not in the artefact box " + box + " but at " + thiefA.getLocation().toVector());
					for (var guard : game().getGuards()) {
						assertFalse(box.contains(guard.getLocation().toVector()), "a guard is in the artefact box");
					}
					assertFalse(artefact.isCaptured(), "artefact stolen right after the step");
					return null;
				});

				// Just before the steal, the lost timer has run for a while
				PaperServer.awaitUntil(() -> Bukkit.getCurrentTick() >= stepStart + CAPTURE_TICKS - 20, 300);
				var progressBefore = PaperServer.onMain(() -> thiefBarProgress(thiefA));
				assertEquals(List.of(), List.copyOf(steals), "artefact stolen too early");
				assertTrue(progressBefore > 0.25f, "thief boss bar before the steal: " + progressBefore);

				PaperServer.awaitUntil(() -> !steals.isEmpty(), 20 + CAPTURE_MARGIN_TICKS);
				// The steal restarts the lost timer
				PaperServer.awaitUntil(() -> thiefBarProgress(thiefA) < 0.1f, 30);
				assertEquals(List.of(thiefA.getName() + " stole " + artefactName), List.copyOf(steals), "steal events");
				PaperServer.onMain(() -> {
					assertEquals(2, thiefScore(artefactName), "thief board score after the steal");
					assertTrue(guardTeamHas("guardUnknown", artefactName), "guard board entry not in guardUnknown");
					return null;
				});

				// A escapes once it may leave again
				PaperServer.awaitUntil(() -> inMapWrapper(thiefA).canLeaveMap(), SHORT_DELAY_TICKS + 20);
				var exit = PaperServer.onMain(() -> map().getExits().get(spawnName));
				stepInto(botA, exit, null);
				PaperServer.awaitUntil(() -> inMapWrapper(thiefA).hasLeft(), 20);
				PaperServer.onMain(() -> {
					assertEquals(1, thiefScore(artefactName), "thief board score after A escaped");
					assertTrue(guardTeamHas("guardEscaped", artefactName), "guard board entry not in guardEscaped");
					assertNotNull(game().getPhaseMachine().getPhase(Vi6PhaseInfiltration.class),
							"round ended with thief B still out of the map");
					return null;
				});

				// B enters, then escapes: every thief has left
				enterMap(botB, spawnName);
				PaperServer.awaitUntil(() -> inMapWrapper(thiefB).canLeaveMap(), SHORT_DELAY_TICKS + 20);
				stepInto(botB, exit, null);
				awaitPhase(Vi6PhaseLobby.class, 10 + 60);
				assertEquals(1, steals.size(), "steal events: " + steals);
			} finally {
				PaperServer.onMain(() -> {
					HandlerList.unregisterAll(recorder);
					return null;
				});
			}
			assertEquals(expectedResult("all-thieves-left", artefactNames, Map.of(artefactName, "escaped")),
					roundResult(capture), "round result line");
			PaperServer.awaitUntil(() -> capture.messages().stream().anyMatch(m -> m.startsWith(GAME_ENDED_PREFIX)),
					20);
			var summaries = capture.messages().stream().filter(m -> m.startsWith("Game ended:")).toList();
			assertEquals(1, summaries.size(), "summary lines: " + summaries);
			assertTrue(summaries.getFirst().startsWith(GAME_ENDED_PREFIX), summaries.getFirst());
			assertEquals(List.of(), capture.failures(), "an exception was logged");
			assertEquals(List.of(), capture.errors(), "an error was logged");
		}
	}

	@RepeatedTest(3)
	void lastThiefOutDiesWithAStolenArtefact() throws Exception {
		Vi6TestHooks.reset();
		try (var capture = new LogCapture()) {
			var round = stealRound(1);
			var thiefA = round.thieves().get(0);
			steal(round, thiefA);
			int deathTick = kill(thiefA.player());
			awaitPhase(Vi6PhaseLobby.class, DEATH_DELAY_TICKS + 10 + 40);

			assertResultAfterDeath(capture, round, deathTick);
			assertEquals(List.of(), capture.failures(), "an exception was logged");
			assertEquals(List.of(), capture.errors(), "an error was logged");
		}
	}

	@RepeatedTest(3)
	void otherThiefEscapesDuringTheDeathDelay() throws Exception {
		Vi6TestHooks.reset();
		try (var capture = new LogCapture()) {
			var round = stealRound(2);
			var botA = round.thieves().get(0);
			var botB = round.thieves().get(1);
			// B waits inside until it may leave
			enterMap(botB, round.spawn());
			PaperServer.awaitUntil(() -> inMapWrapper(botB.player()).canLeaveMap(), SHORT_DELAY_TICKS + 20);
			steal(round, botA);
			int deathTick = kill(botA.player());

			// B leaves within the death delay: every thief has left
			var exit = PaperServer.onMain(() -> map().getExits().get(round.spawn()));
			stepInto(botB, exit, null);
			PaperServer.onMain(() -> {
				assertTrue(inMapWrapper(botB.player()).hasLeft(), "thief B did not leave");
				assertTrue(Bukkit.getCurrentTick() < deathTick + DEATH_DELAY_TICKS,
						"thief B left after the death delay");
				return null;
			});
			awaitPhase(Vi6PhaseLobby.class, DEATH_DELAY_TICKS + 10 + 40);

			assertResultAfterDeath(capture, round, deathTick);
			assertEquals(List.of(), capture.failures(), "an exception was logged");
			assertEquals(List.of(), capture.errors(), "an error was logged");
		}
	}

	@RepeatedTest(3)
	void earlyLobbyCancelsAPendingDeathDelay() throws Exception {
		Vi6TestHooks.reset();
		try (var capture = new LogCapture()) {
			// B stays outside: the death does not end the round
			var round = stealRound(2);
			var botA = round.thieves().get(0);
			steal(round, botA);
			int deathTick = kill(botA.player());
			var infiltration = PaperServer.onMain(() -> {
				var phase = game().getPhaseMachine().getPhase(Vi6PhaseInfiltration.class);
				assertNotNull(phase, "the round ended on the death");
				assertTrue(phase.getPendingDeathDelayTicks() > 0, "no pending death delay after the death");
				game().getPhaseMachine().goTo(Vi6PhaseLobby.class, null);
				assertTrue(Bukkit.getCurrentTick() < deathTick + DEATH_DELAY_TICKS, "lobby after the death delay");
				return phase;
			});
			PaperServer.onMain(() -> {
				assertNotNull(game().getPhaseMachine().getPhase(Vi6PhaseLobby.class), "not in the lobby");
				assertEquals(0, infiltration.getPendingDeathDelayTicks(), "death delay still pending");
				return null;
			});
			PaperServer.awaitUntil(() -> Bukkit.getCurrentTick() > deathTick + DEATH_DELAY_TICKS + 10,
					DEATH_DELAY_TICKS + 20);

			assertEquals(List.of(), capture.failures(), "an exception was logged");
			assertEquals(List.of(), capture.errors(), "an error was logged");
		}
	}

	@RepeatedTest(3)
	void deadThiefWhoQuitsStillLosesHisArtefacts() throws Exception {
		Vi6TestHooks.reset();
		try (var capture = new LogCapture()) {
			// B stays outside: the death does not end the round
			var round = stealRound(2);
			var botA = round.thieves().get(0);
			var botB = round.thieves().get(1);
			steal(round, botA);
			int deathTick = kill(botA.player());
			// A quits within the death delay
			PaperServer.onMain(() -> {
				Ioc.resolve(BotRoster.class).remove(botA);
				assertTrue(Bukkit.getCurrentTick() < deathTick + DEATH_DELAY_TICKS, "quit after the death delay");
				return null;
			});
			PaperServer.awaitUntil(() -> Bukkit.getCurrentTick() > deathTick + DEATH_DELAY_TICKS + 5,
					DEATH_DELAY_TICKS + 20);
			PaperServer.onMain(() -> {
				assertNotNull(game().getPhaseMachine().getPhase(Vi6PhaseInfiltration.class),
						"round ended with thief B still out of the map");
				assertEquals(0, thiefScore(round.artefact()), "thief board score after the quit and the delay");
				return null;
			});

			// B enters, then escapes: every thief has left
			enterMap(botB, round.spawn());
			PaperServer.awaitUntil(() -> inMapWrapper(botB.player()).canLeaveMap(), SHORT_DELAY_TICKS + 20);
			var exit = PaperServer.onMain(() -> map().getExits().get(round.spawn()));
			stepInto(botB, exit, null);
			awaitPhase(Vi6PhaseLobby.class, 10 + 60);

			assertEquals(expectedResult("all-thieves-left", round.artefacts(), Map.of(round.artefact(), "lost")),
					roundResult(capture), "round result line");
			// Pre-existing, see deferred-work.md: Artefact keeps a player who quit inside
			// its capture box, and its tick then NPEs on his removed wrapper
			var unrelated = "getArtefactComponent()";
			assertEquals(List.of(), capture.failures().stream().filter(f -> !f.contains(unrelated)).toList(),
					"an exception was logged");
			assertEquals(List.of(),
					capture.lines().stream().filter(l -> l.level().isMoreSpecificThan(Level.ERROR))
							.filter(l -> l.thrown() == null || !String.valueOf(l.thrown()).contains(unrelated))
							.map(LogCapture.Line::message).toList(),
					"an error was logged");
		}
	}

	/**
	 * A round in infiltration with one guard, thieves, short delays and no KoTH.
	 *
	 * @param thieves
	 *            the thief bots
	 * @param artefact
	 *            the artefact to steal
	 * @param artefacts
	 *            every artefact of the map, by name
	 * @param spawn
	 *            the thief spawn, entrance and exit used
	 */
	private record StealRound(List<Bot> thieves, String artefact, List<String> artefacts, String spawn) {
	}

	private static StealRound stealRound(int thiefCount) throws Exception {
		var bots = Vi6TestHooks.spawnBots(1 + thiefCount);
		var thieves = bots.subList(1, bots.size());
		Vi6TestHooks.putOnTeams(List.of(bots.get(0).player()), players(thieves));
		Vi6TestHooks.forceKoths();
		var artefactName = PaperServer.onMain(() -> {
			var config = Ioc.resolve(ConfigManager.class).getConfig().game().infiltration();
			config.lostDuration(Duration.ofSeconds(30));
			config.delayBetweenCapture(SHORT_DELAY);
			config.delayBeforeEscape(SHORT_DELAY);
			config.endSpectatorTime(Duration.ofMillis(500));
			return firstZoneWithoutGuard(Vi6TestHooks.currentMap().getArtefacts(), Artefact::getBoundingBox);
		});
		var artefactNames = PaperServer
				.onMain(() -> Vi6TestHooks.currentMap().getArtefacts().keySet().stream().sorted().toList());
		var spawnName = PaperServer.onMain(Vi6PhaseMachineBotIT::spawnWithEntranceAndExit);
		Vi6TestHooks.goToInfiltration(spawnName);
		return new StealRound(thieves, artefactName, artefactNames, spawnName);
	}

	/**
	 * {@code thief} enters, waits until it may steal, then steals the round's
	 * artefact.
	 */
	private static void steal(StealRound round, Bot thief) throws Exception {
		enterMap(thief, round.spawn());
		PaperServer.awaitUntil(() -> inMapWrapper(thief.player()).canCaptureArtefact(), SHORT_DELAY_TICKS + 20);
		var artefact = PaperServer.onMain(() -> map().getArtefacts().get(round.artefact()));
		stepInto(thief, artefact.getBoundingBox(), artefact.getBlockPosition());
		PaperServer.awaitUntil(artefact::isCaptured, CAPTURE_TICKS + CAPTURE_MARGIN_TICKS);
		PaperServer.onMain(() -> {
			assertEquals(2, thiefScore(round.artefact()), "thief board score after the steal");
			return null;
		});
	}

	/**
	 * Kills {@code player} through the real death handlers.
	 *
	 * @return the tick of the death
	 */
	private static int kill(Player player) throws Exception {
		return PaperServer.onMain(() -> {
			player.setHealth(0);
			assertTrue(inMapWrapper(player).hasLeft(), player.getName() + " has not left on its death");
			return Bukkit.getCurrentTick();
		});
	}

	/**
	 * The result line reads the stolen artefact {@code lost}, and was logged no
	 * earlier than the end of the death delay.
	 */
	private static void assertResultAfterDeath(LogCapture capture, StealRound round, int deathTick) {
		assertEquals(expectedResult("all-thieves-left", round.artefacts(), Map.of(round.artefact(), "lost")),
				roundResult(capture), "round result line");
		var resultTick = capture.lines().stream().filter(l -> l.message().startsWith(RESULT_PREFIX))
				.mapToInt(LogCapture.Line::tick).findFirst().orElseThrow();
		assertTrue(resultTick - deathTick >= DEATH_DELAY_TICKS,
				"result logged " + (resultTick - deathTick) + " ticks after the death, before the death delay");
	}

	/** Records each artefact steal as {@code "<thief> stole <artefact>"}. */
	public static final class StealRecorder implements Listener {

		private final Queue<String> steals;

		StealRecorder(Queue<String> steals) {
			this.steals = steals;
		}

		@EventHandler
		public void onSteal(ArtefactStealEvent evt) {
			steals.add(evt.getThief().getName() + " stole " + evt.getArtefact().getName());
		}
	}

	// Moving

	/**
	 * Enters the map as a thief does: steps into the trigger box of the entrance
	 * named after its spawn, then checks it is inside.
	 */
	private static void enterMap(Bot thief, String entrance) throws Exception {
		var box = PaperServer.onMain(() -> map().getEntrances().get(entrance).getEntranceTriggerBox());
		stepInto(thief, box, null);
		PaperServer.onMain(() -> {
			assertTrue(inMapWrapper(thief.player()).isInside(),
					thief.name() + " did not enter through " + entrance + " " + box);
			return null;
		});
	}

	/**
	 * Places the bot on a standable spot inside {@code box}, then takes a real
	 * step, towards the side of the box with the most room, so that Vi6 sees a move
	 * into the box.
	 */
	private static void stepInto(Bot bot, BoundingBox box, @Nullable BlockVector excluded) throws Exception {
		var spot = PaperServer.onMain(() -> standableSpot(box, excluded));
		Vi6TestHooks.place(bot.player(), spot);
		PaperServer.onMain(() -> {
			bot.setHeading(roomiestYaw(box, spot));
			bot.setForward(true);
			return null;
		});
		PaperServer.awaitTicks(STEP_TICKS);
		PaperServer.onMain(() -> {
			bot.setForward(false);
			bot.setHeading(null);
			return null;
		});
		PaperServer.awaitTicks(SETTLE_TICKS);
	}

	/**
	 * @return the feet location of a block-centre column of {@code box}, nearest
	 *         its centre, whose floor (ray down from the box's top) has its top
	 *         inside the box, with room for feet and head; main thread only
	 * @param excluded
	 *            a block whose column is skipped, or {@code null}
	 */
	private static Location standableSpot(BoundingBox box, @Nullable BlockVector excluded) {
		var world = game().getWorld();
		var centre = box.getCenter();
		var columns = new ArrayList<Vector>();
		for (int x = (int) Math.floor(box.getMinX()); x < box.getMaxX(); x++) {
			for (int z = (int) Math.floor(box.getMinZ()); z < box.getMaxZ(); z++) {
				var cx = x + 0.5;
				var cz = z + 0.5;
				if (cx < box.getMinX() || cx >= box.getMaxX() || cz < box.getMinZ() || cz >= box.getMaxZ()) {
					continue;
				}
				if (excluded != null && excluded.getBlockX() == x && excluded.getBlockZ() == z) {
					continue;
				}
				columns.add(new Vector(cx, 0, cz));
			}
		}
		columns.sort(Comparator
				.comparingDouble(
						(Vector v) -> Math.pow(v.getX() - centre.getX(), 2) + Math.pow(v.getZ() - centre.getZ(), 2))
				.thenComparingDouble(Vector::getX).thenComparingDouble(Vector::getZ));
		for (var column : columns) {
			var start = new Location(world, column.getX(), box.getMaxY(), column.getZ());
			// Ray traces see unloaded chunks as empty: before a round, the map may not be
			// loaded yet
			world.getChunkAt(start);
			var hit = world.rayTraceBlocks(start, new Vector(0, -1, 0), box.getHeight() + 0.01,
					FluidCollisionMode.NEVER, true);
			if (hit == null || hit.getHitBlockFace() != BlockFace.UP) {
				continue;
			}
			var y = hit.getHitPosition().getY();
			if (y < box.getMinY() || y >= box.getMaxY()) {
				continue;
			}
			var feet = new Location(world, column.getX(), y, column.getZ());
			if (passable(feet.getBlock()) && passable(feet.getBlock().getRelative(BlockFace.UP))) {
				return feet;
			}
		}
		throw new IllegalStateException("no standable spot in " + box);
	}

	private static boolean passable(Block block) {
		return block.isPassable() && !block.isLiquid();
	}

	/**
	 * @return the yaw of the cardinal direction with the most open room inside the
	 *         box from {@code spot}, up to 2 blocks
	 */
	private static float roomiestYaw(BoundingBox box, Location spot) {
		int[][] directions = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
		var bestRoom = -1.0;
		float bestYaw = 0;
		for (var d : directions) {
			var room = 0.0;
			for (var step = 0.25; step <= 2.0; step += 0.25) {
				var point = spot.clone().add(d[0] * step, 0, d[1] * step);
				if (point.getX() < box.getMinX() || point.getX() >= box.getMaxX() || point.getZ() < box.getMinZ()
						|| point.getZ() >= box.getMaxZ() || !passable(point.getBlock())
						|| !passable(point.getBlock().getRelative(BlockFace.UP))) {
					break;
				}
				room = step;
			}
			if (room > bestRoom) {
				bestRoom = room;
				bestYaw = (float) Math.toDegrees(Math.atan2(-d[0], d[1]));
			}
		}
		return bestYaw;
	}

	// Map and game state

	/**
	 * @return the first zone by name whose box contains no guard spawn; main thread
	 *         only
	 */
	private static <T> String firstZoneWithoutGuard(Map<String, T> zones, Function<T, BoundingBox> box) {
		var guardSpawns = Vi6TestHooks.guardSpawns();
		return zones.entrySet().stream().sorted(Map.Entry.comparingByKey())
				.filter(e -> guardSpawns.stream().noneMatch(g -> box.apply(e.getValue()).contains(g.toVector())))
				.map(Map.Entry::getKey).findFirst()
				.orElseThrow(() -> new IllegalStateException("every zone holds a guard spawn: " + zones.keySet()));
	}

	/**
	 * @return the first thief spawn by name with an entrance and an exit of the
	 *         same name, each with a standable spot (some entrances are drops,
	 *         entered by falling through); main thread only
	 */
	private static String spawnWithEntranceAndExit() {
		var map = Vi6TestHooks.currentMap();
		return map.getThiefSpawns().keySet().stream().sorted()
				.filter(name -> map.getEntrances().containsKey(name) && map.getExits().containsKey(name))
				.filter(name -> hasStandableSpot(map.getEntrances().get(name).getEntranceTriggerBox())
						&& hasStandableSpot(map.getExits().get(name)))
				.findFirst().orElseThrow(() -> new IllegalStateException(
						"no thief spawn with a standable entrance and exit of its name"));
	}

	private static boolean hasStandableSpot(BoundingBox box) {
		try {
			standableSpot(box, null);
			return true;
		} catch (IllegalStateException e) {
			return false;
		}
	}

	/** @return the loaded map of the running in-map phase; main thread only */
	private static Vi6Map map() {
		var inMap = game().getPhaseMachine().getPhase(Vi6PhaseInMap.class);
		assertNotNull(inMap, "not in a map phase");
		return inMap.getMap();
	}

	private static InMapPhasePlayerWrapper inMapWrapper(Player player) {
		return Ioc.resolve(WrappingModule.class).getWrapper(player, InMapPhasePlayerWrapper.class);
	}

	private static int thiefScore(String artefact) {
		var objective = game().getScoreboard().getObjective("thiefArtefactListing");
		assertNotNull(objective, "no scoreboard objective thiefArtefactListing");
		return objective.getScore(artefact).getScore();
	}

	private static boolean guardTeamHas(String team, String entry) {
		var t = game().getScoreboard().getTeam(team);
		assertNotNull(t, "no scoreboard team " + team);
		return t.hasEntry(entry);
	}

	/**
	 * @return the progress of the thieves' infiltration boss bar shown to the
	 *         player
	 */
	private static float thiefBarProgress(Player thief) {
		for (var bar : thief.activeBossBars()) {
			if (PlainTextComponentSerializer.plainText().serialize(bar.name()).contains("Volez")) {
				return bar.progress();
			}
		}
		throw new AssertionError(thief.getName() + " sees no thief infiltration boss bar");
	}

	private static List<Player> players(List<Bot> bots) {
		return bots.stream().map(Bot::player).toList();
	}

	// Result line

	private static String roundResult(LogCapture capture) {
		var lines = capture.messages().stream().filter(m -> m.startsWith(RESULT_PREFIX)).toList();
		assertEquals(1, lines.size(), "round result lines: " + lines);
		return lines.get(0);
	}

	/**
	 * @param states
	 *            the state of each artefact that is not safe
	 */
	private static String expectedResult(String reason, List<String> artefacts, Map<String, String> states) {
		return RESULT_PREFIX + "reason=" + reason + ", artefacts=[" + artefacts.stream()
				.map(a -> a + "=" + states.getOrDefault(a, "safe")).collect(Collectors.joining(", ")) + "]";
	}

	// Phases

	private static int awaitPhase(Class<?> phase, int timeoutTicks) throws Exception {
		var reachedAt = new int[]{-1};
		PaperServer.awaitUntil(() -> {
			if (game().getPhaseMachine().getPhase(phase) != null) {
				reachedAt[0] = Bukkit.getCurrentTick();
				return true;
			}
			return false;
		}, timeoutTicks);
		return reachedAt[0];
	}

	private static void assertAround(int expectedTicks, int actualTicks, String what) {
		assertTrue(Math.abs(actualTicks - expectedTicks) <= TOLERANCE_TICKS,
				what + ": expected " + expectedTicks + " ticks (±" + TOLERANCE_TICKS + "), was " + actualTicks);
	}

	private static Vi6Game game() {
		return Ioc.resolve(Vi6Game.class);
	}
}
