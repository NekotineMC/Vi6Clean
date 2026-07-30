package fr.nekotine.vi6clean.configuration;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;

import fr.nekotine.vi6clean.configuration.map.MapConfig;
import fr.nekotine.vi6clean.configuration.mechanic.MechanicsConfig;

@ConfigSerializable
public class GameConfig {

	@Comment("Phase de préparation")
	private PreparationPhaseConfig preparation = new PreparationPhaseConfig();

	@Comment("Phase d'infiltration")
	private InfiltrationPhaseConfig infiltration = new InfiltrationPhaseConfig();

	@Comment("Configuration de la carte")
	private MapConfig map = new MapConfig();

	@Comment("Configuration des mécaniques de jeu")
	private MechanicsConfig mechanics = new MechanicsConfig();

	// Getters et Setters

	public PreparationPhaseConfig preparation() {
		return preparation;
	}

	public void preparation(PreparationPhaseConfig value) {
		preparation = value;
	}

	public InfiltrationPhaseConfig infiltration() {
		return infiltration;
	}

	public void infiltration(InfiltrationPhaseConfig value) {
		infiltration = value;
	}

	public MapConfig map() {
		return map;
	}

	public void map(MapConfig value) {
		map = value;
	}

	public MechanicsConfig mechanics() {
		return mechanics;
	}

	public void mechanics(MechanicsConfig value) {
		mechanics = value;
	}

}
