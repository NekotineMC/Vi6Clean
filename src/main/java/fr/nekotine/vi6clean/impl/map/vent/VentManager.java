package fr.nekotine.vi6clean.impl.map.vent;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected.Half;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BlockVector;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import fr.nekotine.core.block.BlockPatch;
import fr.nekotine.core.block.tempblock.AppliedTempBlockPatch;
import fr.nekotine.core.glow.EntityGlowModule;
import fr.nekotine.core.glow.TeamColor;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.vi6clean.Vi6Main;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.map.Vi6Map;

public class VentManager implements Listener{

	private List<Vent> vents = new LinkedList<>();
	
	private List<AppliedTempBlockPatch> ventPatchs = new LinkedList<>();
	
	public VentManager() {
		var random = new Random();
		var map = Ioc.resolve(Vi6Map.class);
		var allVents = map.getVents();
		if (allVents == null) {
			return;
		}
		var totalVents = allVents.size();
		var nbVents = totalVents > 2 ? random.nextInt(2, totalVents+1) : totalVents;
		if (nbVents <= 1) {
			return;
		}
		var selectedVents = new LinkedList<>(allVents.keySet());
		Collections.shuffle(selectedVents);
		while (selectedVents.size() > nbVents) {
			selectedVents.pop();
		}
		for (var vent : selectedVents) {
			vents.add(new Vent(allVents.get(vent)));
		}
		// MST (Minimal Span Tree)
		var root = vents.get(0);
		var spanTree = new LinkedList<Vent>();
		spanTree.add(root);
		var tempCol = vents.stream().skip(1).collect(Collectors.toCollection(LinkedList::new));
		tempCol.sort((a,b)->(int)(
				a.ventLocation.distanceSquared(root.ventLocation)-
				b.ventLocation.distanceSquared(root.ventLocation)));
		for (var item : tempCol) {
			var shortest = spanTree.get(0);
			var shortestDist = shortest.ventLocation.distanceSquared(item.ventLocation);
			for (var treeItem : spanTree) {
				var dist = treeItem.ventLocation.distanceSquared(item.ventLocation);
				if (dist < shortestDist) {
					shortest = treeItem;
					shortestDist = dist;
				}
			}
			shortest.connectedVentsList.add(item);
			item.connectedVentsList.add(shortest);
			spanTree.add(item);
		}
		//
		var ventPatch = new BlockPatch(b -> b.setType(Material.BARRIER));
		var northGridPatch = new BlockPatch(b -> {
			b.setType(Material.IRON_TRAPDOOR);
			var data = (TrapDoor)b.getBlockData();
			data.setOpen(true);
			data.setHalf(Half.TOP);
			data.setFacing(BlockFace.NORTH);
			b.setBlockData(data);
			});
		var southGridPatch = new BlockPatch(b -> {
			b.setType(Material.IRON_TRAPDOOR);
			var data = (TrapDoor)b.getBlockData();
			data.setOpen(true);
			data.setHalf(Half.TOP);
			data.setFacing(BlockFace.SOUTH);
			b.setBlockData(data);
			});
		var eastGridPatch = new BlockPatch(b -> {
			b.setType(Material.IRON_TRAPDOOR);
			var data = (TrapDoor)b.getBlockData();
			data.setOpen(true);
			data.setHalf(Half.TOP);
			data.setFacing(BlockFace.EAST);
			b.setBlockData(data);
			});
		var westGridPatch = new BlockPatch(b -> {
			b.setType(Material.IRON_TRAPDOOR);
			var data = (TrapDoor)b.getBlockData();
			data.setOpen(true);
			data.setHalf(Half.TOP);
			data.setFacing(BlockFace.WEST);
			b.setBlockData(data);
			});
		var world = Ioc.resolve(Vi6Game.class).getWorld();
		for (var vent : spanTree) {
			ventPatchs.add(ventPatch.patch(world.getBlockAt(vent.ventLocation.toLocation(world))));
			var vect = new BlockVector(vent.ventLocation);
			// NORTH
			vect.add(new Vector(0,0,-1));
			var block = world.getBlockAt(vect.toLocation(world));
			if (block.getType().isEmpty()) {
				ventPatchs.add(northGridPatch.patch(block));
			}
			// SOUTH
			vect.add(new Vector(0,0,2));
			block = world.getBlockAt(vect.toLocation(world));
			if (block.getType().isEmpty()) {
				ventPatchs.add(southGridPatch.patch(block));
			}
			// EAST
			vect.add(new Vector(1,0,-1));
			block = world.getBlockAt(vect.toLocation(world));
			if (block.getType().isEmpty()) {
				ventPatchs.add(eastGridPatch.patch(block));
			}
			// WEST
			vect.add(new Vector(-2,0,0));
			block = world.getBlockAt(vect.toLocation(world));
			if (block.getType().isEmpty()) {
				ventPatchs.add(westGridPatch.patch(block));
			}
			vent.display = (BlockDisplay)world.spawnEntity(new Location(world,
					vent.ventLocation.getX(),
					vent.ventLocation.getY(),
					vent.ventLocation.getZ()), EntityType.BLOCK_DISPLAY, SpawnReason.CUSTOM);
			vent.display.setBlock(Bukkit.createBlockData(Material.STONE));
			vent.display.setVisibleByDefault(false);
			vent.display.setGlowing(true);
			var seat = (ArmorStand)world.spawnEntity(new Location(world,
					vent.ventLocation.getX()+0.5,
					vent.ventLocation.getY()-0.5,
					vent.ventLocation.getZ()+0.5), EntityType.ARMOR_STAND, SpawnReason.CUSTOM);
			seat.setInvisible(true);
			seat.setMarker(true);
			vent.seat = seat;
			// libérer de la ram (micro opti de merde)
			vent.connectedVents = vent.connectedVentsList.toArray(Vent[]::new);
			vent.connectedVentsList = null;
		}
		EventUtil.register(this);
	}
	
