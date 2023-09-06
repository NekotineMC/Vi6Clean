package fr.nekotine.vi6clean.impl.map;

import fr.nekotine.core.map.annotation.ComposingMap;
import fr.nekotine.core.map.element.MapBlockBoundingBoxElement;
import fr.nekotine.core.map.element.MapBoundingBoxElement;
import fr.nekotine.core.map.element.MapLocationElement;
import fr.nekotine.core.map.element.MapPositionElement;

public class Entrance {

	private String name;
	
	@ComposingMap
	private MapPositionElement spawnPoint = new MapPositionElement();
	
	@ComposingMap
	private MapLocationElement minimapPosition = new MapLocationElement();

	@ComposingMap
	private MapBoundingBoxElement entranceTriggerBox = new MapBoundingBoxElement();
	
	@ComposingMap
	private MapBlockBoundingBoxElement blockingBox = new MapBlockBoundingBoxElement();
	
	public MapPositionElement getSpawnPoint() {
		return spawnPoint;
	}
	
	public MapLocationElement getMinimapPosition() {
		return minimapPosition;
	}

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
