package fr.nekotine.vi6clean.bot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import fr.nekotine.core.bot.Bot;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.navigation.PathFinder;
import fr.nekotine.core.navigation.PathRule;
import fr.nekotine.core.navigation.PathWalker;
import fr.nekotine.core.navigation.WalkStatus;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.InMapState;
import fr.nekotine.vi6clean.game.Vi6Game;
import fr.nekotine.vi6clean.game.phase.Vi6PhaseInMap;
import fr.nekotine.vi6clean.map.Vi6Map;
import fr.nekotine.vi6clean.wrapper.InMapPhasePlayerWrapper;

/**
 * The in-map brain of a roaming bot: it walks between the map's areas
 * ({@link RoamAreas#areas}) for the whole round, through its own
 * {@link PathWalker} over the bot's body.
 *
 * <ul>
 * <li>A guard roams under {@link Vi6PathRules#guard}.</li>
 * <li>A thief still {@link InMapState#ENTERING entering} walks into the
 * entrance trigger box nearest it (by box centre), under
 * {@link Vi6PathRules#thiefOutside}; when stuck, or arrived but still outside,
 * it tries the next nearest, cycling. Once {@link InMapState#INSIDE inside}, it
 * roams under {@link Vi6PathRules#thiefInside thiefInside(map, false)}: it
 * never asks to leave.</li>
 * </ul>
 *
 * <p>
 * A new goal is picked on the first tick of a role, and on the tick after the
 * walker reports {@link WalkStatus#ARRIVED} or {@link WalkStatus#STUCK} (after
 * {@link PathWalker#DEFAULT_STUCK_TICKS} ticks with no progress). Areas are
 * drawn with the shared random source, never the current goal again when there
 * are two or more. With no area (or no entrance), the bot idles.
 *
 * <p>
 * Areas are computed once per round, a round being one
 * {@link InMapPhasePlayerWrapper} instance of the bot. A bot in spectator mode,
 * or that has left the map, stops its walker and idles; so does a bot with no
 * in-map wrapper or no team. {@link #halt} stops the walker. State is read only
 * through Vi6's public API, and the bot moves only through its walker.
 * Exceptions of the walker and the finder propagate. Its walker drives the body
 * of the first bot it ticks: one instance per bot, never shared.
 */
public class RoamingBrain implements BotBrain {

	private enum Mode {
		GUARD, ENTERING, INSIDE
	}

	private final RandomGenerator random;

	private final PathFinder finder;

	private @Nullable PathWalker walker;

	/** The in-map wrapper of the round being played, or {@code null}. */
	private @Nullable InMapPhasePlayerWrapper round;

	private @Nullable List<Location> areas;

	/** Entrance goals, nearest first, computed when the thief starts entering. */
	private @Nullable List<Location> entrances;

	private int entranceIndex;

	private @Nullable Location goal;

	private @Nullable Mode goalMode;

	private @Nullable WalkStatus status;

	private int goalsPicked;

	/**
	 * @param random
	 *            the source of area picks, shared with the other bots so a seed
	 *            gives the same picks
	 * @param finder
	 *            the path finder, may be shared by every bot
	 */
	public RoamingBrain(RandomGenerator random, PathFinder finder) {
		this.random = Objects.requireNonNull(random, "random");
		this.finder = Objects.requireNonNull(finder, "finder");
	}

