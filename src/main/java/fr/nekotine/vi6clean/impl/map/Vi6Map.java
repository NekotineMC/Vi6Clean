package fr.nekotine.vi6clean.impl.map;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.configuration.serialization.DelegateDeserialization;
import org.bukkit.util.BoundingBox;

import fr.nekotine.core.map.annotation.GenerateSpecificCommandFor;
import fr.nekotine.core.map.annotation.GenerateCommandFor;
import fr.nekotine.core.map.command.generator.BlockBoundingBoxCommandGenerator;
import fr.nekotine.core.map.command.generator.PositionCommandGenerator;
import fr.nekotine.core.reflexion.annotation.GenericBiTyped;
import fr.nekotine.core.serialization.configurationserializable.ConfigurationSerializableAdapted;
import fr.nekotine.core.serialization.configurationserializable.annotation.ComposingConfiguration;
import fr.nekotine.vi6clean.impl.map.artefact.Artefact;
import fr.nekotine.vi6clean.impl.map.koth.Koth;

@DelegateDeserialization(ConfigurationSerializableAdapted.class)
public class Vi6Map implements ConfigurationSerializableAdapted {

	@GenericBiTyped(a=String.class,b=Artefact.class)
	@GenerateCommandFor
	@ComposingConfiguration
	private Map<String,Artefact> artefacts = new HashMap<>();

	@GenericBiTyped(a=String.class,b=Entrance.class)
	@GenerateCommandFor
	@ComposingConfiguration
	private Map<String,Entrance> entrances = new HashMap<>();

	@GenericBiTyped(a=String.class,b=ThiefSpawn.class)
	@GenerateCommandFor
	@ComposingConfiguration
	private Map<String,ThiefSpawn> thiefSpawns = new HashMap<>();

	@GenericBiTyped(a=String.class,b=BoundingBox.class)
	@GenerateSpecificCommandFor(BlockBoundingBoxCommandGenerator.class)
	@ComposingConfiguration
	private Map<String,BoundingBox> exits = new HashMap<>();

	@GenericBiTyped(a=String.class,b=Location.class)
	@GenerateSpecificCommandFor(PositionCommandGenerator.class)
	@ComposingConfiguration
	private Map<String,Location> guardSpawns = new HashMap<>();

	@GenericBiTyped(a=String.class,b=Location.class)
	@GenerateSpecificCommandFor(PositionCommandGenerator.class)
	@ComposingConfiguration
	private Map<String,Location> thiefMinimapSpawns = new HashMap<>();

	@GenericBiTyped(a=String.class,b=Koth.class)
	@GenerateCommandFor
	@ComposingConfiguration
	private Map<String,Koth> koths = new HashMap<>();

	@GenericBiTyped(a=String.class,b=RoomCaptor.class)
	@GenerateCommandFor
	@ComposingConfiguration
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
