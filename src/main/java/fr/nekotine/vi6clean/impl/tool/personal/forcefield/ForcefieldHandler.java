package fr.nekotine.vi6clean.impl.tool.personal.forcefield;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
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

	private List<String> activatedFields = new LinkedList<>();
	
	private Map<String,BlockDisplay> fieldsDisplay = new HashMap<>();
	
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
			fieldsDisplay.put(gateEntry.getKey(), display);
		}
	}
	
	@Override
	protected void onStopHandling() {
		for (var field : fieldsDisplay.values()) {
			field.remove();
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
	}
	
	public void placeField(String field) {
		var map = Ioc.resolve(Vi6Map.class);
		var world = Ioc.resolve(Vi6Game.class).getWorld();
		var fields = map.getGates();
		var fieldBound = fields.get(field);
		BukkitUtil.fillBoundingBoxWith(world, fieldBound, Material.GRAY_STAINED_GLASS);
		activatedFields.add(field);
	}
	
	public void removeField(String field) {
		var map = Ioc.resolve(Vi6Map.class);
		var world = Ioc.resolve(Vi6Game.class).getWorld();
		var fields = map.getGates();
		var fieldBound = fields.get(field);
		BukkitUtil.fillBoundingBoxWith(world, fieldBound, Material.AIR);
		activatedFields.remove(field);
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
			if (activatedFields.contains(gate)) {
				removeField(gate);
			}else {
				placeField(gate);
			}
		}
		return false;
	}
	
	private void displayDoor(String door, Player player) {
		var target = getTargetedGate(player);
		var enabled = target == door ? TeamColor.DARK_AQUA : TeamColor.GOLD;
		var disabled = target == door ? TeamColor.AQUA : TeamColor.YELLOW;
		var doorEntity = fieldsDisplay.get(door);
		player.showEntity(Ioc.resolve(Vi6Main.class), doorEntity);
		if (activatedFields.contains(door)) {
			glowingModule.glowEntityFor(doorEntity, player, enabled);
		}else {
			glowingModule.glowEntityFor(doorEntity, player, disabled);
		}
	}
	
	private void hideDoor(String door, Player player) {
		var doorEntity = fieldsDisplay.get(door);
		player.hideEntity(Ioc.resolve(Vi6Main.class), doorEntity);
		glowingModule.unglowEntityFor(doorEntity, player);
	}
	
}
