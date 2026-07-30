package fr.nekotine.vi6clean.configuration.map;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;

@ConfigSerializable
public class KothConfig {

	@Comment("Nombre maximum de point de capture dans une map")
	private int limit = 2;

	// Getters and setters

	public int limit() {
		return limit;
	}

	public void limit(int value) {
		limit = value;
	}
}
