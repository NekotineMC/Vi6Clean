package fr.nekotine.vi6clean.configuration;

import java.time.Duration;
import java.util.ArrayList;

/** The five phase durations of a config, as they were when it was loaded. */
record DurationSnapshot(Duration preparation, Duration lost, Duration delayBetweenCapture, Duration delayBeforeEscape,
		Duration endSpectator) {

	static DurationSnapshot of(Vi6Config config) {
		var preparation = config.game().preparation();
		var infiltration = config.game().infiltration();
		return new DurationSnapshot(preparation.duration(), infiltration.lostDuration(),
				infiltration.delayBetweenCapture(), infiltration.delayBeforeEscape(), infiltration.endSpectatorTime());
	}

	/**
	 * @return the durations of {@code current} that differ from this snapshot, as
	 *         {@code "key old -> new"} joined by {@code ", "}, or an empty string
	 *         when none do
	 */
	String describeChanges(DurationSnapshot current) {
		var changes = new ArrayList<String>();
		describe(changes, "game.preparation.duration-seconds", preparation, current.preparation);
		describe(changes, "game.infiltration.lost-duration-seconds", lost, current.lost);
		describe(changes, "game.infiltration.delay-between-capture-seconds", delayBetweenCapture,
				current.delayBetweenCapture);
		describe(changes, "game.infiltration.delay-before-escape-seconds", delayBeforeEscape,
				current.delayBeforeEscape);
		describe(changes, "game.infiltration.end-spectator-time-seconds", endSpectator, current.endSpectator);
		return String.join(", ", changes);
	}

	private static void describe(ArrayList<String> changes, String key, Duration loaded, Duration current) {
		if (!loaded.equals(current)) {
			changes.add(key + " " + seconds(loaded) + "s -> " + seconds(current) + "s");
		}
	}

	private static String seconds(Duration d) {
		return Double.toString(d.toMillis() / 1000.0);
	}
}
