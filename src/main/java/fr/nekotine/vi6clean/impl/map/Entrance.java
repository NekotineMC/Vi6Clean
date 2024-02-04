package fr.nekotine.vi6clean.impl.map;

import org.bukkit.util.BoundingBox;

import fr.nekotine.core.map.annotation.GenerateCommandFor;
import fr.nekotine.core.serialization.configurationserializable.annotation.ComposingConfiguration;
import fr.nekotine.core.serialization.configurationserializable.annotation.MapDictKey;

public class Entrance {

	@MapDictKey
	private String name = "";

	@GenerateCommandFor
	@ComposingConfiguration
	private BoundingBox entranceTriggerBox = new BoundingBox();
	
	@GenerateCommandFor
	@ComposingConfiguration
	private BoundingBox blockingBox = new BoundingBox();

	public BoundingBox getEntranceTriggerBox() {																																																
		return entranceTriggerBox;
	}

	public BoundingBox getBlockingBox() {
		return blockingBox;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
	
}
