package fr.nekotine.vi6clean.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Location;

/** Assertions shared by the integration tests. */
public final class Vi6Asserts {

	/** How far a measured length in ticks may be from the expected one. */
	public static final int TOLERANCE_TICKS = 5;

	/** How far, in blocks, a location may be from the expected one. */
	private static final double NEAR_BLOCKS = 0.1;

	private Vi6Asserts() {
	}

	/**
	 * Asserts that {@code actualTicks} is within {@link #TOLERANCE_TICKS} of
	 * {@code expectedTicks}.
	 */
	public static void assertAround(int expectedTicks, int actualTicks, String what) {
		assertTrue(Math.abs(actualTicks - expectedTicks) <= TOLERANCE_TICKS,
				what + ": expected " + expectedTicks + " ticks (±" + TOLERANCE_TICKS + "), was " + actualTicks);
	}

	/**
	 * Asserts that {@code actual} is in {@code expected}'s world, less than 0.1
	 * block away from it in 3D.
	 */
	public static void assertNear(Location expected, Location actual, String what) {
		assertEquals(expected.getWorld(), actual.getWorld(), what + ": world");
		assertTrue(expected.toVector().distance(actual.toVector()) < NEAR_BLOCKS,
				what + ": expected " + expected.toVector() + ", was " + actual.toVector());
	}

	/**
	 * Asserts that {@code actual} is in {@code expected}'s world, less than 0.1
	 * block away from it horizontally and less than {@code yTolerance} vertically:
	 * for a player who may have landed or stepped since the teleport.
	 */
	public static void assertNearHorizontally(Location expected, Location actual, double yTolerance, String what) {
		assertEquals(expected.getWorld(), actual.getWorld(), what + ": world");
		var dx = expected.getX() - actual.getX();
		var dz = expected.getZ() - actual.getZ();
		assertTrue(Math.sqrt(dx * dx + dz * dz) < NEAR_BLOCKS && Math.abs(expected.getY() - actual.getY()) < yTolerance,
				what + ": expected " + expected.toVector() + ", was " + actual.toVector());
	}
}
