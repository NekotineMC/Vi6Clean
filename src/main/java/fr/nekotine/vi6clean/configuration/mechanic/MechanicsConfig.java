package fr.nekotine.vi6clean.configuration.mechanic;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;

@ConfigSerializable
public class MechanicsConfig {

	@Comment("Configuration de la fermeture automatique des portes/trappes")
	private MajordomConfig majordom = new MajordomConfig();

	@Comment("Configuration de l'énergie des voleurs")
	private AsthmaConfig asthma = new AsthmaConfig();

	@Comment("Configuration de la suffocation des voleurs")
	private SuffocationConfig suffocation = new SuffocationConfig();
	
	@Comment("Configuration de l'invisibilité")
	private InvisibilityConfig invisibility = new InvisibilityConfig();

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
	
	public SuffocationConfig suffocation() {
		return suffocation;
	}
	
	public void suffocation(SuffocationConfig value) {
		suffocation = value;
	}

	public InvisibilityConfig invisibility() {
		return invisibility;
	}

	public void invisibility(InvisibilityConfig value) {
		invisibility = value;
	}

}
