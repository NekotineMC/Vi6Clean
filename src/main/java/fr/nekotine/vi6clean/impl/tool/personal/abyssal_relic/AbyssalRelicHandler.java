package fr.nekotine.vi6clean.impl.tool.personal.abyssal_relic;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.status.effect.StatusEffect;
import fr.nekotine.core.status.effect.StatusEffectModule;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.impl.status.effect.SuffocatingStatusEffectType;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;

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
		for (var tool : getTools()) {
			var opt = wrappingModule.getWrapperOptional(tool.getOwner(), PlayerWrapper.class);
			if (opt.isEmpty()) {
				continue;
			}
			var wrapper = opt.get();
			if (!wrapper.enemyTeamInMap().anyMatch(p -> p.equals(evt.getPlayer()))) {
				continue;
			}
			var inside = tool.getOwner().getLocation().distanceSquared(evt.getTo()) <= RANGE * RANGE;
			if (tool.enemiesInRange.contains(evt.getPlayer())) {
				if (!inside) {
					leaveRange(evt.getPlayer(), tool);
				}
			} else {
				if (inside) {
					EFFECT_MODULE.addEffect(evt.getPlayer(), EFFECT);
					tool.enemiesInRange.add(evt.getPlayer());
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
			leaveRange(player, tool);
		}
	}

	@Override
	protected void onToolCleanup(AbyssalRelic tool) {
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
