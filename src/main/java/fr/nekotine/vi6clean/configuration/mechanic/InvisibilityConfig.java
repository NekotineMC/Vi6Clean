package fr.nekotine.vi6clean.configuration.mechanic;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;

@ConfigSerializable
public class InvisibilityConfig {

	@Comment("Distance entre chaque pas, utilisé pour l'appartion de particles")
	private double stepDistance = 0.5;

	@Comment("Nombres de particules affichés à chaque pas")
	private int particleCount = 3;

	// Getters and setters

	public int particleCount() {
		return particleCount;
	}

	public void particleCount(int value) {
		particleCount = value;
	}

	public double stepDistance() {
		return stepDistance;
	}

	public void stepDistance(double value) {
		stepDistance = value;
	}

}
