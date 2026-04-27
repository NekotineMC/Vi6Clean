package fr.nekotine.vi6clean.impl.tool.personal.watcher;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Silverfish;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.status.effect.StatusEffect;
import fr.nekotine.core.status.effect.StatusEffectModule;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.ticking.TickTimeStamp;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.util.InventoryUtil;
import fr.nekotine.core.util.MobAiUtil;
import fr.nekotine.core.util.SpatialUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.impl.status.effect.OmniCaptedStatusEffectType;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpEndEvent;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpStartEvent;
import fr.nekotine.vi6clean.impl.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.util.Tick;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

@ToolCode("watcher")
public class WatcherHandler extends ToolHandler<WatcherHandler.Watcher> {

	private final StatusEffect permanentGlowEffect = new StatusEffect(OmniCaptedStatusEffectType.get(), 0);

	private final StatusEffect lastingGlowEffect = new StatusEffect(OmniCaptedStatusEffectType.get(),
			Tick.tick().fromDuration(Duration.ofSeconds(getConfiguration().getLong("last_delay_second", 1))));

	private final double DETECTION_BLOCK_RANGE = getConfiguration().getDouble("range", 5);

	private final double DETECTION_RANGE_SQUARED = DETECTION_BLOCK_RANGE * DETECTION_BLOCK_RANGE;

	private final int NB_MAX_WATCHER = getConfiguration().getInt("nbmax", 3);

	public WatcherHandler() {
		super(Watcher::new);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
	}

