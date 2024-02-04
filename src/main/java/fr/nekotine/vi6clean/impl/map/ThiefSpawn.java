package fr.nekotine.vi6clean.impl.map;

import org.bukkit.Location;

import fr.nekotine.core.map.annotation.GenerateCommandFor;
import fr.nekotine.core.serialization.configurationserializable.annotation.ComposingConfiguration;
import fr.nekotine.core.serialization.configurationserializable.annotation.MapDictKey;
import fr.nekotine.core.util.BukkitUtil;

public class ThiefSpawn {

	@MapDictKey
	private String name = "";
	
	@GenerateCommandFor
	@ComposingConfiguration
	private Location spawnPoint = BukkitUtil.defaultLocation();
	
	@GenerateCommandFor
	@ComposingConfiguration
	private Location minimapPosition = BukkitUtil.defaultLocation();

	public Location getSpawnPoint() {
		return spawnPoint;
	}

	public Location getMinimapPosition() {
		return minimapPosition;
	}

	public String getName() {
		return name;
	}
	
}
