package fr.nekotine.vi6clean.configuration.mechanic;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;

@ConfigSerializable
public class MechanicsConfig {

	@Comment("Configuration de la fermeture automatique des portes/trappes")
	private MajordomConfig majordom = new MajordomConfig();

	@Comment("Configuration de l'énergie des voleurs")
	private AsthmaConfig asthma = new AsthmaConfig();

	// Getters and setters

	public MajordomConfig majordom() {
		return majordom;
	}

	public void majordom(MajordomConfig value) {
		majordom = value;
	}

	public AsthmaConfig asthma() {
		return asthma;
	}

	public void asthma(AsthmaConfig value) {
		asthma = value;
	}

}
