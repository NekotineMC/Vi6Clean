package fr.nekotine.vi6clean.impl.tool.teamwide;

import java.util.ArrayList;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.Pair;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;

import fr.nekotine.core.NekotineCore;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.vi6clean.Vi6Main;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.impl.game.Vi6Game;

public class GuardScanner {

	private @NotNull BukkitTask task;
	
	public void startScanning() {
		task = Bukkit.getScheduler().runTaskTimer(NekotineCore.getAttachedPlugin(), this::scan, 0, 20 * 5);// normal = 30s reduced = 10s
	}
	
	public void stopScanning() {
		task.cancel();
	}
	
	public void scan() {
		var pmanager = ProtocolLibrary.getProtocolManager();
		var game = Vi6Main.IOC.resolve(Vi6Game.class);
		var idList = new ArrayList<Integer>(game.getGuards().size());
		for (var guard : game.getGuards()) {
			var ploc = guard.getLocation();
			@SuppressWarnings("deprecation")
			var eid = Bukkit.getUnsafe().nextEntityId();
			idList.add(eid);
			
			var createPacket = pmanager.createPacket(PacketType.Play.Server.SPAWN_ENTITY);
			var createInts = createPacket.getIntegers();
			var createDoubles = createPacket.getDoubles();
			createInts.write(0, eid);												// Entity id
			createPacket.getUUIDs().write(0, UUID.randomUUID());					// UUID
			createPacket.getEntityTypeModifier().write(0, EntityType.ARMOR_STAND);	// Entity type
			createPacket.getBytes().write(0, (byte)(ploc.getPitch()*256.0F / 360.0F));			// Pitch
			createPacket.getBytes().write(1, (byte)(ploc.getYaw()*256.0F / 360.0F));				// Yaw
			createDoubles.write(0, ploc.getX());									// X
			createDoubles.write(1, ploc.getY());									// Y
			createDoubles.write(2, ploc.getZ());									// Z
			
			var metadataPacket = pmanager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
			metadataPacket.getIntegers().write(0, eid);								// Entity id
			var dataValues = new ArrayList<WrappedDataValue>(2);
			var serializer = WrappedDataWatcher.Registry.get(Byte.class);
			dataValues.add(new WrappedDataValue(0, serializer, (byte)(0x20 | 0x40))); // Invisible + Glowing effect
			dataValues.add(new WrappedDataValue(15, serializer, (byte)(0x04 | 0x08))); // Has arm + no BasePlate
			metadataPacket.getDataValueCollectionModifier().write(0, dataValues);
			
			var equipPacket = pmanager.createPacket(PacketType.Play.Server.ENTITY_EQUIPMENT);
			equipPacket.getIntegers().write(0, eid);
			var pairList = new ArrayList<Pair<EnumWrappers.ItemSlot, ItemStack>>(4);
			pairList.add(new Pair<>(EnumWrappers.ItemSlot.HEAD, ItemStackUtil.skull(guard)));
			pairList.add(new Pair<>(EnumWrappers.ItemSlot.CHEST, new ItemStack(Material.NETHERITE_CHESTPLATE)));
			pairList.add(new Pair<>(EnumWrappers.ItemSlot.LEGS, new ItemStack(Material.NETHERITE_LEGGINGS)));
			pairList.add(new Pair<>(EnumWrappers.ItemSlot.FEET, new ItemStack(Material.NETHERITE_BOOTS)));
			equipPacket.getSlotStackPairLists().write(0, pairList);
			
			for (var thief : game.getThiefs()) {
				pmanager.sendServerPacket(thief, createPacket);
				pmanager.sendServerPacket(thief, metadataPacket);
				pmanager.sendServerPacket(thief, equipPacket);
			}
		}
		
		Vi6Sound.SCANNER_SCAN.play(game);
		
		new BukkitRunnable() {
			
			@Override
			public void run() {
				
				var destroyPacket = pmanager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
				destroyPacket.getIntLists().write(0, idList);
				
				for (var thief : game.getThiefs()) {
					pmanager.sendServerPacket(thief, destroyPacket);
				}
			}
		}.runTaskLater(NekotineCore.getAttachedPlugin(), 7 * 20);
	}
	
}
