package fr.nekotine.vi6clean.impl.map;

import org.bukkit.util.BoundingBox;

import fr.nekotine.core.map.annotation.ComposingMap;
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
	private MapBoundingBoxElement blockingBox = new MapBoundingBoxElement();
	
	public MapPositionElement getSpawnPoint() {
		return spawnPoint;
	}
	
	public MapLocationElement getMinimapPosition() {
		return minimapPosition;
	}

	public MapBoundingBoxElement getEntranceTriggerBox() {																																																
		return entranceTriggerBox;
	}

	public BoundingBox getBlockingBox() {
		return blockingBox.get();
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
	
}
