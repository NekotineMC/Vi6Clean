package fr.nekotine.vi6clean.configuration.mechanic;

import java.time.Duration;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;

import io.papermc.paper.util.Tick;

@ConfigSerializable
public class SuffocationConfig {

	@Comment("Durée d'oxygène retiré a chaque tick quand le joueur suffoque")
	private int airTicksRemovedPerTicks = 8;

	@Comment("Interval entre chaque dégats quand le joueur n'as plus d'air")
	private int damageIntervalTicks = 20;

	@Comment("Dégats infligés quand le joueur n'as plus d'air")
	private double damageAmount = 1.0;

	// Getters and setters

	public Duration damageInterval() {
		return Tick.of(damageIntervalTicks);
	}

	public void damageInterval(Duration value) {
		damageIntervalTicks = Tick.tick().fromDuration(value);
	}

	public int airTicksRemovedPerTicks() {
		return airTicksRemovedPerTicks;
	}

	public void airTicksRemovedPerTicks(int value) {
		airTicksRemovedPerTicks = value;
	}

	public double damageAmount() {
		return damageAmount;
	}

	public void damageAmount(double value) {
		damageAmount = value;
	}

}
