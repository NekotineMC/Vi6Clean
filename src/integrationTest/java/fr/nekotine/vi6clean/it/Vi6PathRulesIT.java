package fr.nekotine.vi6clean.it;

import static fr.nekotine.vi6clean.it.Vi6MapSpots.enterMap;
import static fr.nekotine.vi6clean.it.Vi6MapSpots.map;
import static fr.nekotine.vi6clean.it.Vi6MapSpots.passable;
import static fr.nekotine.vi6clean.it.Vi6MapSpots.standableSpot;
import static fr.nekotine.vi6clean.it.Vi6TestHooks.game;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.destroystokyo.paper.entity.Pathfinder.PathResult;
import fr.nekotine.core.bot.Bot;
import fr.nekotine.core.navigation.BlockView;
import fr.nekotine.core.navigation.PathFinder;
import fr.nekotine.core.navigation.PathRule;
import fr.nekotine.core.navigation.astar.AStarPathFinder;
import fr.nekotine.vi6clean.Vi6Main;
import fr.nekotine.vi6clean.bot.Vi6PathRules;
import fr.nekotine.vi6clean.tool.personal.forcefield.ForcefieldHandler;
import fr.xxgoldenbluexx.paperjunitserver.PaperServer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Story 5.4: {@link Vi6PathRules} on the loaded map, searched with the
 * {@link AStarPathFinder}: guards and inside thieves never route through an
 * exit or an entrance blocking box unless the thief leaves, and a guard routes
 * through a closed forcefield where a thief does not.
 */
class Vi6PathRulesIT {

	/**
	 * Margin of the gate searches: small, so the snapshot holds the gate and its
	 * two sides but no way around it.
	 */
	private static final int GATE_MARGIN = 2;

	@AfterEach
	void reset() throws Exception {
		Vi6TestHooks.reset();
	}

	/**
	 * A guard bot and a thief bot in infiltration, the thief entered through the
	 * entrance of the spawn with a standable entrance and exit of its name.
	 *
	 * @return the thief bot and that spawn's name
	 */
	private record Round(Bot thief, String spawn) {
	}

	private static Round infiltration() throws Exception {
		Vi6TestHooks.reset();
		var bots = Vi6TestHooks.spawnBots(2);
		Vi6TestHooks.putOnTeams(List.of(bots.get(0).player()), List.of(bots.get(1).player()));
		Vi6TestHooks.forceKoths();
		var spawn = PaperServer.onMain(Vi6MapSpots::spawnWithEntranceAndExit);
		Vi6TestHooks.goToInfiltration(spawn);
		return new Round(bots.get(1), spawn);
	}

	private static PathFinder finder(int margin) {
		return new AStarPathFinder(JavaPlugin.getPlugin(Vi6Main.class), margin, AStarPathFinder.DEFAULT_NODE_BUDGET);
	}

	private static PathResult find(PathFinder finder, Location from, Location to, PathRule rule) throws Exception {
		return PaperServer.onMain(() -> finder.find(from, to, rule)).get(10, TimeUnit.SECONDS);
	}

	private static PathResult find(Location from, Location to, PathRule rule) throws Exception {
		return find(finder(AStarPathFinder.DEFAULT_MARGIN), from, to, rule);
	}

	private static List<String> blocks(PathResult path) {
		return path.getPoints().stream().map(p -> p.getBlockX() + " " + p.getBlockY() + " " + p.getBlockZ()).toList();
	}

	/**
	 * @return the exits and entrance blocking boxes of the map; main thread only
	 */
	private static List<BoundingBox> forbiddenBoxes() {
		var boxes = new ArrayList<BoundingBox>(map().getExits().values());
		map().getEntrances().values().forEach(e -> boxes.add(e.getBlockingBox()));
		return boxes;
	}

	private static boolean overlapsAny(Location point, List<BoundingBox> boxes) {
		var cell = BoundingBox.of(point.getBlock());
		return boxes.stream().anyMatch(box -> box.overlaps(cell));
	}

