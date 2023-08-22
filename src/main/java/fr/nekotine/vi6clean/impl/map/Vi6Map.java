package fr.nekotine.vi6clean.impl.map;

import java.util.Collection;

import fr.nekotine.core.map.annotation.ComposingMap;
import fr.nekotine.core.map.annotation.MapElementTyped;
import fr.nekotine.core.map.element.MapBlockLocationElement;
import fr.nekotine.core.map.element.MapBoundingBoxElement;
import fr.nekotine.core.map.element.MapDictionaryElement;
import fr.nekotine.core.map.element.MapPositionElement;
import fr.nekotine.vi6clean.impl.map.artefact.Artefact;

public class Vi6Map{
	
	@MapElementTyped(MapBlockLocationElement.class)
	@ComposingMap
	private MapDictionaryElement<Artefact> artefacts = new MapDictionaryElement<>();
	
	@MapElementTyped(Entrance.class)
	@ComposingMap
	private MapDictionaryElement<Entrance> entrances = new MapDictionaryElement<>();
	
	@MapElementTyped(MapBoundingBoxElement.class)
	@ComposingMap
	private MapDictionaryElement<MapBoundingBoxElement> exits = new MapDictionaryElement<>();
	
	@MapElementTyped(MapPositionElement.class)
	@ComposingMap
	private MapDictionaryElement<MapPositionElement> guardSpawns = new MapDictionaryElement<>();
	
	@MapElementTyped(MapPositionElement.class)
	@ComposingMap
	private MapDictionaryElement<MapPositionElement> thiefMinimapSpawns = new MapDictionaryElement<>();
	
	public Collection<MapPositionElement> getGuardSpawns(){
		return guardSpawns.backingMap().values();
	}
	
	public Collection<MapPositionElement> getThiefMinimapSpawns(){
		return thiefMinimapSpawns.backingMap().values();
	}

	public MapDictionaryElement<Artefact> getArtefacts() {
		return artefacts;
	}

	public MapDictionaryElement<Entrance> getEntrances() {
		return entrances;
	}

	public MapDictionaryElement<MapBoundingBoxElement> getExits() {
		return exits;
	}
	
}
