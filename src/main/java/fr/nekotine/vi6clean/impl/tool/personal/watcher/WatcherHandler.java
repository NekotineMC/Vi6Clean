package fr.nekotine.vi6clean.impl.tool.personal.watcher;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.status.effect.StatusEffect;
import fr.nekotine.core.status.effect.StatusEffectModule;
import fr.nekotine.core.ticking.TickTimeStamp;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.constant.Vi6ToolLoreText;
import fr.nekotine.vi6clean.impl.status.effect.OmniCaptedStatusEffectType;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.tool.ToolType;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public class WatcherHandler extends ToolHandler<Watcher>{

	public WatcherHandler() {
		super(ToolType.WATCHER, Watcher::new);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
	}
	
	private static final StatusEffect glowEffect = new StatusEffect(OmniCaptedStatusEffectType.get(), 0);
	
	public static final int DETECTION_BLOCK_RANGE = 3;
	
	public static final int NB_MAX_WATCHER = 3;
	
	public static final int DETECTION_RANGE_SQUARED = DETECTION_BLOCK_RANGE * DETECTION_BLOCK_RANGE;
	
	public static final List<Component> LORE = Vi6ToolLoreText.WATCHER.make(
			Placeholder.unparsed("range", DETECTION_BLOCK_RANGE+" blocs"),
			Placeholder.parsed("nbmax", Integer.toString(NB_MAX_WATCHER))
			);
	
	@Override
	protected void onAttachedToPlayer(Watcher tool, Player player) {
		tool.setSneaking(player.isSneaking());
	}

	@Override
	protected void onDetachFromPlayer(Watcher tool, Player player) {
		tool.cleanup();
	}
	
	@EventHandler
	private void onPlayerInterract(PlayerInteractEvent evt) {
		if (evt.getHand() != EquipmentSlot.HAND) {
			return;
		}
		var evtP = evt.getPlayer();
		var optionalTool = getTools().stream().filter(t -> evtP.equals(t.getOwner()) && t.getItemStack().isSimilar(evt.getItem())).findFirst();
		if (optionalTool.isEmpty()) {
			return;
		}
		System.out.println("ACTION = "+evt.getAction());
		if (EventUtil.isCustomAction(evt, CustomAction.INTERACT_ANY) && optionalTool.get().tryDropWatcher()) {
			evt.setCancelled(true);
		}
		if (EventUtil.isCustomAction(evt, CustomAction.HIT_ANY) && optionalTool.get().tryPickupWatcher()) {
			evt.setCancelled(true);
		}
	}
	
	@EventHandler
	private void onPlayerToggleSneak(PlayerToggleSneakEvent evt) {
		var tools = getTools().stream().filter(t -> evt.getPlayer().equals(t.getOwner())).collect(Collectors.toUnmodifiableSet());
		for (var tool : tools) {
			tool.setSneaking(evt.isSneaking());
		}
	}
	
	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		var statusEffectModule = Ioc.resolve(StatusEffectModule.class);
		for (var tool : getTools()) {
			if (tool.getOwner() == null) {
				continue;
			}
			Supplier<Stream<Player>> enemiTeam =
					Ioc.resolve(WrappingModule.class).getWrapper(tool.getOwner(), PlayerWrapper.class)::ennemiTeamInMap;
			var watchers = tool.getWatcherList();
			var toRemove = watchers.stream().filter(sf ->enemiTeam.get().anyMatch(p -> p.getLocation().distanceSquared(sf.getLocation()) <= 1))
			.collect(Collectors.toCollection(LinkedList::new));
			for (var sf : toRemove) {
				sf.remove();
				watchers.remove(sf);
				tool.itemUpdate();
			}
			
			Collection<Player> inRange = enemiTeam.get().filter(
					ennemi -> watchers.stream().anyMatch(
							sf -> ennemi.getLocation().distanceSquared(sf.getLocation())<= DETECTION_RANGE_SQUARED)
					)
			.collect(Collectors.toCollection(LinkedList::new));
			var oldInRange = tool.getEnnemiesInRange();
			if (inRange.size() <= 0 && oldInRange.size() <= 0) {
				continue;
			}
			var ite = oldInRange.iterator();
			while (ite.hasNext()) {
				var p = ite.next();
				if (inRange.contains(p)) {
					inRange.remove(p);
				}else {
					statusEffectModule.removeEffect(p, glowEffect);
					ite.remove();
				}
			}
			for (var p : inRange) {
				statusEffectModule.addEffect(p, glowEffect);
				oldInRange.add(p);
				Vi6Sound.OMNICAPTEUR_DETECT.play(p);
				var own = tool.getOwner();
				if (own != null) {
					Vi6Sound.OMNICAPTEUR_DETECT.play(own);
				}
			}
		}
		if (evt.timeStampReached(TickTimeStamp.QuartSecond)){
			for (var tool : getTools()) {
				tool.lowTick();
			}
		}
	}
	
}
