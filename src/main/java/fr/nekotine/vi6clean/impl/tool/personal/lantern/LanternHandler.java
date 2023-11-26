package fr.nekotine.vi6clean.impl.tool.personal.lantern;

import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.ticking.TickTimeStamp;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.util.SpatialUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6ToolLoreText;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.tool.ToolType;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public class LanternHandler extends ToolHandler<Lantern>{

	public static final int MAX_LANTERN = 2;
	
	public static final double SQUARED_PICKUP_BLOCK_RANGE = 2.25;
	
	public static final List<Component> LORE = Vi6ToolLoreText.LANTERN.make(
			Placeholder.unparsed("maxlantern", Integer.toString(MAX_LANTERN))
			);
	
	public LanternHandler() {
		super(ToolType.LANTERN, Lantern::new);
	}
	
	@Override
	protected void onAttachedToPlayer(Lantern tool, Player player) {
	}

	@Override
	protected void onDetachFromPlayer(Lantern tool, Player player) {
		tool.cleanup();
	}
	
	@EventHandler
	private void onPlayerInteract(PlayerInteractEvent evt) {
		if (evt.getHand() != EquipmentSlot.HAND) {
			return;
		}
		var evtP = evt.getPlayer();
		var optWrap = Ioc.resolve(WrappingModule.class).getWrapperOptional(evtP, PlayerWrapper.class);
		if (optWrap.isEmpty()) {
			return;
		}
		
		// Pickup check
		var allies = optWrap.get().ourTeam();
		getTools().stream().filter(t -> allies.contains(t.getOwner())).forEach(tool -> tool.allyTryPickup(evtP));
		
		// Drop check
		var optionalTool = getTools().stream().filter(t -> evtP.equals(t.getOwner()) && t.getItemStack().isSimilar(evt.getItem())).findFirst();
		if (optionalTool.isEmpty()) {
			return;
		}
		if (EventUtil.isCustomAction(evt, CustomAction.HIT_ANY) && optionalTool.get().tryPlace()) {
			evt.setCancelled(true);
		}
	}
	
	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		for (var tool : getTools()) {
			tool.tryRemoveFallingArmorStand();
			if (evt.timeStampReached(TickTimeStamp.HalfSecond)) {
				SpatialUtil.circle2DDensity(1.5, 3, Math.random() * 6, tool::lanternSmokes);
			}
		}
	}
	
}
