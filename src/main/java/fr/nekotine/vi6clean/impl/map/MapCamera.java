package fr.nekotine.vi6clean.impl.map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.joml.Vector2d;

import fr.nekotine.core.inventory.ItemStackBuilder;
import fr.nekotine.core.map.annotation.ComposingMap;
import fr.nekotine.core.map.annotation.MapDictKey;
import fr.nekotine.core.util.BukkitUtil;

public class MapCamera{
	public static enum State {
		INACTIVE("cc36ec5323f765ff59f916d2d1a1ec7496854cf7bd26dd2f2cbadc3dd49279c8"),
		STARTING("306107dee7f8d298a4486fd4d6044b90922e63bd29f069fdfbf26f54fd4ebec5"),
		ACTIVE("415e470805d5fc611c44b87c39e7e4dfd474049b24f3f6bba230e250f9b4b87c");
		
		private final ItemStack item;
		State(String url) {
			item = new ItemStackBuilder(Material.PLAYER_HEAD)
			.skull(url)
			.build();
		}
		private ItemStack getItem() {
			return item;
		}
	}
	@MapDictKey
	@ComposingMap
	private String name = "";

	@ComposingMap
	private Location location = BukkitUtil.defaultLocation();
	
	@ComposingMap
	private Material material;
	
	@ComposingMap
	private Vector2d position = new Vector2d();

	private State state;
	private ArmorStand display;
	
	public void setup(World world) {
		display = (ArmorStand)world.spawnEntity(location, EntityType.ARMOR_STAND);
		display.setAI(false);
		display.setCollidable(false);
		display.setInvisible(true);
		display.setSilent(true);
		display.setInvulnerable(true);
		display.setGravity(false);
		display.addDisabledSlots(EquipmentSlot.CHEST,EquipmentSlot.FEET,EquipmentSlot.HAND,EquipmentSlot.LEGS,EquipmentSlot.FEET,EquipmentSlot.OFF_HAND);
		setState(State.INACTIVE);
	}
	public void setState(State state) {
		this.state = state;
		display.getEquipment().setHelmet(state.getItem(), true);
	}
	public State getState() {
		return state;
	}
}
