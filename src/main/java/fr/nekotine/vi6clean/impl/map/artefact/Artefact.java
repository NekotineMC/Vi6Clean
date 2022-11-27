package fr.nekotine.vi6clean.impl.map.artefact;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;

import fr.nekotine.vi6clean.impl.map.util.SerializableBoundingBox;

@SerializableAs("Artefact")
public class Artefact implements ConfigurationSerializable{

	// ---------------------- Serialization
	
	public static Artefact deserialize(Map<String,Object> map) {
		var visual 		= (ArtefactVisual) 			map.get("visual");
		var boundingBox = (SerializableBoundingBox) map.get("boundingBox");
		return new Artefact(visual, boundingBox);
	}
	
	@Override
	public @NotNull Map<String, Object> serialize() {
		var map = new HashMap<String, Object>();
		map.put("visual", visual);
		map.put("boundingBox", boundingBox);
		return map;
	}
	
	// ----------------------
	
	private final SerializableBoundingBox boundingBox;
	
	private ArtefactVisual visual;
	
	public Artefact(ArtefactVisual visual, SerializableBoundingBox boundingBox) {
		this.visual = visual;
		this.boundingBox = boundingBox;
	}
	
	public void setVisual(ArtefactVisual visual) {
		this.visual = visual;
	}
	
	public ArtefactVisual getVisual() {
		return visual;
	}
	
	public BoundingBox getBoundingBox() {
		return boundingBox.get();
	}
	
}
