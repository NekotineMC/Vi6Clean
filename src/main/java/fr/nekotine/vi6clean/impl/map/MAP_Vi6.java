package fr.nekotine.vi6clean.impl.map;

import fr.nekotine.core.map.GameMap;
import fr.nekotine.core.map.MapIdentifier;
import fr.nekotine.core.map.MapTypeIdentifier;
import fr.nekotine.core.map.annotation.ComposingMap;
import fr.nekotine.vi6clean.impl.map.component.ArtefactMapComponent;

public class MAP_Vi6 extends GameMap{

	private static final long serialVersionUID = "Vi6Map".hashCode();
	
	public static MapTypeIdentifier IDENTIFIER = new MapTypeIdentifier("Vi6Map", MAP_Vi6.class) {

		@Override
		public GameMap generateTypedMap(MapIdentifier id) {
			return new MAP_Vi6(id);
		}
		
	};
	
	@ComposingMap(Name = "Artefacts")
	private ArtefactMapComponent artefact = new ArtefactMapComponent(this, "Artefact1");

	public MAP_Vi6(MapIdentifier type) {
		super(type);
	}

	
	
}
