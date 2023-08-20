package fr.nekotine.vi6clean.impl.map;

import fr.nekotine.core.map.annotation.ComposingMap;
import fr.nekotine.core.map.annotation.MapElementTyped;
import fr.nekotine.core.map.element.MapBlockPositionElement;
import fr.nekotine.core.map.element.MapDictionaryElement;
import fr.nekotine.core.map.element.MapPositionElement;
import fr.nekotine.vi6clean.impl.map.artefact.Artefact;

public class MAP_Vi6{
	
	@MapElementTyped(MapBlockPositionElement.class)
	@ComposingMap()
	private MapDictionaryElement<Artefact> artefacts = new MapDictionaryElement<>();
	
	@MapElementTyped(MapPositionElement.class)
	@ComposingMap()
	private MapDictionaryElement<MapPositionElement> guardSpawns = new MapDictionaryElement<>();
	
	@MapElementTyped(MapPositionElement.class)
	@ComposingMap()
	private MapDictionaryElement<MapPositionElement> thiefMinimapSpawns = new MapDictionaryElement<>();
	
}
