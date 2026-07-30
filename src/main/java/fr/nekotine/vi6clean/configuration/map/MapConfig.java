package fr.nekotine.vi6clean.configuration.map;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;

@ConfigSerializable
public class MapConfig {

	@Comment("Configuration des points de capture")
	private KothConfig koth = new KothConfig();

	// Getters and setters

	public KothConfig koth() {
		return koth;
	}

	public void koth(KothConfig value) {
		koth = value;
	}

}
