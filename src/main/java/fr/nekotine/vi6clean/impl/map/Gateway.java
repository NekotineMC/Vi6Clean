package fr.nekotine.vi6clean.impl.map;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;

import fr.nekotine.vi6clean.impl.map.util.SerializableBoundingBox;

public class Gateway implements ConfigurationSerializable{

	// ---------------------- Serialization

	public static Gateway deserialize(Map<String,Object> map) {
		var room		= (String)					map.get("room");
		var boundingBox	= (SerializableBoundingBox) map.get("boundingBox");
		return new Gateway(room, boundingBox);
	}
	
	@Override
	public @NotNull Map<String, Object> serialize() {
		var map = new HashMap<String, Object>();
		map.put("room", room);
		map.put("boundingBox", boundingBox);
		return map;
	}
	
	// ----------------------
	
	private String room;
	
	private SerializableBoundingBox boundingBox;
	
	public Gateway(String room, SerializableBoundingBox boundingBox) {
		this.room = room;
		this.boundingBox = boundingBox;
	}
	
	public BoundingBox getBoundingBox() {
		return boundingBox.get();
	}
	
	public String getRoom() {
		return room;
	}
}
