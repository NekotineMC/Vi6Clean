package fr.nekotine.vi6clean.impl.tool.personal.smokepool;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.status.effect.StatusEffect;
import fr.nekotine.core.status.effect.StatusEffectModule;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.ticking.TickTimeStamp;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.util.SpatialUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.impl.status.effect.invisibility.TrueInvisibilityStatusEffectType;
import fr.nekotine.vi6clean.impl.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import io.papermc.paper.datacomponent.DataComponentTypes;

@ToolCode("smokepool")
public class SmokePoolHandler extends ToolHandler<SmokePoolHandler.SmokePool> {
	protected static final Random RNG = new Random();

	private final double RADIUS = getConfiguration().getDouble("radius", 5);

	private final double SQUARED_RADIUS = RADIUS * RADIUS;

	private final double PARTICLE_DENSITY = getConfiguration().getDouble("particle_density", 78.5398);

	private final int DURATION_TICK = (int) (20 * getConfiguration().getDouble("duration", 8));

	private final int COOLDOWN_TICK = (int) (20 * getConfiguration().getDouble("cooldown", 20));

	private final StatusEffect INVISIBLE = new StatusEffect(TrueInvisibilityStatusEffectType.get(), DURATION_TICK);

	public SmokePoolHandler() {
		super(SmokePool::new);
	}

	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		for (var tool : getTools()) {
			var owner = tool.getOwner();
			if (tool.position != null && owner.getCooldown(Material.FIREWORK_STAR) <= COOLDOWN_TICK) {
				tool.position = null; // La pool a expirée et n'est plus active
				editItem(tool, item -> item.resetData(DataComponentTypes.ITEM_MODEL));
			}
			// PARTICLE
			if (tool.position != null) {
				var l = tool.position.toLocation(owner.getWorld());
				SpatialUtil.disk2DDensity(RADIUS, PARTICLE_DENSITY, (x, y) -> {
					l.set(tool.position.getX() + x, tool.position.getY() - 1, tool.position.getZ() + y);
					double maxy = l.getY() + 2.5;
					while (l.getY() < maxy) {
						if (l.getBlock().getBoundingBox().contains(l.toVector())) {
							l.add(0, 0.1, 0);
						} else {
							l.getWorld().spawnParticle(Particle.SMOKE, l, 1, 0, 0, 0, 0);
							break;
						} ;
					}
				});
			}

			if (evt.timeStampReached(TickTimeStamp.QuartSecond)) {
				// Copied from OmniCaptorHandler
				if (tool.position == null) {
					continue;
				}

				//
				var team = Ioc.resolve(WrappingModule.class).getWrapper(owner, PlayerWrapper.class).ourTeam();
				var inRange = team
						.stream().filter(enemy -> enemy.getLocation().toVector()
								.distanceSquared(tool.position) <= SQUARED_RADIUS * tool.scale)
						.collect(Collectors.toCollection(LinkedList::new));

				var oldInRange = tool.inside;
				if (inRange.size() <= 0 && oldInRange.size() <= 0) {
					continue;
				}

				var ite = oldInRange.iterator();
				var statusModule = Ioc.resolve(StatusEffectModule.class);
				while (ite.hasNext()) {
					var p = ite.next();
					if (inRange.contains(p)) {
						inRange.remove(p);
					} else {
						statusModule.removeEffect(p, INVISIBLE);
						ite.remove();
					}
				}
				for (var p : inRange) {
					statusModule.addEffect(p, INVISIBLE);
					oldInRange.add(p);
				}
			}
		}
	}

	@EventHandler
	private void onPlayerInterract(PlayerInteractEvent evt) {
		if (evt.getHand() != EquipmentSlot.HAND && !EventUtil.isCustomAction(evt, CustomAction.HIT_ANY)) {
			return;
		}
		var statusModule = Ioc.resolve(StatusFlagModule.class);
		var player = evt.getPlayer();
		var item = evt.getItem();
		var tool = getToolFromItem(item);
		if (tool == null || statusModule.hasAny(player, EmpStatusFlag.get()) || player.getCooldown(item) <= 0) {
			return;
		}

		tool.scale = player.getAttribute(Attribute.SCALE).getValue();
		player.setCooldown(item, COOLDOWN_TICK + DURATION_TICK);
		var ploc = player.getLocation();
		tool.position = ploc.toVector();
		Vi6Sound.SMOKEPOOL.play(ploc.getWorld(), ploc);
		item.setData(DataComponentTypes.ITEM_MODEL, Material.GRAY_DYE.key());
		evt.setCancelled(true);
	}

	@Override
	protected void onAttachedToPlayer(SmokePool tool) {
	}

	@Override
	protected void onDetachFromPlayer(SmokePool tool) {
	}

	@Override
	protected void onToolCleanup(SmokePool tool) {
	}

	public static class SmokePool extends Tool {

		private final List<Player> inside = new LinkedList<Player>();

		private Vector position;

		private double scale;

		public SmokePool(ToolHandler<?> handler) {
			super(handler);
		}
	}
}
