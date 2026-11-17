package fr.nekotine.vi6clean.configuration;

import java.time.Duration;
import java.util.List;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;

@ConfigSerializable
public class PreparationPhaseConfig {

	@Comment("Durée de la phase de preparation en secondes")
	private double durationSeconds = 300;

	@Comment("Argent donné au début de la phase de préparation")
	private int money = 2000;

	// Getters and setters

	public Duration duration() {
		return Duration.ofMillis(Math.round(durationSeconds * 1000));
	}

	/**
	 * @throws IllegalArgumentException
	 *             if {@code value} is not between 1 tick and
	 *             {@link Integer#MAX_VALUE} ticks; the old value is kept
	 */
	public void duration(Duration value) {
		durationSeconds = DurationRules.checkedSeconds("duration", value);
	}

	public int money() {
		return money;
	}

	public void money(int value) {
		money = value;
	}

	/**
	 * Resets the duration to its default if invalid, adding a warning to
	 * {@code warnings}.
	 *
	 * @param path
	 *            this section's key path, for the warning
	 */
	void resetInvalidDurations(String path, List<String> warnings) {
		durationSeconds = DurationRules.orDefault(path + ".duration-seconds", durationSeconds,
				new PreparationPhaseConfig().durationSeconds, warnings);
	}

}