	@EventHandler
	private void onPlayerInterract(PlayerInteractEvent evt) {
		if (evt.getHand() != EquipmentSlot.HAND || !EventUtil.isCustomAction(evt, CustomAction.HIT_ANY)) {
			return;
		}
		var statusFlagModule = Ioc.resolve(StatusFlagModule.class);
		var player = evt.getPlayer();
		var tool = getToolFromItem(evt.getItem());
		if (tool == null || statusFlagModule.hasAny(player, EmpStatusFlag.get())) {
			return;
		}

		// TRY PICKUP

		var ownerLoc = player.getLocation();
		for (var sf : tool.watchers.stream()
				.filter(sf -> sf.getLocation().distanceSquared(ownerLoc) <= DETECTION_RANGE_SQUARED)
				.collect(Collectors.toCollection(LinkedList::new))) {
			tool.watchers.remove(sf);
			sf.remove();
			editItem(tool, item -> {
				item.resetData(DataComponentTypes.ITEM_MODEL);
				item.setAmount(NB_MAX_WATCHER - tool.watchers.size());
			});
			evt.setCancelled(true);
			return;
		}

		// TRY DROP

		if (tool.watchers.size() >= NB_MAX_WATCHER) {
			return;
		}
		tool.watchers.add(ownerLoc.getWorld().spawnEntity(ownerLoc, EntityType.SILVERFISH, SpawnReason.CUSTOM, e -> {
			if (e instanceof Silverfish fish) {
				e.setInvulnerable(true);
				e.setSilent(true);
				e.setPersistent(false);
				MobAiUtil.clearBrain(fish); // Gravity is disabled if Mob AI is disabled
			}
		}));
		editItem(tool, item -> {
			var remaining = NB_MAX_WATCHER - tool.watchers.size();
			if (remaining > 0) {
				item.setAmount(remaining);
			} else {
				item.setData(DataComponentTypes.ITEM_MODEL, Material.ENDERMITE_SPAWN_EGG.key());
				item.setAmount(1);
			}
		});
		evt.setCancelled(true);
	}

	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		var statusEffectModule = Ioc.resolve(StatusEffectModule.class);
		var statusFlagModule = Ioc.resolve(StatusFlagModule.class);
		for (var tool : getTools()) {
			var owner = tool.getOwner();
			if (owner == null) {
				continue;
			}

			// PARTICLE

			if (evt.timeStampReached(TickTimeStamp.QuartSecond)) {
				if (tool.watchers.size() < NB_MAX_WATCHER) {
					if (!owner.isSneaking() || !itemMatch(tool, owner.getInventory().getItemInMainHand())) {
						return;
					}
					var loc = owner.getLocation();
					var x = loc.getX();
					var y = loc.getY();
					var z = loc.getZ();
					SpatialUtil.circle2DDensity(DETECTION_BLOCK_RANGE, 5, 0, (offsetX, offsetZ) -> {
						owner.spawnParticle(Particle.FIREWORK, x + offsetX, y, z + offsetZ, 1, 0, 0, 0, 0, null);
					});
				}
			}

			// GLOW EFFECT

			Supplier<Stream<Player>> enemyTeam = Ioc.resolve(WrappingModule.class).getWrapper(owner,
					PlayerWrapper.class)::enemyTeamInMap;

			var toRemove = tool.watchers.stream()
					.filter(sf -> enemyTeam.get().anyMatch(p -> p.getLocation().distanceSquared(sf.getLocation()) <= 1))
					.collect(Collectors.toCollection(LinkedList::new));
			for (var sf : toRemove) {
				sf.remove();
				tool.watchers.remove(sf);
				editItem(tool, item -> {
					item.resetData(DataComponentTypes.ITEM_MODEL);
					item.setAmount(NB_MAX_WATCHER - tool.watchers.size());
				});
			}

			Collection<Player> inRange = enemyTeam.get()
					.filter(enemy -> tool.watchers.stream().anyMatch(
							sf -> enemy.getLocation().distanceSquared(sf.getLocation()) <= DETECTION_RANGE_SQUARED))
					.collect(Collectors.toCollection(LinkedList::new));
			var oldInRange = tool.enemyesInRange;
			if (inRange.size() <= 0 && oldInRange.size() <= 0) {
				continue;
			}
			var ite = oldInRange.iterator();
			while (ite.hasNext()) {
				var p = ite.next();
				if (inRange.contains(p)) {
					inRange.remove(p);
				} else {
					statusEffectModule.addEffect(p, lastingGlowEffect);
					statusEffectModule.removeEffect(p, permanentGlowEffect);
					ite.remove();
				}
			}
			for (var p : inRange) {
				if (owner == null || !statusFlagModule.hasAny(owner, EmpStatusFlag.get())) {
					statusEffectModule.addEffect(p, permanentGlowEffect);
					Vi6Sound.OMNICAPTEUR_DETECT.play(p);
					if (owner != null) {
						Vi6Sound.OMNICAPTEUR_DETECT.play(owner);
					}
				}
				oldInRange.add(p);
			}
		}
	}

	@Override
	protected void onAttachedToPlayer(Watcher tool) {
		editItem(tool, item -> {
			var remaining = NB_MAX_WATCHER - tool.watchers.size();
			if (remaining > 0) {
				item.resetData(DataComponentTypes.ITEM_MODEL);
				item.setAmount(remaining);
			} else {
				item.setData(DataComponentTypes.ITEM_MODEL, Material.ENDERMITE_SPAWN_EGG.key());
				item.setAmount(1);
			}
		});
	}

	@Override
	protected void onDetachFromPlayer(Watcher tool) {
		onToolCleanup(tool);
	}

	@Override
	protected void onToolCleanup(Watcher tool) {
		for (var watcher : tool.watchers) {
			watcher.remove();
		}
		tool.watchers.clear();
	}

	@EventHandler
	private void onEmpStart(EntityEmpStartEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				var tool = getToolFromItem(item);
				var statusModule = Ioc.resolve(StatusEffectModule.class);
				for (var victim : tool.enemyesInRange) {
					statusModule.removeEffect(victim, permanentGlowEffect);
				}
				item.editMeta(m -> m.displayName(getDisplayName().decorate(TextDecoration.STRIKETHROUGH)
						.append(Component.text(" - ")).append(Component.text("Brouillé", NamedTextColor.RED))));
			});
		}
	}

	@EventHandler
	private void onEmpStop(EntityEmpEndEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				var tool = getToolFromItem(item);
				var statusModule = Ioc.resolve(StatusEffectModule.class);
				for (var victim : tool.enemyesInRange) {
					statusModule.addEffect(victim, permanentGlowEffect);
					Vi6Sound.OMNICAPTEUR_DETECT.play(victim);
				}
				if (tool.enemyesInRange.size() > 0) {
					Vi6Sound.OMNICAPTEUR_DETECT.play(p);
				}
				item.editMeta(m -> m.displayName(getDisplayName()));

				if (NB_MAX_WATCHER - tool.watchers.size() > 0) {
					item.resetData(DataComponentTypes.ITEM_MODEL); // back to default model
				} else {
					item.setData(DataComponentTypes.ITEM_MODEL, Material.ENDERMITE_SPAWN_EGG.key());
				}
			});
		}
	}

	public static class Watcher extends Tool {

		private List<Entity> watchers = new LinkedList<>();

		private Collection<Player> enemyesInRange = new LinkedList<>();

		public Watcher(ToolHandler<?> handler) {
			super(handler);
		}
	}
}
