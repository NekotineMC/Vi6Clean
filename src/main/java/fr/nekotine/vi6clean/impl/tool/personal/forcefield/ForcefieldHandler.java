package fr.nekotine.vi6clean.impl.tool.personal.forcefield;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.BukkitUtil;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.util.InventoryUtil;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.core.util.SpatialUtil;
import fr.nekotine.vi6clean.Vi6Main;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.map.Vi6Map;
import fr.nekotine.vi6clean.impl.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

@ToolCode("forcefield")
public class ForcefieldHandler extends ToolHandler<ForcefieldHandler.Forcefield> {

	private Map<String, DoorData> fieldsDisplay = new HashMap<>();

	private final int FORCEFIELD_NB_MAX = getConfiguration().getInt("nbmax", 2);

	public ForcefieldHandler() {
		super(Forcefield::new);
	}

	@Override
	protected void onStartHandling() {
		var world = Ioc.resolve(Vi6Game.class).getWorld();
		var map = Ioc.resolve(Vi6Map.class);
		var bdata = Bukkit.createBlockData(Material.GLASS);
		var gates = map.getGates();
		if (gates == null) {
			return;
		}
		for (var gateEntry : gates.entrySet()) {
			var display = SpatialUtil.fillBoundingBox(world, gateEntry.getValue(), bdata);
			display.setVisibleByDefault(false);
			display.setGlowing(true);
			fieldsDisplay.put(gateEntry.getKey(), new DoorData(display));
		}
	}

	@Override
	protected void onStopHandling() {
		var world = Ioc.resolve(Vi6Game.class).getWorld();
		var fields = Ioc.resolve(Vi6Map.class).getGates();
		if (fields == null) {
			return;
		}
		for (var field : fieldsDisplay.keySet()) {
			BukkitUtil.fillBoundingBoxWith(world, fields.get(field), Material.AIR);
			fieldsDisplay.get(field).display.remove();
		}
		fieldsDisplay.clear();
		super.onStopHandling();
	}

	@EventHandler
	private void onPlayerInterract(PlayerInteractEvent evt) {
		if (evt.getHand() != EquipmentSlot.HAND || !EventUtil.isCustomAction(evt, CustomAction.HIT_ANY)) {
			return;
		}
		var tool = getToolFromItem(evt.getItem());
		if (tool == null) {
			return;
		}
		var gate = getTargetedGate(tool.getOwner());
		if (gate != null) {
			if (fieldsDisplay.get(gate).activated) {
				tool.nbPosed = Math.max(0, tool.nbPosed - 1);
				removeField(gate);
			} else if (tool.nbPosed < FORCEFIELD_NB_MAX) {
				tool.nbPosed++;
				placeField(gate);
			}
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

			if (itemMatch(tool, inv.getItemInMainHand()) || itemMatch(tool, inv.getItemInOffHand())) {
				for (var door : fieldsDisplay.keySet()) {
					displayDoor(door, owner);
				}
			} else {
				for (var door : fieldsDisplay.keySet()) {
					hideDoor(door, owner);
				}
			}
		}

		var statusFlagModule = Ioc.resolve(StatusFlagModule.class);
		if (Ioc.resolve(Vi6Game.class).getGuards().stream()
				.filter(guard -> InventoryUtil.containTaggedItem(guard.getInventory(), TOOL_TYPE_KEY, getToolCode()))
				.allMatch(guard -> statusFlagModule.hasAny(guard, EmpStatusFlag.get()))) {
			for (var doorKey : fieldsDisplay.keySet()) {
				var door = fieldsDisplay.get(doorKey);
				if (door.activated && !door.playerOpened) {
					openField(doorKey);
				}
			}
			return;
		}
		var gates = Ioc.resolve(Vi6Map.class).getGates();
		for (var doorKey : fieldsDisplay.keySet()) {
			var door = fieldsDisplay.get(doorKey);
			var bb = gates.get(doorKey);
			if (door.activated) {
				bb.expand(1);
				if (!door.playerOpened && Ioc.resolve(Vi6Game.class).getGuards().stream()
						.anyMatch(p -> bb.contains(p.getLocation().toVector()))) {
					bb.expand(-1);
					openField(doorKey);
				} else {
					bb.expand(-1);
				}
				bb.expand(1);
				if (door.playerOpened && !Ioc.resolve(Vi6Game.class).getGuards().stream()
						.anyMatch(p -> bb.contains(p.getLocation().toVector()))) {
					bb.expand(-1);
					closeField(doorKey);
				} else {
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
				.sorted((a,
						b) -> (int) (start.distanceSquared(a.getValue().getCenter())
								- start.distanceSquared(b.getValue().getCenter())))
				.map(e -> e.getKey()).findFirst().orElse(null);
	}

	private void displayDoor(String door, Player player) {
		var target = getTargetedGate(player);
		var enabled = target == door ? Color.TEAL : Color.ORANGE;
		var disabled = target == door ? Color.AQUA : Color.YELLOW;
		var doorEntity = fieldsDisplay.get(door);
		player.showEntity(Ioc.resolve(Vi6Main.class), doorEntity.display);
		doorEntity.display.setGlowColorOverride(doorEntity.activated ? enabled : disabled);
	}

	private void hideDoor(String door, Player player) {
		var doorEntity = fieldsDisplay.get(door);
		player.hideEntity(Ioc.resolve(Vi6Main.class), doorEntity.display);
	}

	private class DoorData {

		private DoorData(BlockDisplay display) {
			this.display = display;
		}

		private BlockDisplay display;

		private boolean activated;

		private boolean playerOpened;
	}

	@Override
	protected void onAttachedToPlayer(Forcefield tool) {
	}

	@Override
	protected void onDetachFromPlayer(Forcefield tool) {
	}

	@Override
	protected void onToolCleanup(Forcefield tool) {
	}

	@Override
	protected ItemStack makeItem(Forcefield tool) {
		return ItemStackUtil.make(Material.IRON_DOOR, Component.text("Champ de force"));
	}

	public static class Forcefield extends Tool {

		public Forcefield(ToolHandler<?> handler) {
			super(handler);
		}

		private int nbPosed = 0;
	}
}
