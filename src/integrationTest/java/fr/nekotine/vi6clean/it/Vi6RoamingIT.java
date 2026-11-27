package fr.nekotine.vi6clean.it;

import static fr.nekotine.vi6clean.it.Vi6Asserts.assertCleanLog;
import static fr.nekotine.vi6clean.it.Vi6TestHooks.game;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import fr.nekotine.core.bot.Bot;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.navigation.PathFinder;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.Vi6Main;
import fr.nekotine.vi6clean.bot.PhaseBrain;
import fr.nekotine.vi6clean.bot.RoamingBrain;
import fr.nekotine.vi6clean.bot.Vi6Bots;
import fr.nekotine.vi6clean.constant.InMapState;
import fr.nekotine.vi6clean.game.phase.Vi6PhaseInfiltration;
import fr.nekotine.vi6clean.game.phase.Vi6PhaseLobby;
import fr.nekotine.vi6clean.wrapper.InMapPhasePlayerWrapper;
import fr.xxgoldenbluexx.paperjunitserver.PaperServer;

/**
 * Story 5.5: roaming guards and thieves travel between the map's areas for a
 * whole infiltration, on the default timers, and a failing finder stops only
 * the brain.
 */
class Vi6RoamingIT {

	/** How long a bot may neither move 1 block nor pick a new goal. */
	private static final int MAX_STILL_TICKS = 100;

	/** The lost timer and the end spectator time, with room for map loads. */
	private static final int ROUND_TIMEOUT_TICKS = 7200;

	@BeforeEach
	@AfterEach
	void reset() throws Exception {
		Vi6TestHooks.reset();
	}

	private static Vi6Bots vi6Bots() {
		return Ioc.resolve(ModuleManager.class).get(Vi6Bots.class);
	}

	/** What the sampler saw of one bot, over the round. Main thread only. */
	private static final class Track {

		final Bot bot;

		final RoamingBrain brain;

		boolean wasInside;

		@Nullable Location anchor;

		int anchorPicks;

		int anchorTick;

		@Nullable String still;

		@Nullable String offline;

		/** Goals reached: picks made standing at the previous goal. */
		int arrivals;

		/** The goal and pick count seen at the last sample. */
		@Nullable Location lastGoal;

		int lastPicks;

		/** Consecutive samples out of play still walking or with a goal. */
		int movingOutOfPlay;

		@Nullable String outOfPlay;

		Track(Bot bot, RoamingBrain brain) {
			this.bot = bot;
			this.brain = brain;
		}

		void sample(int tick) {
			var player = bot.player();
			if (!player.isOnline()) {
				if (offline == null) {
					offline = bot.name() + " went offline at tick " + tick;
				}
				return;
			}
			var wrapper = Ioc.resolve(WrappingModule.class).getWrapperOptional(player, InMapPhasePlayerWrapper.class)
					.orElse(null);
			if (wrapper != null && wrapper.getState() == InMapState.INSIDE) {
				wasInside = true;
			}
			var location = player.getLocation();
			int picks = brain.goalsPicked();
			if (picks > lastPicks && lastGoal != null && lastGoal.getWorld().equals(location.getWorld())
					&& Math.hypot(lastGoal.getX() - location.getX(), lastGoal.getZ() - location.getZ()) <= 1
					&& Math.abs(lastGoal.getBlockY() - location.getBlockY()) <= 1) {
				arrivals++;
			}
			lastPicks = picks;
			lastGoal = brain.goal();
			boolean infiltration = game().getPhaseMachine().getPhase(Vi6PhaseInfiltration.class) != null;
			if (infiltration && wrapper != null && (wrapper.hasLeft() || player.getGameMode() == GameMode.SPECTATOR)) {
				if (bot.isForward() || brain.goal() != null) {
					if (++movingOutOfPlay > 2 && outOfPlay == null) {
						outOfPlay = bot.name() + " still walking out of play at tick " + tick + ": forward "
								+ bot.isForward() + ", goal " + brain.goal();
					}
				} else {
					movingOutOfPlay = 0;
				}
			}
			boolean playing = infiltration && wrapper != null && !wrapper.hasLeft()
					&& player.getGameMode() != GameMode.SPECTATOR;
			if (!playing) {
				anchor = null;
				return;
			}
			if (anchor == null || !anchor.getWorld().equals(location.getWorld()) || anchor.distance(location) >= 1
					|| picks != anchorPicks) {
				anchor = location;
				anchorPicks = picks;
				anchorTick = tick;
			} else if (tick - anchorTick > MAX_STILL_TICKS && still == null) {
				still = bot.name() + " stood still from tick " + anchorTick + " to " + tick + " at " + anchor.toVector()
						+ ", goal " + brain.goal() + ", " + picks + " goals picked";
			}
		}
	}