	@Override
	public void tick(Bot bot) {
		if (walker == null) {
			walker = new PathWalker(bot.body());
		}
		var player = bot.player();
		var wrapper = Ioc.resolve(WrappingModule.class).getWrapperOptional(player, InMapPhasePlayerWrapper.class)
				.orElse(null);
		if (wrapper == null) {
			idle();
			return;
		}
		if (wrapper != round) {
			idle();
			round = wrapper;
			areas = null;
			entrances = null;
			entranceIndex = 0;
		}
		var game = Ioc.resolve(Vi6Game.class);
		var inMap = game.getPhaseMachine().getPhase(Vi6PhaseInMap.class);
		if (inMap == null || player.getGameMode() == GameMode.SPECTATOR || wrapper.hasLeft()) {
			idle();
			return;
		}
		Mode mode;
		if (game.getGuards().contains(player)) {
			mode = Mode.GUARD;
		} else if (game.getThiefs().contains(player) && wrapper.getState() == InMapState.ENTERING) {
			mode = Mode.ENTERING;
		} else if (game.getThiefs().contains(player) && wrapper.getState() == InMapState.INSIDE) {
			mode = Mode.INSIDE;
		} else {
			idle();
			return;
		}
		var map = inMap.getMap();
		var world = game.getWorld();
		if (!world.equals(player.getWorld())) {
			// Not in the map's world (yet): nothing to walk to
			idle();
			return;
		}
		if (goalMode != null && mode != goalMode) {
			// A new role: a fresh walk under its own rule, from its first goal
			walker.stop();
			goal = null;
			status = null;
		}
		if (goal == null || status == WalkStatus.ARRIVED || status == WalkStatus.STUCK) {
			boolean failed = status == WalkStatus.ARRIVED || status == WalkStatus.STUCK;
			var next = mode == Mode.ENTERING
					? nextEntrance(map, world, player.getLocation(), failed)
					: pickArea(map, world);
			if (next == null) {
				idle();
				return;
			}
			goal = next;
			goalMode = mode;
			goalsPicked++;
			walker.walkTo(next, finder, rule(mode, map));
		}
		status = walker.tick();
	}

	private @Nullable Location pickArea(Vi6Map map, World world) {
		if (areas == null) {
			areas = RoamAreas.areas(map, world);
		}
		return RoamAreas.pick(areas, goal, random);
	}

	/**
	 * @param advance
	 *            whether the last entrance walk ended (stuck, or arrived but still
	 *            outside): only then is the next entrance tried
	 */
	private @Nullable Location nextEntrance(Vi6Map map, World world, Location from, boolean advance) {
		if (entrances == null) {
			entrances = entrances(map, world, from);
			entranceIndex = 0;
		} else if (advance && !entrances.isEmpty()) {
			entranceIndex = (entranceIndex + 1) % entrances.size();
		}
		return entrances.isEmpty() ? null : entrances.get(entranceIndex);
	}

	/**
	 * @return a goal per entrance trigger box (a standable spot, else its centre),
	 *         nearest {@code from} first by box centre, then by name
	 */
	private static List<Location> entrances(Vi6Map map, World world, Location from) {
		record Candidate(String name, double distance, Location goal) {
		}
		var candidates = new ArrayList<Candidate>();
		var all = map.getEntrances();
		if (all != null) {
			for (var entry : all.entrySet()) {
				var entrance = entry.getValue();
				var box = entrance == null ? null : entrance.getEntranceTriggerBox();
				if (box == null) {
					continue;
				}
				var centre = box.getCenter();
				var spot = RoamAreas.standableSpot(world, box);
				var target = spot != null ? spot : centre.toLocation(world);
				candidates.add(
						new Candidate(String.valueOf(entry.getKey()), centre.distanceSquared(from.toVector()), target));
			}
		}
		candidates.sort(Comparator.comparingDouble(Candidate::distance).thenComparing(Candidate::name));
		return candidates.stream().map(Candidate::goal).toList();
	}

	private static PathRule rule(Mode mode, Vi6Map map) {
		return switch (mode) {
			case GUARD -> Vi6PathRules.guard(map);
			case ENTERING -> Vi6PathRules.thiefOutside(map);
			case INSIDE -> Vi6PathRules.thiefInside(map, false);
		};
	}

	/** Stops the walker (neutral movement once if it was walking) and the goal. */
	private void idle() {
		if (walker != null) {
			walker.stop();
		}
		goal = null;
		goalMode = null;
		status = null;
	}

	@Override
	public void halt(Bot bot) {
		idle();
	}

	/** @return a copy of the current goal, or {@code null} with none */
	public @Nullable Location goal() {
		return goal == null ? null : goal.clone();
	}

	/** @return how many goals this brain has picked since it was built */
	public int goalsPicked() {
		return goalsPicked;
	}
}
