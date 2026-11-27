package fr.nekotine.vi6clean.it;

import static fr.nekotine.vi6clean.it.Vi6TestHooks.game;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.nekotine.core.bot.Bot;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.game.phase.Vi6PhaseInMap;
import fr.nekotine.vi6clean.map.Vi6Map;
import fr.nekotine.vi6clean.wrapper.InMapPhasePlayerWrapper;
import fr.xxgoldenbluexx.paperjunitserver.PaperServer;
import java.util.ArrayList;
import java.util.Comparator;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockVector;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

/**
 * Spots of the loaded map and moves onto them, shared by the bot ITs: standable
 * spots in a box, a real step into a box, entering the map as a thief, and the
 * thief spawn with a standable entrance and exit of its name.
 */
final class Vi6MapSpots {

	/** Ticks for a control to reach the server and the bot to stop moving. */
	static final int SETTLE_TICKS = 5;

	/** Ticks the forward key is held for a step. */
	static final int STEP_TICKS = 3;

	private Vi6MapSpots() {
	}

	/**
	 * Enters the map as a thief does: steps into the trigger box of the entrance
	 * named after its spawn, then checks it is inside.
	 */
	static void enterMap(Bot thief, String entrance) throws Exception {
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
	static void stepInto(Bot bot, BoundingBox box, @Nullable BlockVector excluded) throws Exception {
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
	static Location standableSpot(BoundingBox box, @Nullable BlockVector excluded) {
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

	static boolean passable(Block block) {
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

	/**
	 * @return the first thief spawn by name with an entrance and an exit of the
	 *         same name, each with a standable spot (some entrances are drops,
	 *         entered by falling through); main thread only
	 */
	static String spawnWithEntranceAndExit() {
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
	static Vi6Map map() {
		var inMap = game().getPhaseMachine().getPhase(Vi6PhaseInMap.class);
		assertNotNull(inMap, "not in a map phase");
		return inMap.getMap();
	}

	static InMapPhasePlayerWrapper inMapWrapper(Player player) {
		return Ioc.resolve(WrappingModule.class).getWrapper(player, InMapPhasePlayerWrapper.class);
	}
}
