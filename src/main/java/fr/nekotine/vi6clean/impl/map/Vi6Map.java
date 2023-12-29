package fr.nekotine.vi6clean.impl.map;

import java.util.Collection;

import fr.nekotine.core.map.annotation.ComposingMap;
import fr.nekotine.core.map.annotation.MapElementTyped;
import fr.nekotine.core.map.element.MapBlockBoundingBoxElement;
import fr.nekotine.core.map.element.MapDictionaryElement;
import fr.nekotine.core.map.element.MapPositionElement;
import fr.nekotine.vi6clean.impl.map.artefact.Artefact;
import fr.nekotine.vi6clean.impl.map.koth.Koth;

public class Vi6Map{
	
	@MapElementTyped(Artefact.class)
	@ComposingMap
	private MapDictionaryElement<Artefact> artefacts = new MapDictionaryElement<>();
	
	@MapElementTyped(Entrance.class)
	@ComposingMap
	private MapDictionaryElement<Entrance> entrances = new MapDictionaryElement<>();
	
	@MapElementTyped(ThiefSpawn.class)
	@ComposingMap
	private MapDictionaryElement<ThiefSpawn> thiefSpawns = new MapDictionaryElement<>();
	
	@MapElementTyped(MapBlockBoundingBoxElement.class)
	@ComposingMap
	private MapDictionaryElement<MapBlockBoundingBoxElement> exits = new MapDictionaryElement<>();
	
	@MapElementTyped(MapPositionElement.class)
	@ComposingMap
	private MapDictionaryElement<MapPositionElement> guardSpawns = new MapDictionaryElement<>();
	
	@MapElementTyped(MapPositionElement.class)
	@ComposingMap
	private MapDictionaryElement<MapPositionElement> thiefMinimapSpawns = new MapDictionaryElement<>();
	
	@MapElementTyped(Koth.class)
	@ComposingMap
	private MapDictionaryElement<Koth> koths = new MapDictionaryElement<>();
	
	@MapElementTyped(Camera.class)
	@ComposingMap
	private MapDictionaryElement<Camera> cameras = new MapDictionaryElement<>();
	
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
	
	public MapDictionaryElement<ThiefSpawn> getThiefSpawns() {
		return thiefSpawns;
	}

	public MapDictionaryElement<MapBlockBoundingBoxElement> getExits() {
		return exits;
	}
	
	public MapDictionaryElement<Koth> getKoths() {
		return koths;
	}
	
	public MapDictionaryElement<Camera> getCameras(){
		return cameras;
	}
}
