package fr.nekotine.vi6clean.tool.personal.abyssal_relic;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.status.effect.StatusEffect;
import fr.nekotine.core.status.effect.StatusEffectModule;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.util.InventoryUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.status.effect.SuffocatingStatusEffectType;
import fr.nekotine.vi6clean.status.event.EntityEmpEndEvent;
import fr.nekotine.vi6clean.status.event.EntityEmpStartEvent;
import fr.nekotine.vi6clean.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.tool.Tool;
import fr.nekotine.vi6clean.tool.ToolCode;
import fr.nekotine.vi6clean.tool.ToolHandler;
import fr.nekotine.vi6clean.wrapper.PlayerWrapper;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

@ToolCode("abyssal_relic")
public class AbyssalRelicHandler extends ToolHandler<AbyssalRelicHandler.AbyssalRelic> {

	private final double RANGE = getConfiguration().getDouble("range", 5.0);
	private final StatusEffectModule EFFECT_MODULE;
	private final StatusEffect EFFECT = new StatusEffect(SuffocatingStatusEffectType.get(), -1);

	public AbyssalRelicHandler() {
		super(AbyssalRelic::new);
		Ioc.resolve(ModuleManager.class).tryLoad(StatusEffectModule.class);
		EFFECT_MODULE = Ioc.resolve(StatusEffectModule.class);
	}

	private void leaveRange(Player player, AbyssalRelic tool) {
		EFFECT_MODULE.removeEffect(player, EFFECT);
		tool.enemiesInRange.remove(player);
	}

	@EventHandler
	private void onPlayerMove(PlayerMoveEvent evt) {
		var wrappingModule = Ioc.resolve(WrappingModule.class);
		var statusFlagModule = Ioc.resolve(StatusFlagModule.class);
		var movingPlayer = evt.getPlayer();
		for (var tool : getTools()) {
			var owner = tool.getOwner();
			if (owner == null || statusFlagModule.hasAny(owner, EmpStatusFlag.get())) {
				continue;
			}
			var opt = wrappingModule.getWrapperOptional(owner, PlayerWrapper.class);
			if (opt.isEmpty()) {
				continue;
			}
			var wrapper = opt.get();

			if (movingPlayer.equals(owner)) {
				var ownerLoc = evt.getTo();
				wrapper.enemyTeamInMap().forEach(enemy -> {
					var inside = ownerLoc.distanceSquared(enemy.getLocation()) <= RANGE * RANGE;
					if (tool.enemiesInRange.contains(enemy)) {
						if (!inside) {
							leaveRange(enemy, tool);
						}
					} else {
						if (inside) {
							EFFECT_MODULE.addEffect(enemy, EFFECT);
							tool.enemiesInRange.add(enemy);
						}
					}
				});
			} else if (wrapper.enemyTeamInMap().anyMatch(p -> p.equals(movingPlayer))) {
				var inside = owner.getLocation().distanceSquared(evt.getTo()) <= RANGE * RANGE;
				if (tool.enemiesInRange.contains(movingPlayer)) {
					if (!inside) {
						leaveRange(movingPlayer, tool);
					}
				} else {
					if (inside) {
						EFFECT_MODULE.addEffect(movingPlayer, EFFECT);
						tool.enemiesInRange.add(movingPlayer);
					}
				}
			}
		}
	}

	@Override
	protected void onAttachedToPlayer(AbyssalRelic tool) {
	}

	@Override
	protected void onDetachFromPlayer(AbyssalRelic tool) {
		var it = tool.enemiesInRange.iterator();
		while (it.hasNext()) {
			var player = it.next();
			EFFECT_MODULE.removeEffect(player, EFFECT);
		}
		tool.enemiesInRange.clear();
	}

	@Override
	protected void onToolCleanup(AbyssalRelic tool) {
	}

	@EventHandler
	private void onEmpStart(EntityEmpStartEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				item.setData(DataComponentTypes.ITEM_MODEL, Material.ALLAY_SPAWN_EGG.key());
				item.editMeta(m -> m.displayName(getDisplayName().decorate(TextDecoration.STRIKETHROUGH)
						.append(Component.text(" - ")).append(Component.text("Brouillé", NamedTextColor.RED))));
			});
		}
		for (var tool : getTools()) {
			if (evt.getEntity().equals(tool.getOwner())) {
				var it = tool.enemiesInRange.iterator();
				while (it.hasNext()) {
					var player = it.next();
					EFFECT_MODULE.removeEffect(player, EFFECT);
				}
				tool.enemiesInRange.clear();
			}
		}
	}

	@EventHandler
	private void onEmpEnd(EntityEmpEndEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				item.resetData(DataComponentTypes.ITEM_MODEL);
				item.editMeta(m -> m.displayName(getDisplayName()));
			});
		}
		var statusFlagModule = Ioc.resolve(StatusFlagModule.class);
		var wrappingModule = Ioc.resolve(WrappingModule.class);
		for (var tool : getTools()) {
			var owner = tool.getOwner();
			if (evt.getEntity().equals(owner)) {
				if (statusFlagModule.hasAny(owner, EmpStatusFlag.get())) {
					continue;
				}
				var opt = wrappingModule.getWrapperOptional(owner, PlayerWrapper.class);
				if (opt.isEmpty()) {
					continue;
				}
				var wrapper = opt.get();
				var ownerLoc = owner.getLocation();
				wrapper.enemyTeamInMap().forEach(enemy -> {
					var inside = ownerLoc.distanceSquared(enemy.getLocation()) <= RANGE * RANGE;
					if (inside) {
						EFFECT_MODULE.addEffect(enemy, EFFECT);
						tool.enemiesInRange.add(enemy);
					}
				});
			}
		}
	}

	@EventHandler
	private void onPlayerInteract(PlayerInteractEvent evt) {
		var tool = getToolFromItem(evt.getItem());
		if (tool != null && EventUtil.isCustomAction(evt, CustomAction.INTERACT_ANY)) {
			evt.setCancelled(true);
		}
	}

	public static class AbyssalRelic extends Tool {
		private final Set<Player> enemiesInRange = new HashSet<>();
		public AbyssalRelic(ToolHandler<?> handler) {
			super(handler);
		}
	}
}