	public void dispose() {
		for (var patch : ventPatchs) {
			patch.unpatch(false);
		}
		for (var vent : vents) {
			vent.seat.remove();
			vent.display.remove();
		}
		EventUtil.unregister(this);
	}
	
	@EventHandler
	private void onPlayerInterract(PlayerInteractEvent evt) {
		if (evt.getHand() != EquipmentSlot.HAND) {
			return;
		}
		var player = evt.getPlayer();
		// Player is inside vent
		for (var v : vents) {
			if (v.player == player) {
				if (EventUtil.isCustomAction(evt, CustomAction.HIT_ANY) && tryCrawlVent(v)) {
					evt.setCancelled(true);
				}
				return;
			}
		}
		// Player is outside vent
		if (evt.hasBlock()) {
			var block = evt.getClickedBlock();
			//var playerWrapper = Ioc.resolve(WrappingModule.class).getWrapper(player, PlayerWrapper.class);
			var blockLoc = block.getLocation().toVector().toBlockVector();
			Vent vent = null;
			for (var v : vents) {
				if (v.ventLocation.equals(blockLoc)) {
					vent = v;
					break;
				}
			}
			if (vent == null) {
				return;
			}
			if (player.getLocation().toVector().distanceSquared(blockLoc) <= 9) {
				if (vent.player == null) {
					block.setType(Material.AIR);
					vent.player = player;
					vent.seat.addPassenger(player);
				}
			}
		}
	}
	
