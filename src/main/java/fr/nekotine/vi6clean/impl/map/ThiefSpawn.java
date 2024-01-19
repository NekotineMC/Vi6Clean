package fr.nekotine.vi6clean.impl.map;

import org.bukkit.Location;

import fr.nekotine.core.map.annotation.ComposingMap;
import fr.nekotine.core.map.annotation.MapDictKey;
import fr.nekotine.core.util.BukkitUtil;

public class ThiefSpawn {

	@MapDictKey
	@ComposingMap
	private String name = "";
	
	@ComposingMap
	private Location spawnPoint = BukkitUtil.defaultLocation();
	
	@ComposingMap
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
