package fr.nekotine.vi6clean.configuration;

import java.time.Duration;

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

	public void lostDuration(Duration value) {
		lostDurationSeconds = value.toMillis() / 1000.0;
	}

	public Duration delayBetweenCapture() {
		return Duration.ofMillis(Math.round(delayBetweenCaptureSeconds * 1000));
	}

	public void delayBetweenCapture(Duration value) {
		delayBetweenCaptureSeconds = value.toMillis() / 1000.0;
	}

	public Duration delayBeforeEscape() {
		return Duration.ofMillis(Math.round(delayBeforeEscapeSeconds * 1000));
	}

	public void delayBeforeEscape(Duration value) {
		delayBeforeEscapeSeconds = value.toMillis() / 1000.0;
	}

	public Duration endSpectatorTime() {
		return Duration.ofMillis(Math.round(endSpectatorTimeSeconds * 1000));
	}

	public void endSpectatorTime(Duration value) {
		endSpectatorTimeSeconds = value.toMillis() / 1000.0;
	}

}
