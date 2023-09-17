package fr.nekotine.vi6clean.impl.map.artefact;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

/**
 * L'artefact représenté par cette classe est une entitée qui disparait quand elle est volée.
 * 
 * Cette classe utilise une api dépréciée pour serializer des entitées.
 * 
 * @author XxGoldenbluexX
 *
 */
@SerializableAs("EntityArtefactVisual")
public class EntityArtefactVisual implements ArtefactVisual{

	// ------- Serialization

	public static EntityArtefactVisual deserialize(Map<String,Object> map) {
		var loc 			= (Location)map.get("location");
		var serializedEntity = (byte[])	map.get("serializedEntity");
		return new EntityArtefactVisual(loc, serializedEntity);
	}
	
	@Override
	public @NotNull Map<String, Object> serialize() {
		var map = new HashMap<String, Object>();
		
		return map;
	}
	
	// -------
	
	private byte[] serializedEntity;
	
	private Entity entity;
	
	private Location loc;
	
	public EntityArtefactVisual(Location location, byte[] serializedEntity) {
		loc = location;
		loc.getBlock();
		this.serializedEntity = serializedEntity;
	}
	
	@Override
	public void place() {
		//entity = Bukkit.getUnsafe().deserializeEntity(serializedEntity, loc.getWorld());
	}

	@Override
	public void remove() {
		if (entity != null) {
			entity.remove();
		}
	}
	
	public byte[] getSerializedEntity() {
		return serializedEntity;
	}
	
	public void setSerializedEntity(byte[] serializedEntity) {
		this.serializedEntity = serializedEntity;
	}

}
