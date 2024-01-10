package fr.nekotine.vi6clean.impl.tool.personal.scanner;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.Pair;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.constant.Vi6Team;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;

@ToolCode("scanner")
public class ScannerHandler extends ToolHandler<Scanner>{
	private final int SCAN_DELAY_TICK = (int)(20*getConfiguration().getDouble("delay",30));
	
	private final int SCAN_LIFETIME_TICK = (int)(20*getConfiguration().getDouble("duration",30));

	private @NotNull BukkitTask task;
	
	public ScannerHandler() {
		super(Scanner::new);
	}
	@Override
	protected void onAttachedToPlayer(Scanner tool, Player player) {
	}
	@Override
	protected void onDetachFromPlayer(Scanner tool, Player player) {
	}
	
	public void startScanning() {
		task = Bukkit.getScheduler().runTaskTimer(
				Ioc.resolve(JavaPlugin.class), 
				this::performScan, 0, 
				SCAN_DELAY_TICK);
	}
	
	public void stopScanning() {
		if (task == null) {
			return;
		}
		task.cancel();
		task = null;
	}
	
	@Override
	protected void onStartHandling() {
		startScanning();
	}
	
	@Override
	protected void onStopHandling() {
		stopScanning();
	}
	
	public void performScan() {
		var pmanager = ProtocolLibrary.getProtocolManager();
		var game = Ioc.resolve(Vi6Game.class);
		var wrappingModule = Ioc.resolve(WrappingModule.class);
		var guardOwners = getTools().stream()
				.filter(t -> t.getOwner() != null)
				.map(Tool::getOwner)
				.filter(p -> {
					var flagModule = Ioc.resolve(StatusFlagModule.class);
					return !flagModule.hasAny(p, EmpStatusFlag.get());
				})
				.filter(p -> {
					var w = wrappingModule.getWrapperOptional(p, PlayerWrapper.class);
					return w.isPresent() && w.get().getTeam() == Vi6Team.GUARD;
				}).collect(Collectors.toCollection(LinkedList::new));
		var thiefOwners = getTools().stream()
				.filter(t -> t.getOwner() != null)
				.map(Tool::getOwner)
				.filter(p -> {
					var flagModule = Ioc.resolve(StatusFlagModule.class);
					return !flagModule.hasAny(p, EmpStatusFlag.get());
				})
				.filter(p -> {
					var w = wrappingModule.getWrapperOptional(p, PlayerWrapper.class);
					return w.isPresent() && w.get().getTeam() == Vi6Team.THIEF;
				}).collect(Collectors.toCollection(LinkedList::new));
		if (guardOwners.size() > 0) {
			var thiefScansIds = new LinkedList<Integer>();
			for (var thief : game.getThiefs()) {
				var scanInfo = makeScanCreationPackets(pmanager, thief);
				thiefScansIds.add(scanInfo.getFirst());
				for (var guard : guardOwners) {
					for (var p : scanInfo.getSecond()) {
						pmanager.sendServerPacket(guard, p);
					}
					Vi6Sound.SCANNER_SCAN.play(guard);
				}
			}
			new BukkitRunnable() {
				
				@Override
				public void run() {
					
					var destroyPacket = pmanager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
					destroyPacket.getIntLists().write(0, thiefScansIds);
					
					for (var guard : guardOwners) {
						pmanager.sendServerPacket(guard, destroyPacket);
					}
				}
			}.runTaskLater(Ioc.resolve(JavaPlugin.class), SCAN_LIFETIME_TICK);
		}
		if (thiefOwners.size() > 0) {
			var guardScansIds = new LinkedList<Integer>();
			for (var guard : game.getGuards()) {
				var scanInfo = makeScanCreationPackets(pmanager, guard);
				guardScansIds.add(scanInfo.getFirst());
				for (var thief : thiefOwners) {
					for (var p : scanInfo.getSecond()) {
						pmanager.sendServerPacket(thief, p);
					}
					Vi6Sound.SCANNER_SCAN.play(thief);
				}
			}
			new BukkitRunnable() {
				
				@Override
				public void run() {
					
					var destroyPacket = pmanager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
					destroyPacket.getIntLists().write(0, guardScansIds);
					
					for (var thief : thiefOwners) {
						pmanager.sendServerPacket(thief, destroyPacket);
					}
				}
			}.runTaskLater(Ioc.resolve(JavaPlugin.class), SCAN_LIFETIME_TICK);
		}
	}
	
	private Pair<Integer,PacketContainer[]> makeScanCreationPackets(ProtocolManager pmanager, Player player) {
		var scanLoc = player.getLocation();
		@SuppressWarnings("deprecation")
		var eid = Bukkit.getUnsafe().nextEntityId();
		
		var createPacket = pmanager.createPacket(PacketType.Play.Server.SPAWN_ENTITY);
		var createInts = createPacket.getIntegers();
		var createDoubles = createPacket.getDoubles();
		createInts.write(0, eid);														// Entity id
		createPacket.getUUIDs().write(0, UUID.randomUUID());							// UUID
		createPacket.getEntityTypeModifier().write(0, EntityType.ARMOR_STAND);			// Entity type
		createPacket.getBytes().write(0, (byte)(scanLoc.getPitch()*256.0F / 360.0F));	// Pitch
		createPacket.getBytes().write(1, (byte)(scanLoc.getYaw()*256.0F / 360.0F));		// Yaw
		createDoubles.write(0, scanLoc.getX());											// X
		createDoubles.write(1, scanLoc.getY());											// Y
		createDoubles.write(2, scanLoc.getZ());											// Z
		
		var metadataPacket = pmanager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
		metadataPacket.getIntegers().write(0, eid);										// Entity id
		var dataValues = new ArrayList<WrappedDataValue>(2);
		var serializer = WrappedDataWatcher.Registry.get(Byte.class);
		dataValues.add(new WrappedDataValue(0, serializer, (byte)(0x20 | 0x40))); 		// Invisible + Glowing effect
		dataValues.add(new WrappedDataValue(15, serializer, (byte)(0x04 | 0x08))); 		// Has arm + no BasePlate
		metadataPacket.getDataValueCollectionModifier().write(0, dataValues);
		
		var equipPacket = pmanager.createPacket(PacketType.Play.Server.ENTITY_EQUIPMENT);
		equipPacket.getIntegers().write(0, eid);
		var pairList = new ArrayList<Pair<EnumWrappers.ItemSlot, ItemStack>>(4);
		pairList.add(new Pair<>(EnumWrappers.ItemSlot.HEAD, ItemStackUtil.skull(player)));
		pairList.add(new Pair<>(EnumWrappers.ItemSlot.CHEST, new ItemStack(Material.NETHERITE_CHESTPLATE)));
		pairList.add(new Pair<>(EnumWrappers.ItemSlot.LEGS, new ItemStack(Material.NETHERITE_LEGGINGS)));
		pairList.add(new Pair<>(EnumWrappers.ItemSlot.FEET, new ItemStack(Material.NETHERITE_BOOTS)));
		equipPacket.getSlotStackPairLists().write(0, pairList);
		
				
		return new Pair<>(eid, new PacketContainer[]{createPacket, metadataPacket, equipPacket});
	}
}
