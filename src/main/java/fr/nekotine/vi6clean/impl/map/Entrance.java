package fr.nekotine.vi6clean.impl.map;

import fr.nekotine.core.map.annotation.ComposingMap;
import fr.nekotine.core.map.element.MapBoundingBoxElement;
import fr.nekotine.core.map.element.MapPositionElement;

public class Entrance {

	@ComposingMap
	private MapPositionElement spawnPoint = new MapPositionElement();
	
	@ComposingMap
	private MapPositionElement minimapPosition = new MapPositionElement();

	@ComposingMap
	private MapBoundingBoxElement entranceTriggerBox = new MapBoundingBoxElement();
	
	@ComposingMap
	private MapBoundingBoxElement blockingBox = new MapBoundingBoxElement();
	
	public MapPositionElement getSpawnPoint() {
		return spawnPoint;
	}
	
	public MapPositionElement getMinimapPosition() {
		return minimapPosition;
	}

	public MapBoundingBoxElement getEntranceTriggerBox() {																																																
		return entranceTriggerBox;
	}

	public MapBoundingBoxElement getBlockingBox() {
		return blockingBox;
	}
	
	
	
}
