package fr.nekotine.vi6clean.impl.map;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.util.BlockVector;
import org.bukkit.util.BoundingBox;

import fr.nekotine.core.map.annotation.GenerateCommandFor;
import fr.nekotine.core.map.annotation.GenerateSpecificCommandFor;
import fr.nekotine.core.map.command.generator.BlockBoundingBoxCommandGenerator;
import fr.nekotine.core.map.command.generator.BlockLocationCommandGenerator;
import fr.nekotine.core.map.command.generator.PositionCommandGenerator;
import fr.nekotine.core.reflexion.annotation.GenericBiTyped;
import fr.nekotine.core.serialization.configurationserializable.ConfigurationSerializableAdapted;
import fr.nekotine.core.serialization.configurationserializable.annotation.ComposingConfiguration;
import fr.nekotine.vi6clean.impl.map.artefact.Artefact;
import fr.nekotine.vi6clean.impl.map.koth.Koth;

public class Vi6Map extends ConfigurationSerializableAdapted {

	public Vi6Map(Map<String, Object> map) {
		super(map);
	}

	@GenericBiTyped(a=String.class,b=Artefact.class)
	@GenerateCommandFor
	@ComposingConfiguration
	private Map<String,Artefact> artefacts;
	
	@GenericBiTyped(a=String.class,b=Entrance.class)
	@GenerateCommandFor
	@ComposingConfiguration
	private Map<String,Entrance> entrances;

	@GenericBiTyped(a=String.class,b=ThiefSpawn.class)
	@GenerateCommandFor
	@ComposingConfiguration
	private Map<String,ThiefSpawn> thiefSpawns;

	@GenericBiTyped(a=String.class,b=BoundingBox.class)
	@GenerateSpecificCommandFor(BlockBoundingBoxCommandGenerator.class)
	@ComposingConfiguration
	private Map<String,BoundingBox> exits;

	@GenericBiTyped(a=String.class,b=Location.class)
	@GenerateSpecificCommandFor(PositionCommandGenerator.class)
	@ComposingConfiguration
	private Map<String,Location> guardSpawns;

	@GenericBiTyped(a=String.class,b=Location.class)
	@GenerateSpecificCommandFor(PositionCommandGenerator.class)
	@ComposingConfiguration
	private Map<String,Location> thiefMinimapSpawns;

	@GenericBiTyped(a=String.class,b=Koth.class)
	@GenerateCommandFor
	@ComposingConfiguration
	private Map<String,Koth> koths;

	@GenericBiTyped(a=String.class,b=RoomCaptor.class)
	@GenerateCommandFor
	@ComposingConfiguration
	private Map<String,RoomCaptor> roomCaptors;
	
	@GenericBiTyped(a=String.class,b=BoundingBox.class)
	@GenerateSpecificCommandFor(BlockBoundingBoxCommandGenerator.class)
	@ComposingConfiguration
	private Map<String,BoundingBox> gates;
	
	@GenericBiTyped(a=String.class,b=BlockVector.class)
	@GenerateSpecificCommandFor(BlockLocationCommandGenerator.class)
	@ComposingConfiguration
	private Map<String, BlockVector> vents;

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
	
	public Map<String,BoundingBox> getGates() {
		return gates;
	}
	
	public Map<String,BlockVector> getVents() {
		return vents;
	}
}
