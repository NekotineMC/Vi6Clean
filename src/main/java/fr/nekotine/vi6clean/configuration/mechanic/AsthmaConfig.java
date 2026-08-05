package fr.nekotine.vi6clean.configuration.mechanic;

import java.time.Duration;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;

import io.papermc.paper.util.Tick;

@ConfigSerializable
public class AsthmaConfig {

	@Comment("Délais entre chaque retrait d'un demis gigot en cas de course")
	private int sprintConsumptionDelayTicks = 40;

	@Comment("Délais entre chaque ajout d'un demis gigot en cas de marche")
	private int walkRegenerationDelayTicks = 40;

	@Comment("Délais entre chaque ajout d'un demis gigot quand immobile")
	private int idleRegenerationDelayTicks = 20;

	@Comment("Durée sans bouger avant qu'un joueur soit considéré comme immobile")
	private int idleDelayTicks = 5;

	@Comment("Nombre maximum de demis gigot laissé au joueur après une capture")
	private int maxFoodAfterCapture = 10;

	// Getters and setters

	public Duration sprintConsumptionDelay() {
		return Tick.of(sprintConsumptionDelayTicks);
	}

	public void sprintConsumptionDelay(Duration value) {
		sprintConsumptionDelayTicks = Tick.tick().fromDuration(value);
	}

	public Duration walkRegenerationDelay() {
		return Tick.of(walkRegenerationDelayTicks);
	}

	public void walkRegenerationDelay(Duration value) {
		walkRegenerationDelayTicks = Tick.tick().fromDuration(value);
	}

	public Duration idleRegenerationDelay() {
		return Tick.of(idleRegenerationDelayTicks);
	}

	public void idleRegenerationDelay(Duration value) {
		idleRegenerationDelayTicks = Tick.tick().fromDuration(value);
	}

	public Duration idleDelay() {
		return Tick.of(idleDelayTicks);
	}

	public void idleDelay(Duration value) {
		idleDelayTicks = Tick.tick().fromDuration(value);
	}

	public int maxFoodAfterCapture() {
		return maxFoodAfterCapture;
	}

	public void maxFoodAfterCapture(int value) {
		maxFoodAfterCapture = value;
	}

}
