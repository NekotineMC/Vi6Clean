package fr.nekotine.vi6clean.impl.map;

import fr.nekotine.core.map.annotation.GenerateCommandFor;
import fr.nekotine.core.serialization.configurationserializable.annotation.ComposingConfiguration;
import fr.nekotine.core.serialization.configurationserializable.annotation.MapDictKey;
import fr.nekotine.core.util.BukkitUtil;
import org.bukkit.Location;
import org.bukkit.util.BlockVector;

public class ThiefSpawn {

	@MapDictKey
	private String name = "";

	@GenerateCommandFor
	@ComposingConfiguration
	private Location spawnPoint = BukkitUtil.defaultLocation();

	@GenerateCommandFor
	@ComposingConfiguration
	private BlockVector minimapPosition = new BlockVector();

	public Location getSpawnPoint() {
		return spawnPoint;
	}

	public BlockVector getMinimapPosition() {
		return minimapPosition;
	}

	public String getName() {
		return name;
	}
}
