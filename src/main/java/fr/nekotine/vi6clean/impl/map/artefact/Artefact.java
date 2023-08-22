package fr.nekotine.vi6clean.impl.map.artefact;

import org.bukkit.util.BoundingBox;

import fr.nekotine.core.map.annotation.ComposingMap;
import fr.nekotine.core.map.element.MapBlockLocationElement;
import fr.nekotine.core.map.element.MapBoundingBoxElement;
import fr.nekotine.core.map.element.MapPositionElement;

public class Artefact{
	
	@ComposingMap
	private MapPositionElement position = new MapPositionElement();
	
	@ComposingMap
	private MapBlockLocationElement blockPosition = new MapBlockLocationElement();
	
	@ComposingMap
	private MapBoundingBoxElement boundingBox = new MapBoundingBoxElement();
	
	private ArtefactVisual visual;
	
	public Artefact(ArtefactVisual visual, MapBoundingBoxElement boundingBox) {
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
