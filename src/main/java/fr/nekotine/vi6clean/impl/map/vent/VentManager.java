package fr.nekotine.vi6clean.impl.map.vent;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected.Half;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Marker;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;

import fr.nekotine.core.block.BlockPatch;
import fr.nekotine.core.block.tempblock.AppliedTempBlockPatch;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.util.EventUtil;
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
		}
		EventUtil.unregister(this);
	}
	
	@EventHandler
	public void onPlayerInterract(PlayerInteractEvent evt) {
		if (evt.hasBlock()) {
			var block = evt.getClickedBlock();
			var player = evt.getPlayer();
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
	public void onEntityDismount(EntityDismountEvent evt) {
		var dismounted = evt.getDismounted();
		if (dismounted instanceof Player player) {
			for (var v : vents) {
				if (v.player == player) {
					System.out.println("FOUND");
					 v.player = null;
					 var world = Ioc.resolve(Vi6Game.class).getWorld();
					 world.getBlockAt(new Location(world,
							 v.ventLocation.getBlockX(),
							 v.ventLocation.getBlockY(),
							 v.ventLocation.getBlockZ())).setType(Material.BARRIER);
					 return;
				}
			}
		}
	}
	
	private class Vent {
		private Vent(BlockVector location) {
			this.ventLocation = location;
		}
		
		private Entity seat;
		
		private Player player = null;
		
		private BlockVector ventLocation;
		
		private List<Vent> connectedVentsList = new LinkedList<>();
		
		private Vent[] connectedVents;
		
	}
	
}
