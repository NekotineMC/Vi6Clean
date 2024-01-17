package fr.nekotine.vi6clean.impl.map;

import java.util.Collection;

import org.bukkit.Location;

import fr.nekotine.core.map.annotation.CommandGeneratorOverride;
import fr.nekotine.core.map.annotation.ComposingMap;
import fr.nekotine.core.map.annotation.MapElementTyped;
import fr.nekotine.core.map.command.generator.PositionCommandGenerator;
import fr.nekotine.core.map.element.MapBlockBoundingBoxElement;
import fr.nekotine.core.map.element.MapDictionaryElement;
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
	
	@CommandGeneratorOverride(PositionCommandGenerator.class)
	@MapElementTyped(Location.class)
	@ComposingMap
	private MapDictionaryElement<Location> guardSpawns = new MapDictionaryElement<>();
	
	@CommandGeneratorOverride(PositionCommandGenerator.class)
	@MapElementTyped(Location.class)
	@ComposingMap
	private MapDictionaryElement<Location> thiefMinimapSpawns = new MapDictionaryElement<>();
	
	@MapElementTyped(Koth.class)
	@ComposingMap
	private MapDictionaryElement<Koth> koths = new MapDictionaryElement<>();
	
	@MapElementTyped(RoomCaptor.class)
	@ComposingMap
	private MapDictionaryElement<RoomCaptor> roomCaptors = new MapDictionaryElement<>();
	
	
	public Collection<Location> getGuardSpawns(){
		return guardSpawns.backingMap().values();
	}
	
	public Collection<Location> getThiefMinimapSpawns(){
		return thiefMinimapSpawns.backingMap().values();
	}

	public MapDictionaryElement<Artefact> getArtefacts() {
		return artefacts;
	}

	public MapDictionaryElement<Entrance> getEntrances() {
		return entrances;
	}
	
	public MapDictionaryElement<RoomCaptor> getRoomCaptors() {
		return roomCaptors;
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
}