	@Test
	void guardsAndInsideThievesKeepOutOfExitsAndBlockingBoxes() throws Exception {
		var round = infiltration();
		enterMap(round.thief(), round.spawn());
		var from = PaperServer.onMain(() -> round.thief().player().getLocation());
		var exitSpot = PaperServer.onMain(() -> standableSpot(map().getExits().get(round.spawn()), null));
		var guard = PaperServer.onMain(() -> Vi6PathRules.guard(map()));
		var staying = PaperServer.onMain(() -> Vi6PathRules.thiefInside(map(), false));
		var leaving = PaperServer.onMain(() -> Vi6PathRules.thiefInside(map(), true));
		var outside = PaperServer.onMain(() -> Vi6PathRules.thiefOutside(map()));
		var forbidden = PaperServer.onMain(Vi6PathRulesIT::forbiddenBoxes);

		// The first entrance blocking box that holds blocks (some are empty): refused
		// unless the thief is outside or leaving.
		var blocking = PaperServer.onMain(() -> map().getEntrances().entrySet().stream()
				.sorted(Map.Entry.comparingByKey()).map(e -> cells(e.getValue().getBlockingBox()))
				.filter(cells -> !cells.isEmpty()).findFirst()
				.orElseThrow(() -> new IllegalStateException("no entrance of the map has a non-empty blocking box")));
		BlockView noBlocks = (x, y, z) -> null;
		for (var cell : blocking) {
			assertFalse(guard.allows(noBlocks, cell.getX(), cell.getY(), cell.getZ()), "guard in blocking box " + cell);
			assertFalse(staying.allows(noBlocks, cell.getX(), cell.getY(), cell.getZ()),
					"staying thief in blocking box " + cell);
			assertTrue(leaving.allows(noBlocks, cell.getX(), cell.getY(), cell.getZ()),
					"leaving thief refused in blocking box " + cell);
			assertTrue(outside.allows(noBlocks, cell.getX(), cell.getY(), cell.getZ()),
					"outside thief refused in blocking box " + cell);
		}
		// A guard may stand in the entrance trigger box, outside every forbidden box.
		var trigger = PaperServer.onMain(() -> cells(map().getEntrances().get(round.spawn()).getEntranceTriggerBox())
				.stream().filter(cell -> !overlapsAny(cell.getLocation(), forbidden)).findFirst());
		assertTrue(trigger.isPresent(), "every trigger box cell of " + round.spawn() + " is in a forbidden box");
		var t = trigger.get();
		assertTrue(guard.allows(noBlocks, t.getX(), t.getY(), t.getZ()), "a guard may not stand in trigger cell " + t);

		var any = find(from, exitSpot, PathRule.ANY);
		assertTrue(any.canReachFinalPoint(), "the exit is not reachable at all: " + blocks(any));

		var guardPath = find(from, exitSpot, guard);
		assertFalse(guardPath.canReachFinalPoint(), "a guard reached the exit: " + blocks(guardPath));
		var stayingPath = find(from, exitSpot, staying);
		assertFalse(stayingPath.canReachFinalPoint(), "a thief not leaving reached the exit: " + blocks(stayingPath));
		PaperServer.onMain(() -> {
			for (var path : List.of(guardPath, stayingPath)) {
				// The start is the body's own position, never refused.
				for (var point : path.getPoints().subList(1, path.getPoints().size())) {
					assertFalse(overlapsAny(point, forbidden),
							"a point in an exit or a blocking box: " + point + " in " + blocks(path));
				}
			}
			return null;
		});

		var leavingPath = find(from, exitSpot, leaving);
		assertTrue(leavingPath.canReachFinalPoint(), "a leaving thief did not reach the exit: " + blocks(leavingPath));
		var outsidePath = find(from, exitSpot, outside);
		assertTrue(outsidePath.canReachFinalPoint(), "an outside thief did not reach the exit: " + blocks(outsidePath));
	}

	/**
	 * A gate one block thick, with a standable block on each side at its bottom.
	 */
	private record Gate(String name, BoundingBox box, Location sideA, Location sideB) {
	}

