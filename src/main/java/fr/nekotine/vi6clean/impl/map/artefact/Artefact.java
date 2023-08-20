package fr.nekotine.vi6clean.impl.map.artefact;

import org.bukkit.util.BoundingBox;

import fr.nekotine.core.map.annotation.ComposingMap;
import fr.nekotine.core.map.element.MapBlockPositionElement;
import fr.nekotine.core.map.element.MapPositionElement;
import fr.nekotine.vi6clean.impl.map.util.SerializableBoundingBox;

public class Artefact{

	private ArtefactVisual sousArtefact;
	
	@ComposingMap()
	private MapPositionElement position = new MapPositionElement();
	
	@ComposingMap()
	private MapBlockPositionElement blockPosition = new MapBlockPositionElement();
	
	private SerializableBoundingBox boundingBox;
	
	private ArtefactVisual visual;
	
	public Artefact(ArtefactVisual visual, SerializableBoundingBox boundingBox) {
		this.visual = visual;
		this.boundingBox = boundingBox;
	}
	
	public Artefact() {
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
