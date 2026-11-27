package fr.nekotine.vi6clean.bot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.random.RandomGenerator;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.Nullable;

import fr.nekotine.vi6clean.map.RoomCaptor;
import fr.nekotine.vi6clean.map.Vi6Map;
import fr.nekotine.vi6clean.map.artefact.Artefact;
import fr.nekotine.vi6clean.map.koth.Koth;

/**
 * The map areas roaming bots walk between, and the pick of the next one.
 */
final class RoamAreas {

	private RoamAreas() {
	}

	/**
	 * A standable spot of {@code box}: the first block column, nearest the box
	 * centre first (then by x, then z), whose centre is in the box, with a feet
	 * block in the box (lowest first) on a floor whose {@code isSolid()} is true,
	 * and feet and head blocks passable and not liquid. Main thread only: reads the
	 * live world, loading chunks.
	 *
	 * @return the spot's feet location, at its block's horizontal centre, or
	 *         {@code null} with none
	 */
	static @Nullable Location standableSpot(World world, BoundingBox box) {
		var centre = box.getCenter();
		var columns = new ArrayList<int[]>();
		for (int x = (int) Math.floor(box.getMinX()); x < box.getMaxX(); x++) {
			for (int z = (int) Math.floor(box.getMinZ()); z < box.getMaxZ(); z++) {
				double cx = x + 0.5;
				double cz = z + 0.5;
				if (cx >= box.getMinX() && cx < box.getMaxX() && cz >= box.getMinZ() && cz < box.getMaxZ()) {
					columns.add(new int[]{x, z});
				}
			}
		}
		columns.sort(Comparator
				.comparingDouble((int[] c) -> square(c[0] + 0.5 - centre.getX()) + square(c[1] + 0.5 - centre.getZ()))
				.thenComparingInt(c -> c[0]).thenComparingInt(c -> c[1]));
		for (var column : columns) {
			for (int y = (int) Math.ceil(box.getMinY()); y < box.getMaxY(); y++) {
				var feet = world.getBlockAt(column[0], y, column[1]);
				if (standable(feet)) {
					return new Location(world, column[0] + 0.5, y, column[1] + 0.5);
				}
			}
		}
		return null;
	}

	private static boolean standable(Block feet) {
		return feet.getRelative(0, -1, 0).getType().isSolid() && passable(feet) && passable(feet.getRelative(0, 1, 0));
	}

	private static boolean passable(Block block) {
		return block.isPassable() && !block.isLiquid();
	}

	private static double square(double v) {
		return v * v;
	}

	/**
	 * The roaming areas of {@code map}: a standable spot of each artefact capture
	 * box, room captor trigger box and KoTH box, boxes sorted by name (then
	 * artefacts, room captors, KoTHs). Boxes with no standable spot are skipped,
	 * and a spot shared by two boxes is kept once. {@code null} collections and
	 * entries count as none. Main thread only.
	 */
	static List<Location> areas(Vi6Map map, World world) {
		var boxes = new ArrayList<Named>();
		addBoxes(boxes, map.getArtefacts(), Artefact::getBoundingBox);
		addBoxes(boxes, map.getRoomCaptors(), RoomCaptor::getTriggerBox);
		addBoxes(boxes, map.getKoths(), Koth::getBoundingBox);
		// Stable: same-name boxes keep their type order
		boxes.sort(Comparator.comparing(Named::name));
		var spots = new LinkedHashSet<Location>();
		for (var named : boxes) {
			var spot = standableSpot(world, named.box());
			if (spot != null) {
				spots.add(spot);
			}
		}
		return List.copyOf(spots);
	}

	private record Named(String name, BoundingBox box) {
	}

	private static <V> void addBoxes(List<Named> out, @Nullable Map<String, V> values, Function<V, BoundingBox> box) {
		if (values == null) {
			return;
		}
		values.entrySet().stream().filter(e -> e.getKey() != null && e.getValue() != null)
				.sorted(Map.Entry.comparingByKey()).forEach(e -> {
					var b = box.apply(e.getValue());
					if (b != null) {
						out.add(new Named(e.getKey(), b));
					}
				});
	}

	/**
	 * Draws the next goal from {@code areas}: never {@code current} when there are
	 * two areas or more, the only area when there is one.
	 *
	 * @param current
	 *            the current goal, or {@code null}; it need not be an area
	 * @return the next goal, or {@code null} when {@code areas} is empty
	 */
	@Nullable static <T> T pick(List<T> areas, @Nullable T current, RandomGenerator random) {
		int n = areas.size();
		if (n == 0) {
			return null;
		}
		if (n == 1) {
			return areas.getFirst();
		}
		int skip = current == null ? -1 : areas.indexOf(current);
		if (skip < 0) {
			return areas.get(random.nextInt(n));
		}
		int index = random.nextInt(n - 1);
		return areas.get(index >= skip ? index + 1 : index);
	}
}
