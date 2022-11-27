package fr.nekotine.vi6clean.impl.map.artefact;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.serialization.SerializableAs;
import org.jetbrains.annotations.NotNull;

/**
 * L'artefact représenté par cette classe est un block ayant deux état différent s'il est volé où a prendre.
 * @author XxGoldenbluexX
 *
 */
@SerializableAs("BlockArtefactVisual")
public class BlockArtefactVisual implements ArtefactVisual{

	// ------- Serialization
	
	public static BlockArtefactVisual deserialize(Map<String,Object> map) {
		var loc 			= (Location)map.get("location");
		var availableString = (String)	map.get("available");
		var stolenString 	= (String)	map.get("stolen");
		var available 	= Bukkit.createBlockData(availableString);
		var stolen 		= Bukkit.createBlockData(stolenString);
		return new BlockArtefactVisual(loc, available, stolen);
	}
	
	@Override
	public @NotNull Map<String, Object> serialize() {
		var map = new HashMap<String, Object>();
		map.put("location", location);
		map.put("available", availableBlockData.getAsString(true));
		map.put("stolen", stolenBlockData.getAsString(true));
		return map;
	}
	
	// -------
	
	private Location location;
	
	private BlockData stolenBlockData;
	
	private BlockData availableBlockData;
	
	public BlockArtefactVisual(Location loc, BlockData available, BlockData stolen) {
		location = loc;
		availableBlockData = available;
		stolenBlockData = stolen;
	}
	
	@Override
	public void place() {
		location.getBlock().setBlockData(availableBlockData);
	}

	@Override
	public void remove() {
		location.getBlock().setBlockData(stolenBlockData);
	}
}
