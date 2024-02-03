package fr.nekotine.vi6clean.impl.map;

import org.bukkit.util.BoundingBox;

import fr.nekotine.core.map.annotation.GenerateCommandFor;
import fr.nekotine.core.serialization.configurationserializable.annotation.ComposingConfiguration;
import fr.nekotine.core.serialization.configurationserializable.annotation.MapDictKey;

public class RoomCaptor {

	@MapDictKey
	private String name = "";
	
	@GenerateCommandFor
	@ComposingConfiguration
	private String room = "";
	
	@GenerateCommandFor
	@ComposingConfiguration
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