	/** @return the first gate by name with standable sides; main thread only */
	private static Gate gateWithStandableSides() {
		var world = game().getWorld();
		var gates = map().getGates();
		assertTrue(gates != null && !gates.isEmpty(), "the map has no gate");
		var names = gates.keySet().stream().sorted().toList();
		for (var name : names) {
			var box = gates.get(name);
			int y = (int) Math.floor(box.getMinY());
			int midX = (int) Math.floor(box.getCenterX());
			int midZ = (int) Math.floor(box.getCenterZ());
			Location a;
			Location b;
			if (box.getWidthX() == 1) {
				a = new Location(world, box.getMinX() - 1 + 0.5, y, midZ + 0.5);
				b = new Location(world, box.getMaxX() + 0.5, y, midZ + 0.5);
			} else if (box.getWidthZ() == 1) {
				a = new Location(world, midX + 0.5, y, box.getMinZ() - 1 + 0.5);
				b = new Location(world, midX + 0.5, y, box.getMaxZ() + 0.5);
			} else {
				continue;
			}
			world.getChunkAt(a);
			world.getChunkAt(b);
			if (standable(a.getBlock()) && standable(b.getBlock())) {
				return new Gate(name, box.clone(), a, b);
			}
		}
		throw new IllegalStateException("no gate one block thick with standable sides among " + names);
	}

	private static boolean standable(Block feet) {
		return feet.getRelative(BlockFace.DOWN).getType().isSolid() && passable(feet)
				&& passable(feet.getRelative(BlockFace.UP));
	}

	private static List<Block> cells(BoundingBox box) {
		var world = game().getWorld();
		var cells = new ArrayList<Block>();
		for (int x = (int) Math.floor(box.getMinX()); x < box.getMaxX(); x++) {
			for (int y = (int) Math.floor(box.getMinY()); y < box.getMaxY(); y++) {
				for (int z = (int) Math.floor(box.getMinZ()); z < box.getMaxZ(); z++) {
					cells.add(world.getBlockAt(x, y, z));
				}
			}
		}
		return cells;
	}

	private static void fill(List<Block> cells, Material material) throws Exception {
		PaperServer.onMain(() -> {
			cells.forEach(cell -> cell.setType(material, false));
			return null;
		});
	}

	private static boolean crosses(PathResult path, BoundingBox gate) {
		return path.getPoints().stream().anyMatch(p -> gate.overlaps(BoundingBox.of(p.getBlock())));
	}

	@Test
	void guardsCrossAClosedForcefieldAndThievesOnlyAnOpenedOne() throws Exception {
		infiltration();
		var gate = PaperServer.onMain(Vi6PathRulesIT::gateWithStandableSides);
		var cells = PaperServer.onMain(() -> cells(gate.box()));
		Map<Block, BlockData> saved = new HashMap<>();
		PaperServer.onMain(() -> {
			cells.forEach(cell -> saved.put(cell, cell.getBlockData()));
			return null;
		});
		var finder = finder(GATE_MARGIN);
		assertEquals(Material.GRAY_STAINED_GLASS, ForcefieldHandler.FIELD_MATERIAL,
				"Vi6PathRulesTest copies the field material");
		try {
			fill(cells, ForcefieldHandler.FIELD_MATERIAL);
			var guard = PaperServer.onMain(() -> Vi6PathRules.guard(map()));
			var thief = PaperServer.onMain(() -> Vi6PathRules.thiefInside(map(), false));

			var guardClosed = find(finder, gate.sideA(), gate.sideB(), guard);
			assertTrue(guardClosed.canReachFinalPoint(),
					"a guard did not cross the closed field " + gate.name() + ": " + blocks(guardClosed));
			assertTrue(crosses(guardClosed, gate.box()), "the guard went around " + gate.name());
			var thiefClosed = find(finder, gate.sideA(), gate.sideB(), thief);
			assertFalse(thiefClosed.canReachFinalPoint(),
					"a thief crossed the closed field " + gate.name() + ": " + blocks(thiefClosed));
			assertFalse(crosses(thiefClosed, gate.box()), "a thief path enters the closed field");

			fill(cells, Material.AIR);
			var guardOpen = find(finder, gate.sideA(), gate.sideB(), guard);
			var thiefOpen = find(finder, gate.sideA(), gate.sideB(), thief);
			assertTrue(guardOpen.canReachFinalPoint(), "a guard did not cross the opened field: " + blocks(guardOpen));
			assertTrue(thiefOpen.canReachFinalPoint(), "a thief did not cross the opened field: " + blocks(thiefOpen));
			assertEquals(blocks(guardOpen), blocks(thiefOpen), "an opened field is the same to both roles");
		} finally {
			PaperServer.onMain(() -> {
				saved.forEach((cell, data) -> cell.setBlockData(data, false));
				return null;
			});
		}
	}
}
