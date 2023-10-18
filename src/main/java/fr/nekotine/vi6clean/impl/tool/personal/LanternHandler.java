package fr.nekotine.vi6clean.impl.tool.personal;

import java.util.List;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;

import fr.nekotine.core.ticking.TickTimeStamp;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.vi6clean.constant.Vi6ToolLoreText;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.tool.ToolType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public class LanternHandler extends ToolHandler<Lantern>{

	public static final int MAX_LANTERN = 30;
	
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
		var gm = player.getGameMode();
		if (gm == GameMode.ADVENTURE || gm == GameMode.SURVIVAL) {
			player.setAllowFlight(false);
		}
	}
	
	@EventHandler
	private void onPlayerMove(PlayerMoveEvent evt) {
		
	}
	
	@EventHandler
	private void onPlayerInteract(PlayerInteractEvent evt) {
		if (evt.getHand() != EquipmentSlot.HAND) {
			return;
		}
		var evtP = evt.getPlayer();
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
		// kill falling armor stand
		for (var tool : getTools()) {
			tool.tryRemoveFallingArmorStand();
			if (evt.timeStampReached(TickTimeStamp.QuartSecond)) {
				tool.lanternSmokes();
			}
		}
	}
	
}
