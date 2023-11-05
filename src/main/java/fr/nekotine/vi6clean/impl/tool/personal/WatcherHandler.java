package fr.nekotine.vi6clean.impl.tool.personal;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.entity.Silverfish;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.NekotineCore;
import fr.nekotine.core.status.effect.StatusEffect;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.ticking.TickTimeStamp;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.constant.Vi6ToolLoreText;
import fr.nekotine.vi6clean.impl.status.effect.OmniCaptedStatusEffectType;
import fr.nekotine.vi6clean.impl.status.flag.InvisibleStatusFlag;
import fr.nekotine.vi6clean.impl.status.flag.OmniCaptedStatusFlag;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.tool.ToolType;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public class WatcherHandler extends ToolHandler<Watcher>{

	public WatcherHandler() {
		super(ToolType.WATCHER, Watcher::new);
		NekotineCore.MODULES.tryLoad(TickingModule.class);
	}
	
	private static final StatusEffect glowEffect = new StatusEffect(OmniCaptedStatusEffectType.get(), 0);
	
	public static final int DETECTION_BLOCK_RANGE = 3;
	
	public static final int NB_MAX_WATCHER = 3;
	
	private static final int DETECTION_RANGE_SQUARED = DETECTION_BLOCK_RANGE * DETECTION_BLOCK_RANGE;
	
	public static final List<Component> LORE = Vi6ToolLoreText.WATCHER.make(
			Placeholder.unparsed("range", DETECTION_BLOCK_RANGE+" block"),
			Placeholder.parsed("nbMax", Integer.toString(NB_MAX_WATCHER))
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
	private void onPlayerToggleSneak(PlayerToggleSneakEvent evt) {
		var tools = getTools().stream().filter(t -> evt.getPlayer().equals(t.getOwner())).collect(Collectors.toUnmodifiableSet());
		for (var tool : tools) {
			tool.setSneaking(evt.isSneaking());
		}
	}
	
	private Collection<Player> inRange(Silverfish sf, OmniCaptor captor) {
		return captor.getEnemyTeam()
		.filter(ennemi -> ennemi.getLocation().distanceSquared(sf.getLocation()) <= DETECTION_RANGE_SQUARED)
		.collect(Collectors.toCollection(LinkedList::new));
	}
	
	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		var flagModule = NekotineCore.MODULES.get(StatusFlagModule.class);
		for (var tool : getTools()) {
			for (var sf : tool.getWatcherList()) {
				
			}
			var inRange = inRange(as, tool);
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
					flagModule.removeFlag(p, OmniCaptedStatusFlag.get());
					ite.remove();
				}
			}
			for (var p : inRange) {
				flagModule.addFlag(p, OmniCaptedStatusFlag.get());
				oldInRange.add(p);
				Vi6Sound.OMNICAPTEUR_DETECT.play(p);
				var own = tool.getOwner();
				if (own != null) {
					Vi6Sound.OMNICAPTEUR_DETECT.play(own);
				}
			}
			tool.itemUpdate();
		}
		if (evt.timeStampReached(TickTimeStamp.QuartSecond)){
			for (var tool : getTools()) {
				tool.lowTick();
			}
		}
	}
	
}
