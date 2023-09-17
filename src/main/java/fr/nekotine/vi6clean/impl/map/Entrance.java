package fr.nekotine.vi6clean.impl.map;

import fr.nekotine.core.map.annotation.ComposingMap;
import fr.nekotine.core.map.element.MapBlockBoundingBoxElement;
import fr.nekotine.core.map.element.MapBoundingBoxElement;

public class Entrance {

	private String name;

	@ComposingMap
	private MapBoundingBoxElement entranceTriggerBox = new MapBoundingBoxElement();
	
	@ComposingMap
	private MapBlockBoundingBoxElement blockingBox = new MapBlockBoundingBoxElement();

	public MapBoundingBoxElement getEntranceTriggerBox() {																																																
		return entranceTriggerBox;
	}

	public MapBlockBoundingBoxElement getBlockingBox() {
		return blockingBox;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
	
}
