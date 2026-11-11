package fr.nekotine.vi6clean.configuration;

import java.time.Duration;

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

	public void duration(Duration value) {
		durationSeconds = value.toMillis() / 1000.0;
	}

	public int money() {
		return money;
	}

	public void money(int value) {
		money = value;
	}

}
