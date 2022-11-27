package fr.nekotine.vi6clean.impl.map.util;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

public class SerializableBoundingBox implements ConfigurationSerializable{

	// ---------------------- Serialization

	public static SerializableBoundingBox deserialize(Map<String, Object> map) {
		var corner1 = (Vector) 			map.get("corner1");
		var corner2 = (Vector) 			map.get("corner2");
		return new SerializableBoundingBox(corner1, corner2);
	}

	@Override
	public @NotNull Map<String, Object> serialize() {
		var map = new HashMap<String, Object>();
		map.put("corner1", boundingBox.getMin());
		map.put("corner2", boundingBox.getMax());
		return map;
	}

	// ----------------------
	
	private BoundingBox boundingBox = new BoundingBox();
	
	public SerializableBoundingBox() {}
	
	public SerializableBoundingBox(Vector corner1, Vector corner2) {
		boundingBox.resize(corner1.getX(), corner1.getY(), corner1.getZ(), corner2.getX(), corner2.getY(), corner2.getZ());
	}
	
	public BoundingBox get() {
		return boundingBox;
	}

}
