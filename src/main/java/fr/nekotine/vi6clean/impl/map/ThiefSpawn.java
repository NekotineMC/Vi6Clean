package fr.nekotine.vi6clean.impl.map;

import fr.nekotine.core.map.annotation.ComposingMap;
import fr.nekotine.core.map.annotation.MapDictKey;
import fr.nekotine.core.map.element.MapLocationElement;
import fr.nekotine.core.map.element.MapPositionElement;

public class ThiefSpawn {

	@MapDictKey
	@ComposingMap
	private String name = "";
	
	@ComposingMap
	private MapPositionElement spawnPoint = new MapPositionElement();
	
	@ComposingMap
	private MapLocationElement minimapPosition = new MapLocationElement();

	public MapPositionElement getSpawnPoint() {
		return spawnPoint;
	}

	public MapLocationElement getMinimapPosition() {
		return minimapPosition;
	}

	public String getName() {
		return name;
	}
	
}
