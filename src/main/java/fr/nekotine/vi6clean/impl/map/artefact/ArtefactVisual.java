package fr.nekotine.vi6clean.impl.map.artefact;

import org.bukkit.configuration.serialization.ConfigurationSerializable;

public interface ArtefactVisual extends ConfigurationSerializable{
	
	public void place();
	
	public void remove();
	
}
