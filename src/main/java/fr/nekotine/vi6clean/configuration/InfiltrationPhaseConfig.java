package fr.nekotine.vi6clean.configuration;

import java.time.Duration;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;

@ConfigSerializable
public class InfiltrationPhaseConfig {

	@Comment("Durée sans capture d'artefact avant que les voleurs soit considérés comme perdu")
	private int lostDurationSeconds = 240;

	@Comment("Durée minimale entre deux capture d'artefact")
	private int delayBetweenCaptureSeconds = 30;

	@Comment("Durée minimale entre la dernière capture d'un voleur est sa fuite")
	private int delayBeforeEscapeSeconds = 30;
	
	@Comment("Durée en spéctateur à la fin de la partie")
	private int endSpectatorTimeSeconds = 5;

	// Getters and setters

	public Duration lostDuration() {
		return Duration.ofSeconds(lostDurationSeconds);
	}

	public void lostDuration(Duration value) {
		lostDurationSeconds = Math.toIntExact(value.toSeconds());
	}

	public Duration delayBetweenCapture() {
		return Duration.ofSeconds(delayBetweenCaptureSeconds);
	}

	public void delayBetweenCapture(Duration value) {
		delayBetweenCaptureSeconds = Math.toIntExact(value.toSeconds());
	}
	
	public Duration delayBeforeEscape() {
		return Duration.ofSeconds(delayBeforeEscapeSeconds);
	}
	
	public void delayBeforeEscape(Duration value) {
		delayBeforeEscapeSeconds = Math.toIntExact(value.toSeconds());
	}

	public Duration endSpectatorTime() {
		return Duration.ofSeconds(endSpectatorTimeSeconds);
	}

	public void endSpectatorTime(Duration value) {
		endSpectatorTimeSeconds = Math.toIntExact(value.toSeconds());
	}

}
