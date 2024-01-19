package fr.nekotine.vi6clean.impl.map;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.util.BoundingBox;

import fr.nekotine.core.map.annotation.CommandGeneratorOverride;
import fr.nekotine.core.map.annotation.ComposingMap;
import fr.nekotine.core.map.annotation.MapElementTyped;
import fr.nekotine.core.map.command.generator.BlockBoundingBoxCommandGenerator;
import fr.nekotine.core.map.command.generator.PositionCommandGenerator;
import fr.nekotine.vi6clean.impl.map.artefact.Artefact;
import fr.nekotine.vi6clean.impl.map.koth.Koth;

public class Vi6Map {

	@MapElementTyped(Artefact.class)
	@ComposingMap
	private Map<String,Artefact> artefacts = new HashMap<>();

	@MapElementTyped(Entrance.class)
	@ComposingMap
	private Map<String,Entrance> entrances = new HashMap<>();

	@MapElementTyped(ThiefSpawn.class)
	@ComposingMap
	private Map<String,ThiefSpawn> thiefSpawns = new HashMap<>();

	@CommandGeneratorOverride(BlockBoundingBoxCommandGenerator.class)
	@MapElementTyped(BoundingBox.class)
	@ComposingMap
	private Map<String,BoundingBox> exits = new HashMap<>();

	@CommandGeneratorOverride(PositionCommandGenerator.class)
	@MapElementTyped(Location.class)
	@ComposingMap
	private Map<String,Location> guardSpawns = new HashMap<>();

	@CommandGeneratorOverride(PositionCommandGenerator.class)
	@MapElementTyped(Location.class)
	@ComposingMap
	private Map<String,Location> thiefMinimapSpawns = new HashMap<>();

	@MapElementTyped(Koth.class)
	@ComposingMap
	private Map<String,Koth> koths = new HashMap<>();

	@MapElementTyped(RoomCaptor.class)
	@ComposingMap
	private Map<String,RoomCaptor> roomCaptors = new HashMap<>();

	public Collection<Location> getGuardSpawns() {
		return guardSpawns.values();
	}

	public Collection<Location> getThiefMinimapSpawns() {
		return thiefMinimapSpawns.values();
	}

	public Map<String,Artefact> getArtefacts() {
		return artefacts;
	}

	public Map<String,Entrance> getEntrances() {
		return entrances;
	}

	public Map<String,RoomCaptor> getRoomCaptors() {
		return roomCaptors;
	}

	public Map<String,ThiefSpawn> getThiefSpawns() {
		return thiefSpawns;
	}

	public Map<String,BoundingBox> getExits() {
		return exits;
	}

	public Map<String,Koth> getKoths() {
		return koths;
	}
}
