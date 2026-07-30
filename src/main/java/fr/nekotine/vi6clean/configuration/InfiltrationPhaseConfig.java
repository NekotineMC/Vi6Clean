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

	// Getters and setters

	public int lostDurationSeconds() {
		return lostDurationSeconds;
	}

	public void lostDurationSeconds(int value) {
		lostDurationSeconds = value;
	}

	public Duration lostDuration() {
		return Duration.ofSeconds(lostDurationSeconds);
	}

	public void lostDuration(Duration value) {
		lostDurationSeconds = Math.toIntExact(value.toSeconds());
	}

	public int delayBetweenCaptureSeconds() {
		return delayBetweenCaptureSeconds;
	}

	public void delayBetweenCaptureSeconds(int value) {
		delayBetweenCaptureSeconds = value;
	}

	public Duration delayBetweenCapture() {
		return Duration.ofSeconds(delayBetweenCaptureSeconds);
	}

	public void delayBetweenCapture(Duration value) {
		delayBetweenCaptureSeconds = Math.toIntExact(value.toSeconds());
	}

	public int delayBeforeEscapeSeconds() {
		return delayBeforeEscapeSeconds;
	}

	public void delayBeforeEscapeSeconds(int value) {
		delayBeforeEscapeSeconds = value;
	}

	public Duration delayBeforeEscape() {
		return Duration.ofSeconds(delayBeforeEscapeSeconds);
	}

	public void delayBeforeEscape(Duration value) {
		delayBeforeEscapeSeconds = Math.toIntExact(value.toSeconds());
	}

}
