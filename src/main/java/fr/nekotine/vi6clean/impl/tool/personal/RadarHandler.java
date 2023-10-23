package fr.nekotine.vi6clean.impl.tool.personal;

import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;

import fr.nekotine.core.NekotineCore;
import fr.nekotine.core.ticking.TickTimeStamp;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.vi6clean.constant.Vi6ToolLoreText;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.tool.ToolType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public class RadarHandler extends ToolHandler<Radar>{
	private static final int DETECTION_BLOCK_RANGE = 25;
	public static final int DETECTION_RANGE_SQUARED = DETECTION_BLOCK_RANGE * DETECTION_BLOCK_RANGE;
	private static final int DELAY_SECOND = 5;
	private int counter = 0;
	
	//
	
	public static final List<Component> LORE = Vi6ToolLoreText.RADAR.make(
			Placeholder.unparsed("range", DETECTION_BLOCK_RANGE+" block"),
			Placeholder.parsed("delay", DELAY_SECOND+" secondes")
	);
	
	//
	
	public RadarHandler() {
		super(ToolType.RADAR, Radar::new);
		NekotineCore.MODULES.tryLoad(TickingModule.class);
	}
	
	//
	
	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		if (evt.timeStampReached(TickTimeStamp.Second)){
			boolean cd_ended = false;
			if (++counter >= DELAY_SECOND) {
				counter = 0;
				cd_ended = true;
			}
			
			for(var tool : getTools()) {
				tool.tickParticle();
				if(cd_ended)
					tool.detect();
			}
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
			counter = 0;
			evt.setCancelled(true);
		}
	}

	//
	
	@Override
	protected void onAttachedToPlayer(Radar tool, Player player) {
	}
	@Override
	protected void onDetachFromPlayer(Radar tool, Player player) {
	}
}
