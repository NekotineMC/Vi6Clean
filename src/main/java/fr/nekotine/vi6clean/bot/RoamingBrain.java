package fr.nekotine.vi6clean.bot;

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
 * <li>A thief still {@link InMapState#ENTERING entering} roams the same areas
 * under {@link Vi6PathRules#thiefOutside}: every area is inside the map, so its
 * path goes through an entrance. Once {@link InMapState#INSIDE inside}, it
 * keeps its goal, searched again under {@link Vi6PathRules#thiefInside
 * thiefInside(map, false)}, and roams under that rule: it never asks to
 * leave.</li>
 * </ul>
 *
 * <p>
 * A new goal is picked on the first tick of a role, and on the tick after the
 * walker reports {@link WalkStatus#ARRIVED} or {@link WalkStatus#STUCK} (after
 * {@link PathWalker#DEFAULT_STUCK_TICKS} ticks with no progress). Areas are
 * drawn with the shared random source, never the current goal again when there
 * are two or more. With no area, the bot idles.
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
			// A new role: a fresh search under its own rule. A thief that just got
			// inside keeps walking to the area it was entering for.
			walker.stop();
			status = null;
			if (goal != null && goalMode == Mode.ENTERING && mode == Mode.INSIDE) {
				goalMode = mode;
				walker.walkTo(goal, finder, rule(mode, map));
			} else {
				goal = null;
			}
		}
		if (goal == null || status == WalkStatus.ARRIVED || status == WalkStatus.STUCK) {
			var next = pickArea(map, world);
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
