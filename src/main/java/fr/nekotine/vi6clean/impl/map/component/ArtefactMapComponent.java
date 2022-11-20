package fr.nekotine.vi6clean.impl.map.component;

import fr.nekotine.core.map.GameMap;
import fr.nekotine.core.map.annotation.ComposingMap;
import fr.nekotine.core.map.component.MapComponent;
import fr.nekotine.core.map.component.MapRectangleAreaElement;

public class ArtefactMapComponent extends MapComponent {

	@ComposingMap(Name = "CaptureZone")
	private MapRectangleAreaElement area = new MapRectangleAreaElement(getMap(), "CaptureZone");
	
	public ArtefactMapComponent(GameMap map, String name) {
		super(map, name);
		new MapRectangleAreaElement(map, name);
	}
	
	// Getter / Setter
	
	public MapRectangleAreaElement getArea() {
		return area;
	}

}
