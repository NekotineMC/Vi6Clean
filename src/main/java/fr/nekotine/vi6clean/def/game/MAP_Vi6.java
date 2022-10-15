package fr.nekotine.vi6clean.def.game;

import fr.nekotine.core.map.GameMap;
import fr.nekotine.core.map.MapIdentifier;
import fr.nekotine.core.map.MapTypeIdentifier;

public class MAP_Vi6 extends GameMap{

	private static final long serialVersionUID = "Vi6Map".hashCode();
	
	public static MapTypeIdentifier IDENTIFIER = new MapTypeIdentifier("Vi6Map", MAP_Vi6.class) {

		@Override
		public GameMap generateTypedMap(MapIdentifier id) {
			return new MAP_Vi6(id);
		}
		
	};

	public MAP_Vi6(MapIdentifier type) {
		super(type);
	}

	
	
}
