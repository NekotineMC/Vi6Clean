package fr.nekotine.vi6clean.impl.map;

import org.bukkit.util.BoundingBox;

import fr.nekotine.core.map.annotation.ComposingMap;
import fr.nekotine.core.map.annotation.MapDictKey;

public class Entrance {

	@MapDictKey
	@ComposingMap
	private String name = "";

	@ComposingMap
	private BoundingBox entranceTriggerBox = new BoundingBox();
	
	@ComposingMap
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
