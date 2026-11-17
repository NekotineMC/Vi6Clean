package fr.nekotine.vi6clean.game.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The round ends whatever logging its result throws:
 * {@link Vi6PhaseInfiltration#logThenComplete}.
 */
class RoundCompletionTest {

	private final List<String> steps = new ArrayList<>();

	private final List<Exception> warnings = new ArrayList<>();

	@Test
	void completesAfterTheLogWithoutWarning() {
		Vi6PhaseInfiltration.logThenComplete(() -> steps.add("log"), () -> steps.add("complete"), warnings::add);
		assertEquals(List.of("log", "complete"), steps);
		assertEquals(List.of(), warnings, "warning on the normal path");
	}

	@Test
	void completesWhenTheLogThrowsAnException() {
		var thrown = new IllegalStateException("boom");
		Vi6PhaseInfiltration.logThenComplete(() -> {
			throw thrown;
		}, () -> steps.add("complete"), warnings::add);
		assertEquals(List.of("complete"), steps);
		assertEquals(1, warnings.size(), "warnings: " + warnings);
		assertSame(thrown, warnings.get(0));
	}

	@Test
	void completesWhenTheLogThrowsAnError() {
		var thrown = new AssertionError("boom");
		var rethrown = assertThrows(AssertionError.class, () -> Vi6PhaseInfiltration.logThenComplete(() -> {
			throw thrown;
		}, () -> steps.add("complete"), warnings::add));
		assertSame(thrown, rethrown);
		assertEquals(List.of("complete"), steps);
		assertTrue(warnings.isEmpty(), "an Error is rethrown, not warned");
	}
}
