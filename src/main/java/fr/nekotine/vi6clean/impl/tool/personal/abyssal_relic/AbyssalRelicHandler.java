package fr.nekotine.vi6clean.impl.tool.personal.abyssal_relic;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.ticking.TickTimeStamp;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.impl.status.flag.SuffocatingStatusFlag;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;

@ToolCode("abyssal_relic")
public class AbyssalRelicHandler extends ToolHandler<AbyssalRelicHandler.AbyssalRelic> {

	private final double range;

	public AbyssalRelicHandler() {
		super(AbyssalRelic::new);
		range = getConfiguration().getDouble("range", 15.0);
	}

	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		if (!evt.timeStampReached(TickTimeStamp.QuartSecond)) {
			return;
		}
		var statusFlagModule = Ioc.resolve(StatusFlagModule.class);
		var wrappingModule = Ioc.resolve(WrappingModule.class);

		Set<Player> newTargets = new HashSet<>();

		for (var tool : getTools()) {
			var owner = tool.getOwner();
			if (owner == null)
				continue;

			var opt = wrappingModule.getWrapperOptional(owner, PlayerWrapper.class);
			if (opt.isEmpty())
				continue;

			var enemies = opt.get().enemyTeamInMap().collect(Collectors.toSet());
			owner.getNearbyEntities(range, range, range).stream().filter(e -> e instanceof Player).map(e -> (Player) e)
					.filter(enemies::contains).forEach(newTargets::add);
		}

		// Add flag to new targets (idempotent)
		for (var target : newTargets) {
			statusFlagModule.addFlag(target, SuffocatingStatusFlag.get());
		}

		// Remove flag from players no longer in range
		var toRemove = SuffocatingStatusFlag.get().getSuffocatingPlayers().stream().filter(p -> !newTargets.contains(p))
				.collect(Collectors.toList());

		for (var p : toRemove) {
			statusFlagModule.removeFlag(p, SuffocatingStatusFlag.get());
		}
	}

	@Override
	protected void onAttachedToPlayer(AbyssalRelic tool) {
	}

	@Override
	protected void onDetachFromPlayer(AbyssalRelic tool) {
	}

	@Override
	protected void onToolCleanup(AbyssalRelic tool) {
	}

	@EventHandler
	private void onPlayerInteract(PlayerInteractEvent evt) {
	var tool = getToolFromItem(evt.getItem());
	if(tool != null && EventUtil.isCustomAction(evt, CustomAction.INTERACT_ANY)) {
	evt.setCancelled(true);
	}
	}

	public static class AbyssalRelic extends Tool {
		public AbyssalRelic(ToolHandler<?> handler) {
			super(handler);
		}
	}
}
