package fr.nekotine.vi6clean.impl.tool.personal.parabolic_mic;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Vibration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.player.PlayerMoveEvent;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.util.InventoryUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpEndEvent;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpStartEvent;
import fr.nekotine.vi6clean.impl.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

@ToolCode("parabolic_mic")
public class ParabolicMicHandler extends ToolHandler<ParabolicMicHandler.ParabolicMic> {

	public ParabolicMicHandler() {
		super(ParabolicMic::new);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
	}

	private final double DETECTION_BLOCK_RANGE = getConfiguration().getDouble("range", 20d);

	private final double DETECTION_RANGE_SQUARED = DETECTION_BLOCK_RANGE * DETECTION_BLOCK_RANGE;

	@EventHandler
	private void onMove(PlayerMoveEvent evt) {
		if (!evt.hasChangedBlock()) {
			return;
		}
		var flagModule = Ioc.resolve(StatusFlagModule.class);
		for (var tool : getTools()) {
			var owner = tool.getOwner();
			var evtPlayer = evt.getPlayer();
			if (owner == null || tool.vibrationTargetEntity == null || evtPlayer.equals(tool.vibrationTargetEntity)) {
				continue;
			}
			var ownerloc = owner.getLocation();
			var destloc = evt.getTo();
			var enemyTeam = Ioc.resolve(WrappingModule.class).getWrapper(owner, PlayerWrapper.class).enemyTeamInMap();
			/*
			 * if (evtPlayer.equals(owner)) { vibrationTarget.teleport(owner); continue; }
			 */
			if (!ownerloc.getWorld().equals(destloc.getWorld()) || !enemyTeam.anyMatch(e -> e.equals(evtPlayer))
					|| evtPlayer.equals(owner)
					|| evt.getTo().distanceSquared(owner.getLocation()) > DETECTION_RANGE_SQUARED
					|| flagModule.hasAny(owner, EmpStatusFlag.get())) {
				continue;
			}
			var vibration = new Vibration(new Vibration.Destination.EntityDestination(tool.vibrationTargetEntity), 10);
			owner.spawnParticle(Particle.VIBRATION, evt.getTo(), 1, vibration);
		}
	}

	@Override
	protected void onAttachedToPlayer(ParabolicMic tool) {
		var player = tool.getOwner();
		var passenger = (ArmorStand) player.getWorld().spawnEntity(player.getLocation(), EntityType.ARMOR_STAND,
				SpawnReason.CUSTOM, e -> {
					if (e instanceof ArmorStand stand) {
						stand.setPersistent(false);
						stand.setInvisible(true);
						stand.setMarker(true);
						stand.setBasePlate(false);
					}
				});
		player.addPassenger(passenger);
		tool.vibrationTargetEntity = passenger;
	}

	@Override
	protected void onDetachFromPlayer(ParabolicMic tool) {
		var passenger = tool.vibrationTargetEntity;
		tool.vibrationTargetEntity = null;
		passenger.remove();
	}

	@Override
	protected void onToolCleanup(ParabolicMic tool) {
	}

	@EventHandler
	private void onEmpStart(EntityEmpStartEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				item.setData(DataComponentTypes.ITEM_MODEL, Material.SCULK_SENSOR.key());
				item.editMeta(m -> m.displayName(getDisplayName().decorate(TextDecoration.STRIKETHROUGH)
						.append(Component.text(" - ")).append(Component.text("Brouillé", NamedTextColor.RED))));
			});
		}
	}

	@EventHandler
	private void onEmpStop(EntityEmpEndEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				item.resetData(DataComponentTypes.ITEM_MODEL); // back to default model
				item.editMeta(m -> m.displayName(getDisplayName()));
			});
		}
	}

	public static class ParabolicMic extends Tool {

		public ParabolicMic(ToolHandler<?> handler) {
			super(handler);
		}

		private Entity vibrationTargetEntity;
	}
}
