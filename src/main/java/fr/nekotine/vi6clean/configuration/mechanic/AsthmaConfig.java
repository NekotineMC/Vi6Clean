package fr.nekotine.vi6clean.configuration.mechanic;

import java.time.Duration;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;

import io.papermc.paper.util.Tick;

@ConfigSerializable
public class AsthmaConfig {

	@Comment("Délais entre chaque retrait d'un demis gigot e cas de course")
	private int halfDrumstickConsumptionDelayTicks = 40;

	// Getters and setters

	public int halfDrumstickConsumptionDelayTicks() {
		return halfDrumstickConsumptionDelayTicks;
	}

	public void halfDrumstickConsumptionDelayTicks(int value) {
		halfDrumstickConsumptionDelayTicks = value;
	}

	public Duration delay() {
		return Tick.of(halfDrumstickConsumptionDelayTicks);
	}

	public void delay(Duration value) {
		halfDrumstickConsumptionDelayTicks = Tick.tick().fromDuration(value);
	}

}
