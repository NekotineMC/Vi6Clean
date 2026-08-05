package fr.nekotine.vi6clean.configuration.mechanic;

import java.time.Duration;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;

import io.papermc.paper.util.Tick;

@ConfigSerializable
public class MajordomConfig {

	@Comment("Délais avant que les portes/trappes se ferment automatiquement")
	private int delayTicks = 40;

	// Getters and setters

	public Duration delay() {
		return Tick.of(delayTicks);
	}

	public void delay(Duration value) {
		delayTicks = Tick.tick().fromDuration(value);
	}

}
