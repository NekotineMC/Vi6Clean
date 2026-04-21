package fr.nekotine.vi6clean.impl.tool.personal.tracker;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.RayTraceResult;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.text.TextModule;
import fr.nekotine.core.text.style.NekotineStyles;
import fr.nekotine.core.text.tree.Leaf;
import fr.nekotine.core.ticking.TickTimeStamp;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.util.InventoryUtil;
import fr.nekotine.core.util.SpatialUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpEndEvent;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpStartEvent;
import fr.nekotine.vi6clean.impl.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.LodestoneTracker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

@ToolCode("tracker")
public class TrackerHandler extends ToolHandler<TrackerHandler.Tracker> {
	// private static final int REFRESH_DELAY_SECOND = 2;

	protected static final double RAY_DISTANCE = 100;

	protected static final int RAY_SIZE = 0;

	// private int n = 0;
	public TrackerHandler() {
		super(Tracker::new);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
	}

	//

	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		if (evt.timeStampReached(TickTimeStamp.Second) /* && ++n>=REFRESH_DELAY_SECOND */) {
			// n = 0;
			var textModule = Ioc.resolve(TextModule.class);
			var statusFlagModule = Ioc.resolve(StatusFlagModule.class);
			for (var tool : getTools()) {
				var owner = tool.getOwner();
				if (tool.tracked != null && !statusFlagModule.hasAny(owner, EmpStatusFlag.get())) {
					var distance = owner.getLocation().distance(tool.tracked.getLocation());
					editItem(tool, item -> {
						item.editMeta(meta -> {
							meta.displayName(
									getDisplayName()
											.append(Component
													.text(" - ").append(
															textModule
																	.message(Leaf.builder()
																			.addLine("<red>Distance: <aqua><distance>m")
																			.addStyle(Placeholder.unparsed("distance",
																					String.valueOf((int) distance)))
																			.addStyle(NekotineStyles.STANDART))
																	.buildFirst())));
						});
						item.setData(DataComponentTypes.LODESTONE_TRACKER,
								LodestoneTracker.lodestoneTracker(tool.tracked.getLocation(), false));
					});
				}
			}
		}
	}

	@EventHandler
	private void onPlayerInterract(PlayerInteractEvent evt) {
		if (evt.getHand() != EquipmentSlot.HAND && !EventUtil.isCustomAction(evt, CustomAction.HIT_ANY)) {
			return;
		}

		var statusFlagModule = Ioc.resolve(StatusFlagModule.class);
		var player = evt.getPlayer();
		var tool = getToolFromItem(evt.getItem());
		if (tool == null || tool.tracked != null || statusFlagModule.hasAny(player, EmpStatusFlag.get())) {
			return;
		}

		// SHOOT
		var ownWrap = Ioc.resolve(WrappingModule.class).getWrapper(player, PlayerWrapper.class);
		var eyeLoc = player.getEyeLocation();
		RayTraceResult res = player.getWorld().rayTrace(eyeLoc, eyeLoc.getDirection(), TrackerHandler.RAY_DISTANCE,
				FluidCollisionMode.NEVER, true, TrackerHandler.RAY_SIZE,
				hit -> ownWrap.ennemiTeamInMap().anyMatch(ennemi -> ennemi.equals(hit)));

		var range = TrackerHandler.RAY_DISTANCE;
		if (res != null) {
			var hitP = res.getHitPosition();
			range = eyeLoc.distance(new Location(eyeLoc.getWorld(), hitP.getX(), hitP.getY(), hitP.getZ()));
		}
		SpatialUtil.line3DFromDir(eyeLoc.getX(), eyeLoc.getY(), eyeLoc.getZ(), eyeLoc.getDirection(), range, 4,
				(vec) -> player.spawnParticle(Particle.FIREWORK, vec.getX(), vec.getY(), vec.getZ(), 0, 0, 0, 0, 0f));

		if (res == null || res.getHitEntity() == null) {
			remove(tool);
			Vi6Sound.TRACKER_FAIL.play(player);
			return;
		}

		if (res.getHitEntity() instanceof Player hit) { // Always the case
			tool.tracked = hit;
		}
		Vi6Sound.TRACKER_SUCCESS.play(player);
		// kb & animation
		tool.tracked.playHurtAnimation(player.getEyeLocation().getYaw() + tool.tracked.getLocation().getYaw());
		editItem(tool, item -> {
			item.setData(DataComponentTypes.ITEM_MODEL, Material.COMPASS.key());
		});
		evt.setCancelled(true);
	}

	@Override
	protected void onAttachedToPlayer(Tracker tool) {
		if (tool.tracked == null) {
			editItem(tool, item -> item.editMeta(meta -> meta.displayName(getDisplayName()
					.append(Component.text(" - ").append(Component.text("Armé", NamedTextColor.AQUA))))));
		} else {
			editItem(tool, item -> {
				item.setData(DataComponentTypes.ITEM_MODEL, Material.COMPASS.key());
			});
		}
	}

	@Override
	protected void onDetachFromPlayer(Tracker tool) {
	}

	@Override
	protected void onToolCleanup(Tracker tool) {
	}

	@EventHandler
	private void onEmpStart(EntityEmpStartEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				item.setData(DataComponentTypes.ITEM_MODEL, Material.RECOVERY_COMPASS.key());
				item.editMeta(m -> m.displayName(getDisplayName().decorate(TextDecoration.STRIKETHROUGH)
						.append(Component.text(" - ")).append(Component.text("Brouillé", NamedTextColor.RED))));
				item.unsetData(DataComponentTypes.LODESTONE_TRACKER);
			});
		}
	}

	@EventHandler
	private void onEmpStop(EntityEmpEndEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				var tool = getToolFromItem(item);
				if (tool.tracked == null) {
					item.resetData(DataComponentTypes.ITEM_MODEL); // back to default model
					item.editMeta(m -> m.displayName(getDisplayName()
							.append(Component.text(" - ").append(Component.text("Armé", NamedTextColor.AQUA)))));
				} else {
					item.setData(DataComponentTypes.ITEM_MODEL, Material.COMPASS.key());
				}
			});
		}
	}

	public static class Tracker extends Tool {

		private Player tracked;

		public Tracker(ToolHandler<?> handler) {
			super(handler);
		}
	}
}
