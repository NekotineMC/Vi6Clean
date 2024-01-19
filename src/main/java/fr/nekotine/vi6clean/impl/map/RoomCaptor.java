package fr.nekotine.vi6clean.impl.map;

import org.bukkit.util.BoundingBox;

import fr.nekotine.core.map.annotation.ComposingMap;
import fr.nekotine.core.map.annotation.MapDictKey;

public class RoomCaptor {

	@ComposingMap
	@MapDictKey
	private String name = "";
	
	@ComposingMap
	private String room = "";
	
	@ComposingMap
	private BoundingBox triggerBox = new BoundingBox();

	public BoundingBox getTriggerBox() {
		return triggerBox;
	}
	
	public void setTriggerBox(BoundingBox triggerBox) {
		this.triggerBox = triggerBox;
	}
	
	public String getName() {
		return name;
	}
	
	public String getRoom() {
		return room;
	}
	
}
