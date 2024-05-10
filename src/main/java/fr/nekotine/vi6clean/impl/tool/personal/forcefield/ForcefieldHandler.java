package fr.nekotine.vi6clean.impl.tool.personal.forcefield;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import fr.nekotine.core.glow.EntityGlowModule;
import fr.nekotine.core.glow.TeamColor;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.BukkitUtil;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.util.SpatialUtil;
import fr.nekotine.vi6clean.Vi6Main;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.map.Vi6Map;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;

@ToolCode("forcefield")
public class ForcefieldHandler extends ToolHandler<Forcefield>{
	
	private Map<String,DoorData> fieldsDisplay = new HashMap<>();
	
	private EntityGlowModule glowingModule = Ioc.resolve(EntityGlowModule.class);
	
	public ForcefieldHandler() {
		super(Forcefield::new);
	}
	
	@Override
	protected void onStartHandling() {
		var world = Ioc.resolve(Vi6Game.class).getWorld();
		var map = Ioc.resolve(Vi6Map.class);
		var bdata = Bukkit.createBlockData(Material.GLASS);
		for (var gateEntry : map.getGates().entrySet()) {
			var display = SpatialUtil.fillBoundingBox(world, gateEntry.getValue(), bdata);
			display.setVisibleByDefault(false);
			fieldsDisplay.put(gateEntry.getKey(), new DoorData(display));
		}
	}
	
	@Override
	protected void onStopHandling() {
		var world = Ioc.resolve(Vi6Game.class).getWorld();
		var fields = Ioc.resolve(Vi6Map.class).getGates();
		for (var field : fieldsDisplay.keySet()) {
			BukkitUtil.fillBoundingBoxWith(world, fields.get(field), Material.AIR);
			fieldsDisplay.get(field).display.remove();
		}
		fieldsDisplay.clear();
		super.onStopHandling();
	}

	@Override
	protected void onAttachedToPlayer(Forcefield tool, Player player) {
	}

	@Override
	protected void onDetachFromPlayer(Forcefield tool, Player player) {
	}
	
	@EventHandler
	private void onPlayerInterract(PlayerInteractEvent evt) {
		if (evt.getHand() != EquipmentSlot.HAND) {
			return;
		}
		var evtP = evt.getPlayer();
		var optionalTool = getTools().stream().filter(t -> evtP.equals(t.getOwner()) && t.getItemStack().isSimilar(evt.getItem())).findFirst();
		if (optionalTool.isEmpty()) {
			return;
		}
		if (EventUtil.isCustomAction(evt, CustomAction.HIT_ANY) && tryPlaceField(optionalTool.get())) {
			evt.setCancelled(true);
		}
	}
	
	@EventHandler
	private void onTick(TickElapsedEvent evt) {
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
		}
	}
	
	public void placeField(String field) {
		var door = fieldsDisplay.get(field);
		if (!door.playerOpened) {
			var map = Ioc.resolve(Vi6Map.class);
			var world = Ioc.resolve(Vi6Game.class).getWorld();
			var fields = map.getGates();
			var fieldBound = fields.get(field);
			BukkitUtil.fillBoundingBoxWith(world, fieldBound, Material.GRAY_STAINED_GLASS);
		}
		door.activated = true;
	}
	
	public void removeField(String field) {
		var door = fieldsDisplay.get(field);
		if (!door.playerOpened) {
			var map = Ioc.resolve(Vi6Map.class);
			var world = Ioc.resolve(Vi6Game.class).getWorld();
			var fields = map.getGates();
			var fieldBound = fields.get(field);
			BukkitUtil.fillBoundingBoxWith(world, fieldBound, Material.AIR);
		}
		door.activated = false;
	}
	
	public void closeField(String field) {
		var door = fieldsDisplay.get(field);
		if (door.activated) {
			var map = Ioc.resolve(Vi6Map.class);
			var world = Ioc.resolve(Vi6Game.class).getWorld();
			var fields = map.getGates();
			var fieldBound = fields.get(field);
			BukkitUtil.fillBoundingBoxWith(world, fieldBound, Material.GRAY_STAINED_GLASS);
		}
		door.playerOpened = false;
	}
	
	public void openField(String field) {
		var door = fieldsDisplay.get(field);
		if (door.activated) {
			var map = Ioc.resolve(Vi6Map.class);
			var world = Ioc.resolve(Vi6Game.class).getWorld();
			var fields = map.getGates();
			var fieldBound = fields.get(field);
			BukkitUtil.fillBoundingBoxWith(world, fieldBound, Material.AIR);
		}
		door.playerOpened = true;
	}
	
	public @Nullable String getTargetedGate(Player player) {
		var eyeLoc = player.getEyeLocation();
		var start = eyeLoc.toVector();
		var dir = eyeLoc.getDirection();
		return Ioc.resolve(Vi6Map.class).getGates().entrySet().stream()
				.filter(bb -> bb.getValue().rayTrace(start, dir, 100.0) != null)
				.sorted((a,b)-> (int)(start.distanceSquared(a.getValue().getCenter())-start.distanceSquared(b.getValue().getCenter())))
				.map(e -> e.getKey()).findFirst().orElse(null);
	}
	
	private boolean tryPlaceField(Forcefield ff) {
		var gate = getTargetedGate(ff.getOwner());
		if (gate != null) {
			if (fieldsDisplay.get(gate).activated) {
				removeField(gate);
			}else {
				placeField(gate);
			}
			return true;
		}
		return false;
	}
	
	private void displayDoor(String door, Player player) {
		var target = getTargetedGate(player);
		var enabled = target == door ? TeamColor.DARK_AQUA : TeamColor.GOLD;
		var disabled = target == door ? TeamColor.AQUA : TeamColor.YELLOW;
		var doorEntity = fieldsDisplay.get(door);
		player.showEntity(Ioc.resolve(Vi6Main.class), doorEntity.display);
		if (doorEntity.activated) {
			glowingModule.glowEntityFor(doorEntity.display, player, enabled);
		}else {
			glowingModule.glowEntityFor(doorEntity.display, player, disabled);
		}
	}
	
	private void hideDoor(String door, Player player) {
		var doorEntity = fieldsDisplay.get(door);
		player.hideEntity(Ioc.resolve(Vi6Main.class), doorEntity.display);
		glowingModule.unglowEntityFor(doorEntity.display, player);
	}
	
	private class DoorData{
		
		private DoorData(BlockDisplay display) {
			this.display = display;
		}
		
		private BlockDisplay display;
		
		private boolean activated;
		
		private boolean playerOpened;
		
	}
	
}
