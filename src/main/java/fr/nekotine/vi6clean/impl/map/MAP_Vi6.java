package fr.nekotine.vi6clean.impl.map;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;
import org.jetbrains.annotations.NotNull;

@SerializableAs("MAP_Vi6")
public class MAP_Vi6 implements ConfigurationSerializable{

	public MAP_Vi6(Map<String,Object> map) {
		
	}
	
	@Override
	public @NotNull Map<String, Object> serialize() {
		var map = new HashMap<String, Object>();
		return map;
	}

	
	
}