	@Test
	void guardsAndThievesRoamUntilTheRoundEnds() throws Exception {
		var bots = Vi6TestHooks.spawnBots(4);
		var guards = List.of(bots.get(0), bots.get(1));
		var thieves = List.of(bots.get(2), bots.get(3));
		Vi6TestHooks.putOnTeams(guards.stream().map(Bot::player).toList(), thieves.stream().map(Bot::player).toList());
		Vi6TestHooks.forceKoths();
		var tracks = PaperServer.onMain(() -> {
			var result = new LinkedHashMap<Bot, Track>();
			for (var bot : bots) {
				var brain = vi6Bots().roamingBrain();
				vi6Bots().setBrain(bot, new PhaseBrain(PhaseBrain.IDLE, PhaseBrain.IDLE, brain));
				result.put(bot, new Track(bot, brain));
			}
			return result;
		});
		var spawn = PaperServer.onMain(Vi6MapSpots::spawnWithEntranceAndExit);
		try (var capture = new LogCapture()) {
			Vi6TestHooks.goToInfiltration(spawn);
			var sampler = PaperServer
					.onMain(() -> Bukkit.getScheduler().runTaskTimer(JavaPlugin.getPlugin(Vi6Main.class),
							() -> tracks.values().forEach(t -> t.sample(Bukkit.getCurrentTick())), 0, 1));
			try {
				Vi6TestHooks.awaitPhase(Vi6PhaseLobby.class, ROUND_TIMEOUT_TICKS);
			} finally {
				PaperServer.onMain(() -> {
					sampler.cancel();
					return null;
				});
			}
			PaperServer.onMain(() -> {
				var picks = new TreeMap<String, Integer>();
				var arrivals = new TreeMap<String, Integer>();
				for (var track : tracks.values()) {
					picks.put(track.bot.name(), track.brain.goalsPicked());
					arrivals.put(track.bot.name(), track.arrivals);
					assertNull(track.offline);
					assertTrue(track.bot.player().isOnline(), track.bot.name() + " was disconnected");
					assertNull(track.still);
					assertNull(track.outOfPlay);
				}
				for (var count : arrivals.values()) {
					assertTrue(count >= 1, "a bot never reached a goal, arrivals: " + arrivals + ", picks: " + picks);
				}
				for (var thief : thieves) {
					assertTrue(tracks.get(thief).wasInside, thief.name() + " never got inside");
				}
				for (var count : picks.values()) {
					assertTrue(count >= 3, "too few goals picked: " + picks);
				}
				// Spectators at the round's end, then the lobby: every walk was stopped
				for (var bot : bots) {
					assertFalse(bot.isForward(), bot.name() + " still holds forward in the lobby");
					assertNull(bot.getHeading(), bot.name() + " still has a heading in the lobby");
				}
				return null;
			});
			assertCleanLog(capture);
		}
	}

	@Test
	void leavingThePhaseMidWalkReleasesMovement() throws Exception {
		var bots = Vi6TestHooks.spawnBots(2);
		var guard = bots.get(0);
		Vi6TestHooks.putOnTeams(List.of(guard.player()), List.of(bots.get(1).player()));
		Vi6TestHooks.forceKoths();
		var brain = PaperServer.onMain(() -> {
			var roaming = vi6Bots().roamingBrain();
			vi6Bots().setBrain(guard, new PhaseBrain(PhaseBrain.IDLE, PhaseBrain.IDLE, roaming));
			return roaming;
		});
		try (var capture = new LogCapture()) {
			Vi6TestHooks.goToInfiltration(PaperServer.onMain(Vi6MapSpots::spawnWithEntranceAndExit));
			PaperServer.awaitUntil(() -> brain.goal() != null && guard.isForward(), 200);
			PaperServer.onMain(() -> {
				game().getPhaseMachine().goTo(Vi6PhaseLobby.class, null);
				return null;
			});
			PaperServer.awaitTicks(3);
			PaperServer.onMain(() -> {
				assertFalse(guard.isForward(), "forward still held after the phase ended");
				assertNull(guard.getHeading(), "heading still set after the phase ended");
				assertNull(brain.goal(), "the brain kept its goal after the phase ended");
				return null;
			});
			assertCleanLog(capture);
		}
	}

	@Test
	void aThrowingFinderStopsTheBrainAndTheBotStays() throws Exception {
		var bots = Vi6TestHooks.spawnBots(2);
		var guard = bots.get(0);
		Vi6TestHooks.putOnTeams(List.of(guard.player()), List.of(bots.get(1).player()));
		Vi6TestHooks.forceKoths();
		Vi6TestHooks.goToInfiltration(PaperServer.onMain(Vi6MapSpots::spawnWithEntranceAndExit));
		var calls = new AtomicInteger();
		PathFinder failing = (_, _, _) -> {
			calls.incrementAndGet();
			throw new IllegalStateException("finder test failure");
		};
		try (var capture = new LogCapture()) {
			PaperServer.onMain(() -> {
				// A key held when the brain fails: the failure must release it
				guard.setForward(true);
				vi6Bots().setBrain(guard,
						new PhaseBrain(PhaseBrain.IDLE, PhaseBrain.IDLE, new RoamingBrain(new Random(1), failing)));
				return null;
			});
			PaperServer.awaitTicks(5);
			assertEquals(1, calls.get(), "the brain was not stopped after the finder's first throw");
			var lines = capture.errors().stream().filter(l -> l.startsWith(guard.name() + " ")).toList();
			assertEquals(1, lines.size(), "expected one brain error line, got " + lines);
			var line = lines.getFirst();
			assertTrue(line.contains("équipe: guard"), line);
			assertTrue(line.contains("phase: Vi6PhaseInfiltration"), line);
			assertTrue(
					capture.failures().stream()
							.anyMatch(l -> l.startsWith(guard.name() + " ") && l.contains("finder test failure")),
					"the brain error line carries no exception: " + capture.failures());
			var others = new ArrayList<>(capture.errors());
			others.removeAll(lines);
			assertEquals(List.of(), others, "other error lines");
		}
		PaperServer.onMain(() -> {
			assertTrue(guard.player().isOnline(), "the bot was disconnected");
			assertNull(vi6Bots().getBrain(guard), "the brain was not removed");
			assertFalse(guard.isForward(), "forward still held");
			assertNull(guard.getHeading(), "heading still set");
			return null;
		});
	}
}
