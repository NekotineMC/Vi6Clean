package fr.nekotine.vi6clean.impl.tool.personal.sculk_sensor;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.block.data.type.SculkSensor.Phase;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockReceiveGameEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import com.destroystokyo.paper.MaterialSetTag;

import fr.nekotine.core.block.BlockPatch;
import fr.nekotine.core.block.tempblock.AppliedTempBlockPatch;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.ticking.TickTimeStamp;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EntityUtil;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.util.SpatialUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;

@ToolCode("sculk_sensor")
public class SculkSensorHandler extends ToolHandler<SculkSensorHandler.SculkSensor> {

	private static final double SCULK_SENSOR_RANGE = 8; // https://minecraft.fandom.com/wiki/Sculk_Sensor#Vibration_detection

	private final BlockPatch sculkBlockPatch = new BlockPatch(state -> state.setType(Material.SCULK_SENSOR));

	private static final Tag<Material> REPLACABLES = new MaterialSetTag(
			NamespacedKey.fromString("sculk_sensor_replacable", Ioc.resolve(JavaPlugin.class)), mat -> {
				return mat == Material.AIR || Tag.STANDING_SIGNS.isTagged(mat) || Tag.WALL_SIGNS.isTagged(mat)
						|| Tag.TRAPDOORS.isTagged(mat) || Tag.WOOL_CARPETS.isTagged(mat);
			});

	public SculkSensorHandler() {
		super(SculkSensor::new);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
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
		var player = evt.getPlayer();
		var ploc = player.getLocation();
		// TRY PLACE
		if (EntityUtil.IsOnGround(player) && REPLACABLES.isTagged(ploc.getBlock().getType())) {
			tool.patch = sculkBlockPatch.patch(ploc.getBlock(), false);
			evt.setCancelled(true);
			detachFromOwner(tool);
		}
	}

	@EventHandler
	private void onSculkTriggered(BlockReceiveGameEvent evt) {
		if (evt.getBlock().getType() != Material.SCULK_SENSOR) {
			return;
		}
		if (evt.getEntity() instanceof Player player) {
			var wrapModule = Ioc.resolve(WrappingModule.class);
			var wrapper = wrapModule.getWrapperOptional(player, PlayerWrapper.class);
			if (wrapper.isEmpty() || wrapper.get().isGuard()) {
				evt.setCancelled(true);
				return;
			}
		}
		if (evt.getBlock().getBlockData() instanceof org.bukkit.block.data.type.SculkSensor sensor) {
			if (sensor.getSculkSensorPhase() == Phase.COOLDOWN || sensor.getSculkSensorPhase() == Phase.ACTIVE) {
				return; // Avoid crashing the server with infinite recursion
			}
		}

		new BukkitRunnable() { // Run next tick to avoid StackOverflow
			@Override
			public void run() {
				evt.getBlock().getWorld().sendGameEvent(evt.getEntity(), evt.getEvent(),
						evt.getBlock().getLocation().toVector());
			}
		}.runTaskLater(Ioc.resolve(JavaPlugin.class), 10); // Delay to avoid infinite recurtion
	}

	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		if (!evt.timeStampReached(TickTimeStamp.QuartSecond)) {
			return;
		}
		for (var tool : getTools()) {
			var owner = tool.getOwner();
			if (owner != null && owner.isSneaking()) {
				// Low tick
				var loc = owner.getLocation();
				var x = loc.getX();
				var y = loc.getY();
				var z = loc.getZ();
				SpatialUtil.circle2DDensity(SCULK_SENSOR_RANGE, 5, 0, (offsetX, offsetZ) -> {
					owner.spawnParticle(Particle.FIREWORK, x + offsetX, y, z + offsetZ, 1, 0, 0, 0, 0, null);
				});
			}
		}
	}

	@Override
	protected void onAttachedToPlayer(SculkSensor tool) {
	}

	@Override
	protected void onDetachFromPlayer(SculkSensor tool) {
	}

	@Override
	protected void onToolCleanup(SculkSensor tool) {
		if (tool.patch != null) {
			tool.patch.unpatch(false);
			tool.patch = null;
		}
	}

	public static class SculkSensor extends Tool {

		public SculkSensor(ToolHandler<?> handler) {
			super(handler);
		}

		private AppliedTempBlockPatch patch;

	}
}
