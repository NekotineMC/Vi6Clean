package fr.nekotine.vi6clean.impl.tool.personal.recall;

import java.util.ArrayList;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.track.ClientTrackModule;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.util.InventoryUtil;
import fr.nekotine.core.util.SpatialUtil;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpEndEvent;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpStartEvent;
import fr.nekotine.vi6clean.impl.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

@ToolCode("recall")
public class RecallHandler extends ToolHandler<RecallHandler.Recall> {

	private final int TELEPORT_DELAY_TICKS = (int) (20 * getConfiguration().getDouble("teleport_delay", 6));

	private final int PARTICLE_NUMBER = getConfiguration().getInt("particle_number", 2);

	private final int COOLDOWN_TICKS = (int) (20 * getConfiguration().getDouble("cooldown", 1));

	public RecallHandler() {
		super(Recall::new);
		Ioc.resolve(ModuleManager.class).tryLoad(ClientTrackModule.class);
	}

	@EventHandler
	private void onPlayerInterract(PlayerInteractEvent evt) {
		if (evt.getHand() != EquipmentSlot.HAND || !EventUtil.isCustomAction(evt, CustomAction.HIT_ANY)) {
			return;
		}
		var statusModule = Ioc.resolve(StatusFlagModule.class);
		var player = evt.getPlayer();
		var tool = getToolFromItem(evt.getItem());
		if (tool == null || (!tool.activated && player.hasCooldown(Material.CHORUS_FRUIT))
				|| statusModule.hasAny(player, EmpStatusFlag.get())) {
			return;
		}

		// use
		var l1 = new ArrayList<Location>();
		var l2 = new ArrayList<Location>();
		var w = Ioc.resolve(Vi6Game.class).getWorld();
		SpatialUtil.helix(3, 0.75, 3, 0, 0.25, v -> l1.add(v.toLocation(w)));
		SpatialUtil.helix(3, 0.75, 3, Math.PI, 0.25, v -> l2.add(v.toLocation(w)));

		var trackModule = Ioc.resolve(ClientTrackModule.class);
		if (tool.activated) {
			recall(tool);
		} else {
			tool.activated = true;
			trackModule.untrack(player);
			tool.placedLocation = player.getLocation().clone();
			editItem(tool, item -> item.setData(DataComponentTypes.ITEM_MODEL, Material.POPPED_CHORUS_FRUIT.key()));
			player.setCooldown(Material.CHORUS_FRUIT, TELEPORT_DELAY_TICKS);
		}

		evt.setCancelled(true);
	}

	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		for (var tool : getTools()) {
			var owner = tool.getOwner();
			if (tool.activated) {
				if (owner.hasCooldown(Material.CHORUS_FRUIT)) {
					var world = owner.getWorld();
					world.spawnParticle(Particle.GLOW, tool.placedLocation.clone().subtract(0, 0.1, 0), PARTICLE_NUMBER,
							0.1, 0, 0.1, 0);
					/*
					 * SpatialUtil.helix(3*(owner.getCooldown(Material.CHORUS_FRUIT)/
					 * TELEPORT_DELAY_TICKS), 0.75, 3, 0, 0.25, v ->
					 * world.spawnParticle(Particle.GLOW, v.toLocation(world).add(0.5, 100, 0.5),
					 * 1,0,0,0,0,null));
					 * SpatialUtil.helix(3*(owner.getCooldown(Material.CHORUS_FRUIT)/
					 * TELEPORT_DELAY_TICKS), 0.75, 3, Math.PI, 0.25, v ->
					 * world.spawnParticle(Particle.GLOW, v.toLocation(world).add(0.5, 100, 0.5),
					 * 1,0,0,0,0,null));
					 */
				} else {
					recall(tool);
				}
			}
		}
	}

	private void recall(Recall tool) {
		var trackModule = Ioc.resolve(ClientTrackModule.class);
		var owner = tool.getOwner();
		tool.activated = false;
		trackModule.track(owner);
		owner.teleport(tool.placedLocation);
		owner.setCooldown(Material.CHORUS_FRUIT, COOLDOWN_TICKS);
		editItem(tool, item -> item.resetData(DataComponentTypes.ITEM_MODEL));
	}

	@Override
	protected void onAttachedToPlayer(Recall tool) {
	}

	@Override
	protected void onDetachFromPlayer(Recall tool) {
		Ioc.resolve(ClientTrackModule.class).track(tool.getOwner());
	}

	@Override
	protected void onToolCleanup(Recall tool) {
	}

	@EventHandler
	private void onEmpStart(EntityEmpStartEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				item.editMeta(m -> m.displayName(getDisplayName().decorate(TextDecoration.STRIKETHROUGH)
						.append(Component.text(" - ")).append(Component.text("Brouillé", NamedTextColor.RED))));
				p.setCooldown(item, COOLDOWN_TICKS);
				onToolCleanup(getToolFromItem(item));
			});
		}
	}

	@EventHandler
	private void onEmpStop(EntityEmpEndEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				item.editMeta(m -> m.displayName(getDisplayName()));
			});
		}
	}

	public static class Recall extends Tool {

		public Recall(ToolHandler<?> handler) {
			super(handler);
		}

		private boolean activated;

		private Location placedLocation;
	}
}
