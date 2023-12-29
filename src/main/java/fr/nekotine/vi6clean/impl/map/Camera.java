package fr.nekotine.vi6clean.impl.map;

import java.util.ArrayList;
import java.util.Collection;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.PacketListener;

import fr.nekotine.core.inventory.ItemStackBuilder;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.map.annotation.ComposingMap;
import fr.nekotine.core.map.annotation.MapDictKey;
import fr.nekotine.core.map.element.MapLocationElement;
import fr.nekotine.core.track.ClientTrackModule;

public class Camera {
	@MapDictKey
	@ComposingMap
	private String name = "";
	@ComposingMap
	private MapLocationElement location = new MapLocationElement();
	@ComposingMap
	private int number;
	@ComposingMap
	private Material material;
	@ComposingMap
	private int slot;
	
	private boolean idle = true;
	private Collection<Player> spectators = new ArrayList<Player>();
	private String idleURL = "cc36ec5323f765ff59f916d2d1a1ec7496854cf7bd26dd2f2cbadc3dd49279c8";
	private String activeURL = "415e470805d5fc611c44b87c39e7e4dfd474049b24f3f6bba230e250f9b4b87c";
	private ItemStack idleHead = new ItemStackBuilder(Material.PLAYER_HEAD)
			.skull(idleURL)
			.build();
	private ItemStack activeHead = new ItemStackBuilder(Material.PLAYER_HEAD)
			.skull(activeURL)
			.build();
	private ArmorStand as;
	
	//
	
	private void updateArmorStand() {
		as.getEquipment().setHelmet(idle? idleHead : activeHead);
	}
	private PacketListener packetAdapter = new PacketAdapter(Ioc.resolve(JavaPlugin.class),
			PacketType.Play.Client.POSITION_LOOK,
			PacketType.Play.Client.LOOK) {
		public void onPacketReceiving(PacketEvent evt) {
			if(evt.getPacketType()==PacketType.Play.Client.POSITION_LOOK) {
				onPositionLook(evt);
			}else {
				onLook(evt);
			}
		}
		private void onPositionLook(PacketEvent evt) {
			
		}
		private void onLook(PacketEvent evt) {
			
		}
	};
	
	//
	
	public String getName() {
		return name;
	}
	public MapLocationElement getLocation() {
		return location;
	}
	public int getNumber() {
		return number;
	}
	public Material getMaterial() {
		return material;
	}
	public int getSlot() {
		return slot;
	}
	public void spectate(Player player) {
		if(idle) {
			idle = false;
			updateArmorStand();
		}
		Ioc.resolve(ClientTrackModule.class).untrack(player);
		spectators.add(player);
		player.teleport(location.toLocation(player.getWorld()));
		//freeze le joueur
	}
	public void setup(World world) {
		as = (ArmorStand)world.spawnEntity(location.toLocation(world), EntityType.ARMOR_STAND, SpawnReason.CUSTOM);
		as.setAI(false);
		as.setCollidable(false);
		as.setInvisible(true);
		as.setSilent(true);
		as.setInvulnerable(true);
		as.setGravity(false);
		as.addDisabledSlots(EquipmentSlot.CHEST,EquipmentSlot.FEET,EquipmentSlot.HAND,EquipmentSlot.LEGS,EquipmentSlot.FEET,EquipmentSlot.OFF_HAND);
		updateArmorStand();
		ProtocolLibrary.getProtocolManager().addPacketListener(packetAdapter);
	}
	public void clean() {
		as.remove();
		ProtocolLibrary.getProtocolManager().removePacketListener(packetAdapter);
		spectators.clear();
	}
}
