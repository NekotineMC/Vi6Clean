package fr.nekotine.vi6clean.configuration;

import java.time.Duration;
import java.util.List;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;

@ConfigSerializable
public class BotsConfig {

	@Comment("Délai en secondes entre l'arrivée d'un bot dans le lobby (où il rejoint son équipe tout de suite) et son /game ready")
	private double lobbyReadyDelaySeconds = 10;

	// Getters and setters

	public Duration lobbyReadyDelay() {
		return Duration.ofMillis(Math.round(lobbyReadyDelaySeconds * 1000));
	}

	/**
	 * @throws IllegalArgumentException
	 *             if {@code value} is not between 1 tick and
	 *             {@link Integer#MAX_VALUE} ticks; the old value is kept
	 */
	public void lobbyReadyDelay(Duration value) {
		lobbyReadyDelaySeconds = DurationRules.checkedSeconds("lobbyReadyDelay", value);
	}

	/**
	 * Resets the delay to its default if invalid, adding a warning to
	 * {@code warnings}.
	 *
	 * @param path
	 *            this section's key path, for the warning
	 */
	void resetInvalidDurations(String path, List<String> warnings) {
		lobbyReadyDelaySeconds = DurationRules.orDefault(path + ".lobby-ready-delay-seconds", lobbyReadyDelaySeconds,
				new BotsConfig().lobbyReadyDelaySeconds, warnings);
	}

}