	@EventHandler
	private void onEntityDismount(EntityDismountEvent evt) {
		var dismounted = evt.getEntity();
		if (dismounted instanceof Player player) {
			var plugin = Ioc.resolve(Vi6Main.class);
			for (var v : vents) {
				if (v.player == player) {
					 v.player = null;
					 var world = Ioc.resolve(Vi6Game.class).getWorld();
					 player.setInvulnerable(true);
					 new BukkitRunnable() {// Pour éviter de suffoquer un tick, on délais

						 @Override
						public void run() {
							 player.setInvulnerable(false);
						 }
						 
					 }.runTask(plugin);
					world.getBlockAt(new Location(world,
							 v.ventLocation.getBlockX(),
							 v.ventLocation.getBlockY(),
							 v.ventLocation.getBlockZ())).setType(Material.BARRIER);
					for (var nearVent : v.connectedVents) {
						player.hideEntity(plugin, nearVent.display);
					}
					player.hideEntity(plugin, player);
					 
					//// Select available places
					var vect = new BlockVector(v.ventLocation);
					var aim = player.getEyeLocation().getDirection().add(v.ventLocation);
					double smallestDist = 3;
					Vector nearest = null;
					// NORTH
					vect.add(new Vector(0,-1,-1));
					var dist = vect.distanceSquared(aim);
					if (dist < smallestDist && vect.toLocation(world).getBlock().getType().isEmpty()) {
						dist = smallestDist;
						nearest = new BlockVector().copy(vect);
					}
					// SOUTH
					vect.add(new Vector(0,0,2));
					dist = vect.distanceSquared(aim);
					if (dist < smallestDist && vect.toLocation(world).getBlock().getType().isEmpty()) {
						dist = smallestDist;
						nearest = new BlockVector().copy(vect);
					}
					// EAST
					vect.add(new Vector(1,0,-1));
					dist = vect.distanceSquared(aim);
					if (dist < smallestDist && vect.toLocation(world).getBlock().getType().isEmpty()) {
						dist = smallestDist;
						nearest = new BlockVector().copy(vect);
					}
					// WEST
					vect.add(new Vector(-2,0,0));
					dist = vect.distanceSquared(aim);
					if (dist < smallestDist && vect.toLocation(world).getBlock().getType().isEmpty()) {
						dist = smallestDist;
						nearest = new BlockVector().copy(vect);
					}
					if (nearest != null) {
						nearest.add(new Vector(0.5,0,0.5));
						player.teleport(nearest.toLocation(world, player.getYaw(), player.getPitch()));
						return;
					}
					//// Select available upper places
					vect = new BlockVector(v.ventLocation);
					// NORTH
					vect.add(new Vector(0,-1,-1));
					dist = vect.distanceSquared(aim);
					if (dist < smallestDist && vect.toLocation(world).getBlock().getType() == Material.IRON_TRAPDOOR) {
						dist = smallestDist;
						nearest = new BlockVector().copy(vect);
					}
					// SOUTH
					vect.add(new Vector(0,0,2));
					dist = vect.distanceSquared(aim);
					if (dist < smallestDist && vect.toLocation(world).getBlock().getType() == Material.IRON_TRAPDOOR) {
						dist = smallestDist;
						nearest = new BlockVector().copy(vect);
					}
					// EAST
					vect.add(new Vector(1,0,-1));
					dist = vect.distanceSquared(aim);
					if (dist < smallestDist && vect.toLocation(world).getBlock().getType() == Material.IRON_TRAPDOOR) {
						dist = smallestDist;
						nearest = new BlockVector().copy(vect);
					}
					// WEST
					vect.add(new Vector(-2,0,0));
					dist = vect.distanceSquared(aim);
					if (dist < smallestDist && vect.toLocation(world).getBlock().getType() == Material.IRON_TRAPDOOR) {
						dist = smallestDist;
						nearest = new BlockVector().copy(vect);
					}
					if (nearest != null) {
						nearest.add(new Vector(0.5,0,0.5));
						player.teleport(nearest.toLocation(world, player.getYaw(), player.getPitch()));
						return;
					}
					return;
				}
			}
		}
	}
	
	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		var plugin = Ioc.resolve(Vi6Main.class);
		var glowModule = Ioc.resolve(EntityGlowModule.class);
		for (var v :  vents) {
			if (v.player != null) {
				var targetVent = getTargetedVent(v.player);
				var enabled = targetVent == v ? TeamColor.BLUE : TeamColor.YELLOW;
				var disabled = targetVent == v ? TeamColor.DARK_RED : TeamColor.RED;
				for (var nearVent : v.connectedVents) {
					v.player.showEntity(plugin, nearVent.display);
					if (nearVent.isTraveledTo || nearVent.player != null) {
						glowModule.glowEntityFor(nearVent.display, v.player, disabled);
					}else {
						glowModule.glowEntityFor(nearVent.display, v.player, enabled);
					}
				}
			}
		}
		/*
		///
		var target = getTargetedGate(player);
		
		var doorEntity = fieldsDisplay.get(door);
		player.showEntity(Ioc.resolve(Vi6Main.class), doorEntity.display);
		if (doorEntity.activated) {
			glowingModule.glowEntityFor(doorEntity.display, player, enabled);
		}else {
			glowingModule.glowEntityFor(doorEntity.display, player, disabled);
		}
		//
		var doorEntity = fieldsDisplay.get(door);
		player.hideEntity(Ioc.resolve(Vi6Main.class), doorEntity.display);
		glowingModule.unglowEntityFor(doorEntity.display, player);
		//
		for (var tool : getTools()) {
			var owner = tool.getOwner();
			if (owner == null) {
				continue;
			}
			var inv = owner.getInventory();
			if (inv.getItemInMainHand().isSimilar(tool.getItemStack()) ||
					inv.getItemInOffHand().isSimilar(tool.getItemStack())) {
				for (var door : fieldsDisplay.keySet()) {
					displayDoor(door, owner);
				}
			}else {
				for (var door : fieldsDisplay.keySet()) {
					hideDoor(door, owner);
				}
			}
		}
		var gates = Ioc.resolve(Vi6Map.class).getGates();
		for (var doorKey : fieldsDisplay.keySet()) {
			var door = fieldsDisplay.get(doorKey);
			var bb = gates.get(doorKey);
			if (door.activated) {
				bb.expand(1);
				if (!door.playerOpened &&
						Ioc.resolve(Vi6Game.class).getGuards().stream().anyMatch(p -> bb.contains(p.getLocation().toVector()))) {
					bb.expand(-1);
					openField(doorKey);
				}else {
					bb.expand(-1);
				}
				bb.expand(1);
				if (door.playerOpened &&
						!Ioc.resolve(Vi6Game.class).getGuards().stream().anyMatch(p -> bb.contains(p.getLocation().toVector()))) {
					bb.expand(-1);
					closeField(doorKey);
				}else {
					bb.expand(-1);
				}
			}
		}*/
	}
	
	private boolean tryCrawlVent(Vent vent) {
		System.out.println("CRAWL");
		return false;
	}
	
	private @Nullable Vent getTargetedVent(Player player) {
		var eyeLoc = player.getEyeLocation();
		var start = eyeLoc.toVector();
		var dir = eyeLoc.getDirection();
		return vents.stream()
				.filter(v -> {
					var bb = new BoundingBox(
							v.ventLocation.getX(),
							v.ventLocation.getY(),
							v.ventLocation.getZ(),
							v.ventLocation.getX()+1,
							v.ventLocation.getY()+1,
							v.ventLocation.getZ()+1);
					return bb.rayTrace(start, dir, 100.0) != null;
				})
				.sorted((a,b)-> (int)(start.distanceSquared(a.ventLocation)-start.distanceSquared(b.ventLocation)))
				.findFirst().orElse(null);
	}
	
	private class Vent {
		private Vent(BlockVector location) {
			this.ventLocation = location;
		}
		
		private Entity seat;
		
		private BlockDisplay display;
		
		private Player player = null;
		
		private BlockVector ventLocation;
		
		private List<Vent> connectedVentsList = new LinkedList<>();
		
		private Vent[] connectedVents;
		
		private boolean isTraveledTo;
		
	}
	
}
