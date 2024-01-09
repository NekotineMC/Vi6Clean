package fr.nekotine.vi6clean.impl.map;

import fr.nekotine.core.map.annotation.ComposingMap;
import fr.nekotine.core.map.element.MapBoundingBoxElement;

public class RoomCaptor {

	private String name = "";
	
	@ComposingMap
	private MapBoundingBoxElement triggerBox = new MapBoundingBoxElement();

	public MapBoundingBoxElement getTriggerBox() {
		return triggerBox;
	}
	
	public void setTriggerBox(MapBoundingBoxElement triggerBox) {
		this.triggerBox = triggerBox;
	}
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
	
}
