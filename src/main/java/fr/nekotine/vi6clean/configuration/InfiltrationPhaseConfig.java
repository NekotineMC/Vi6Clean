package fr.nekotine.vi6clean.configuration;

import java.time.Duration;
import java.util.List;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;

@ConfigSerializable
public class InfiltrationPhaseConfig {

	@Comment("Durée sans capture d'artefact avant que les voleurs soit considérés comme perdu")
	private double lostDurationSeconds = 240;

	@Comment("Durée minimale entre deux capture d'artefact")
	private double delayBetweenCaptureSeconds = 30;

	@Comment("Durée minimale entre la dernière capture d'un voleur est sa fuite")
	private double delayBeforeEscapeSeconds = 30;

	@Comment("Durée en spéctateur à la fin de la partie")
	private double endSpectatorTimeSeconds = 5;

	// Getters and setters

	public Duration lostDuration() {
		return Duration.ofMillis(Math.round(lostDurationSeconds * 1000));
	}

	/**
	 * @throws IllegalArgumentException
	 *             if {@code value} is not between 1 tick and
	 *             {@link Integer#MAX_VALUE} ticks; the old value is kept
	 */
	public void lostDuration(Duration value) {
		lostDurationSeconds = DurationRules.checkedSeconds("lostDuration", value);
	}

	public Duration delayBetweenCapture() {
		return Duration.ofMillis(Math.round(delayBetweenCaptureSeconds * 1000));
	}

	/**
	 * @throws IllegalArgumentException
	 *             if {@code value} is not between 1 tick and
	 *             {@link Integer#MAX_VALUE} ticks; the old value is kept
	 */
	public void delayBetweenCapture(Duration value) {
		delayBetweenCaptureSeconds = DurationRules.checkedSeconds("delayBetweenCapture", value);
	}

	public Duration delayBeforeEscape() {
		return Duration.ofMillis(Math.round(delayBeforeEscapeSeconds * 1000));
	}

	/**
	 * @throws IllegalArgumentException
	 *             if {@code value} is not between 1 tick and
	 *             {@link Integer#MAX_VALUE} ticks; the old value is kept
	 */
	public void delayBeforeEscape(Duration value) {
		delayBeforeEscapeSeconds = DurationRules.checkedSeconds("delayBeforeEscape", value);
	}

	public Duration endSpectatorTime() {
		return Duration.ofMillis(Math.round(endSpectatorTimeSeconds * 1000));
	}

	/**
	 * @throws IllegalArgumentException
	 *             if {@code value} is not between 1 tick and
	 *             {@link Integer#MAX_VALUE} ticks; the old value is kept
	 */
	public void endSpectatorTime(Duration value) {
		endSpectatorTimeSeconds = DurationRules.checkedSeconds("endSpectatorTime", value);
	}

	/**
	 * Resets each invalid duration to its default, adding one warning per reset key
	 * to {@code warnings}.
	 *
	 * @param path
	 *            this section's key path, for the warnings
	 */
	void resetInvalidDurations(String path, List<String> warnings) {
		var defaults = new InfiltrationPhaseConfig();
		lostDurationSeconds = DurationRules.orDefault(path + ".lost-duration-seconds", lostDurationSeconds,
				defaults.lostDurationSeconds, warnings);
		delayBetweenCaptureSeconds = DurationRules.orDefault(path + ".delay-between-capture-seconds",
				delayBetweenCaptureSeconds, defaults.delayBetweenCaptureSeconds, warnings);
		delayBeforeEscapeSeconds = DurationRules.orDefault(path + ".delay-before-escape-seconds",
				delayBeforeEscapeSeconds, defaults.delayBeforeEscapeSeconds, warnings);
		endSpectatorTimeSeconds = DurationRules.orDefault(path + ".end-spectator-time-seconds", endSpectatorTimeSeconds,
				defaults.endSpectatorTimeSeconds, warnings);
	}

}
