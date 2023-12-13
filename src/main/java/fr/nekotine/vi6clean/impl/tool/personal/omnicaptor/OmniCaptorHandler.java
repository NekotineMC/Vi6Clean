package fr.nekotine.vi6clean.impl.tool.personal.omnicaptor;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.ticking.TickTimeStamp;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.constant.Vi6ToolLoreText;
import fr.nekotine.vi6clean.impl.status.flag.OmniCaptedStatusFlag;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.tool.ToolType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public class OmniCaptorHandler extends ToolHandler<OmniCaptor>{

	public OmniCaptorHandler() {
		super(ToolType.OMNICAPTOR, OmniCaptor::new);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
	}
	
	public static final int DETECTION_BLOCK_RANGE = 3;
	
	public static final int DETECTION_RANGE_SQUARED = DETECTION_BLOCK_RANGE * DETECTION_BLOCK_RANGE;
	
	public static final List<Component> LORE = Vi6ToolLoreText.OMNICAPTOR.make(
			Placeholder.unparsed("range", DETECTION_BLOCK_RANGE+" blocs"),
			Placeholder.parsed("statusname", OmniCaptedStatusFlag.getStatusName())
			);
	
	@Override
	protected void onAttachedToPlayer(OmniCaptor tool, Player player) {
		tool.setSneaking(false);
	}

	@Override
	protected void onDetachFromPlayer(OmniCaptor tool, Player player) {
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
		if (EventUtil.isCustomAction(evt, CustomAction.INTERACT_ANY) && optionalTool.get().tryPlace()) {
			evt.setCancelled(true);
		}
		if (EventUtil.isCustomAction(evt, CustomAction.HIT_ANY) && optionalTool.get().tryPickup()) {
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
	
	private Collection<Player> inRange(ArmorStand as, OmniCaptor captor) {
		return captor.getEnemyTeam()
		.filter(ennemi -> ennemi.getLocation().distanceSquared(as.getLocation()) <= DETECTION_RANGE_SQUARED)
		.collect(Collectors.toCollection(LinkedList::new));
	}
	
	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		for (var tool : getTools()) {
			var as = tool.getPlaced();
			if (as == null) {
				if (evt.timeStampReached(TickTimeStamp.QuartSecond)){
					tool.lowTick();
				}
				continue;
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
					tool.removeEffect(p);
					ite.remove();
				}
			}
			for (var p : inRange) {
				tool.applyEffect(p);
				oldInRange.add(p);
				Vi6Sound.OMNICAPTEUR_DETECT.play(p);
				var own = tool.getOwner();
				if (own != null) {
					Vi6Sound.OMNICAPTEUR_DETECT.play(own);
				}
			}
			tool.itemUpdate();
		}
	}
	
}
