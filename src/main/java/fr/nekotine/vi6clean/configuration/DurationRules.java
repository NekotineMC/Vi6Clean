package fr.nekotine.vi6clean.configuration;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The rule every phase duration follows: finite, at least 1 tick (50 ms) and at
 * most {@link Integer#MAX_VALUE} ticks, which is what {@code Tick.fromDuration}
 * can turn into a positive {@code int}.
 */
final class DurationRules {

	static final long MILLIS_PER_TICK = 50;

	static final long MIN_MILLIS = MILLIS_PER_TICK;

	/**
	 * The largest millisecond count {@code Tick.fromDuration} still fits in an int.
	 */
	static final long MAX_MILLIS = Integer.MAX_VALUE * MILLIS_PER_TICK + (MILLIS_PER_TICK - 1);

	private DurationRules() {
	}

	static boolean isValidMillis(long millis) {
		return millis >= MIN_MILLIS && millis <= MAX_MILLIS;
	}

	/**
	 * @return whether {@code seconds}, as the getters read it, is a valid duration
	 */
	static boolean isValidSeconds(double seconds) {
		return Double.isFinite(seconds) && isValidMillis(Math.round(seconds * 1000));
	}

	/**
	 * @return {@code value} in seconds, as stored by the config classes
	 * @throws IllegalArgumentException
	 *             naming {@code field} if {@code value} is null or invalid
	 */
	static double checkedSeconds(String field, Duration value) {
		if (value == null) {
			throw new IllegalArgumentException(field + " must not be null");
		}
		long millis;
		try {
			millis = value.toMillis();
		} catch (ArithmeticException e) {
			throw new IllegalArgumentException(field + " is too long: " + value + " " + bounds(), e);
		}
		if (!isValidMillis(millis)) {
			throw new IllegalArgumentException(field + " is out of range: " + value + " " + bounds());
		}
		return millis / 1000.0;
	}

	/**
	 * @return {@code seconds} if valid, else {@code defaultSeconds}, with a warning
	 *         naming {@code key} and the bad value added to {@code warnings}
	 */
	static double orDefault(String key, double seconds, double defaultSeconds, List<String> warnings) {
		if (isValidSeconds(seconds)) {
			return seconds;
		}
		warnings.add("Durée invalide " + key + ": " + seconds + " " + bounds() + ", valeur par défaut utilisée: "
				+ defaultSeconds);
		return defaultSeconds;
	}

	/**
	 * Resets every invalid duration of {@code config} (phase durations and the bot
	 * lobby ready delay) to its default.
	 *
	 * @return one warning per reset key, empty when all are valid
	 */
	static List<String> resetInvalidDurations(Vi6Config config) {
		var warnings = new ArrayList<String>();
		var game = config.game();
		if (game != null) {
			if (game.preparation() != null) {
				game.preparation().resetInvalidDurations("game.preparation", warnings);
			}
			if (game.infiltration() != null) {
				game.infiltration().resetInvalidDurations("game.infiltration", warnings);
			}
		}
		if (config.bots() != null) {
			config.bots().resetInvalidDurations("bots", warnings);
		}
		return warnings;
	}

	private static String seconds(long millis) {
		return BigDecimal.valueOf(millis, 3).stripTrailingZeros().toPlainString();
	}

	/** @return the allowed range, in seconds, in parentheses */
	static String bounds() {
		return "(entre " + seconds(MIN_MILLIS) + " s et " + seconds(Integer.MAX_VALUE * MILLIS_PER_TICK) + " s)";
	}
}
