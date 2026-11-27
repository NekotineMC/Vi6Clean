package fr.nekotine.vi6clean.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * Story 5.5: how {@link RoamAreas#pick} draws the next goal, with no server.
 */
class RoamAreasTest {

	private static final List<String> AREAS = List.of("a", "b", "c", "d");

	@Test
	void neverPicksTheCurrentGoalWithTwoAreasOrMore() {
		var random = new Random(1);
		for (var areas : List.of(List.of("a", "b"), AREAS)) {
			var seen = new HashSet<String>();
			String current = null;
			for (int i = 0; i < 1000; i++) {
				var next = RoamAreas.pick(areas, current, random);
				assertTrue(areas.contains(next), next + " is not an area");
				assertNotEquals(current, next, "picked the current goal again");
				seen.add(next);
				current = next;
			}
			assertEquals(new HashSet<>(areas), seen, "some areas were never picked");
		}
	}

	@Test
	void aGoalOutsideTheAreasMayGoAnywhere() {
		var random = new Random(3);
		var seen = new HashSet<String>();
		for (int i = 0; i < 1000; i++) {
			seen.add(RoamAreas.pick(AREAS, "entrance", random));
		}
		assertEquals(new HashSet<>(AREAS), seen);
	}

	@Test
	void theSameSeedGivesTheSameSequence() {
		assertEquals(sequence(42), sequence(42));
		assertNotEquals(sequence(42), sequence(7));
	}

	private static List<String> sequence(long seed) {
		var random = new Random(seed);
		var picks = new ArrayList<String>();
		String current = null;
		for (int i = 0; i < 50; i++) {
			current = RoamAreas.pick(AREAS, current, random);
			picks.add(current);
		}
		return picks;
	}

	@Test
	void oneAreaIsPickedAgain() {
		var random = new Random(5);
		assertEquals("a", RoamAreas.pick(List.of("a"), null, random));
		assertEquals("a", RoamAreas.pick(List.of("a"), "a", random));
	}

	@Test
	void noAreaGivesNull() {
		assertNull(RoamAreas.pick(List.<String>of(), null, new Random(5)));
		assertNull(RoamAreas.pick(List.<String>of(), "a", new Random(5)));
	}
}
