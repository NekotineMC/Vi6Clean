package fr.nekotine.vi6clean.bot;

import fr.nekotine.core.navigation.BlockView;
import fr.nekotine.core.navigation.PathRule;
import fr.nekotine.core.navigation.Passage;
import fr.nekotine.vi6clean.map.Entrance;
import fr.nekotine.vi6clean.map.Vi6Map;
import fr.nekotine.vi6clean.tool.personal.forcefield.ForcefieldHandler;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.util.BoundingBox;

/**
 * Vi6's map rules as {@link PathRule}s, per role, mirroring what
 * {@code Vi6PhaseInMap} enforces on humans: an inside player who may not leave
 * cannot move into an entrance blocking box or an exit, and a guard's
 * forcefield opens for guards walking up to it.
 *
 * <ul>
 * <li>{@link #guard}: every entrance blocking box and every exit is forbidden;
 * a closed forcefield ({@link ForcefieldHandler#FIELD_MATERIAL} in a gate) is
 * open.</li>
 * <li>{@link #thiefOutside}: nothing is forbidden (entrances and exits do
 * nothing to a thief not yet inside).</li>
 * <li>{@link #thiefInside}: every entrance blocking box and every exit is
 * forbidden, unless the thief is leaving. Whether it may leave yet is the
 * caller's call.</li>
 * </ul>
 *
 * <p>
 * Thieves override no block: a closed field is glass, which the finder already
 * refuses, and an opened field is air. A feet block {@code (x, y, z)} is
 * refused when its cell {@code [x, x+1] × [y, y+1] × [z, z+1]} strictly
 * overlaps a forbidden box, so a box that only touches a face does not refuse
 * it; a gate block is one whose cell strictly overlaps a gate box. The rules
 * add no cost.
 *
 * <p>
 * Factories run on the main thread and copy the boxes they need: the rule keeps
 * no reference to the map (whose boxes are mutable), reads blocks only through
 * the search's {@link BlockView}, and is pure, so it may run off the main
 * thread. Build a new rule per search from the current game state. A
 * {@code null} map collection counts as empty.
 */
public final class Vi6PathRules {

	private Vi6PathRules() {
	}

	/**
	 * @return the rule of a guard: never through an exit or an entrance blocking
	 *         box, through closed forcefields
	 * @throws NullPointerException
	 *             if {@code map} is {@code null}
	 */
	public static PathRule guard(Vi6Map map) {
		Objects.requireNonNull(map, "map");
		return of(forbidden(map), boxes(map.getGates()), ForcefieldHandler.FIELD_MATERIAL);
	}

	/**
	 * @param map
	 *            unused (entrances and exits do nothing to an outside thief); taken
	 *            for symmetry with the other roles
	 * @return the rule of a thief not yet inside: nothing forbidden
	 * @throws NullPointerException
	 *             if {@code map} is {@code null}
	 */
	public static PathRule thiefOutside(Vi6Map map) {
		Objects.requireNonNull(map, "map");
		return none();
	}

	/**
	 * @param leaving
	 *            whether the thief is on its way out (the caller decides whether it
	 *            may leave yet)
	 * @return the rule of a thief inside: never through an exit or an entrance
	 *         blocking box unless leaving
	 * @throws NullPointerException
	 *             if {@code map} is {@code null}
	 */
	public static PathRule thiefInside(Vi6Map map, boolean leaving) {
		Objects.requireNonNull(map, "map");
		return leaving ? none() : of(forbidden(map), List.of(), ForcefieldHandler.FIELD_MATERIAL);
	}

	/**
	 * A rule over plain boxes, copied: feet cells overlapping a forbidden box are
	 * refused, blocks of {@code field} in a gate are open. {@code null} boxes are
	 * skipped.
	 */
	static PathRule of(Collection<BoundingBox> forbidden, Collection<BoundingBox> gates, Material field) {
		return new Rule(copy(forbidden), copy(gates), Objects.requireNonNull(field, "field"));
	}

	private static PathRule none() {
		return of(List.of(), List.of(), ForcefieldHandler.FIELD_MATERIAL);
	}

	private static List<BoundingBox> forbidden(Vi6Map map) {
		var boxes = new ArrayList<BoundingBox>();
		Map<String, Entrance> entrances = map.getEntrances();
		if (entrances != null) {
			for (var entrance : entrances.values()) {
				if (entrance != null) {
					boxes.add(entrance.getBlockingBox());
				}
			}
		}
		boxes.addAll(boxes(map.getExits()));
		return boxes;
	}

	private static Collection<BoundingBox> boxes(Map<String, BoundingBox> boxes) {
		return boxes == null ? List.of() : boxes.values();
	}

	private static Box[] copy(Collection<BoundingBox> boxes) {
		Objects.requireNonNull(boxes, "boxes");
		return boxes.stream().filter(Objects::nonNull).map(Box::of).toArray(Box[]::new);
	}

	/** An immutable copy of a {@link BoundingBox}. */
	private record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

		static Box of(BoundingBox box) {
			return new Box(box.getMinX(), box.getMinY(), box.getMinZ(), box.getMaxX(), box.getMaxY(), box.getMaxZ());
		}

		/** Same test as {@link BoundingBox#overlaps}, against a block's unit cell. */
		boolean overlapsBlock(int x, int y, int z) {
			return minX < x + 1 && maxX > x && minY < y + 1 && maxY > y && minZ < z + 1 && maxZ > z;
		}
	}

	private record Rule(Box[] forbidden, Box[] gates, Material field) implements PathRule {

		@Override
		public boolean allows(BlockView view, int x, int y, int z) {
			return !any(forbidden, x, y, z);
		}

		@Override
		public Passage passage(BlockView view, int x, int y, int z) {
			if (!any(gates, x, y, z)) {
				return Passage.DEFAULT;
			}
			var data = view.getBlockData(x, y, z);
			return data != null && data.getMaterial() == field ? Passage.OPEN : Passage.DEFAULT;
		}

		private static boolean any(Box[] boxes, int x, int y, int z) {
			for (var box : boxes) {
				if (box.overlapsBlock(x, y, z)) {
					return true;
				}
			}
			return false;
		}
	}
}
