package fr.nekotine.vi6clean.impl.tool.personal.shadow;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;

import fr.nekotine.core.NekotineCore;
import fr.nekotine.core.ticking.TickTimeStamp;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.tool.ToolType;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;

public class ShadowHandler extends ToolHandler<Shadow>{

	public static final double SHADOW_KILL_RANGE_BLOCK = 1;
	
	public ShadowHandler() {
		super(ToolType.OMNICAPTOR, Shadow::new);
		NekotineCore.MODULES.tryLoad(TickingModule.class);
	}
	
	@Override
	protected void onAttachedToPlayer(Shadow tool, Player player) {
	}

	@Override
	protected void onDetachFromPlayer(Shadow tool, Player player) {
		tool.tryUse();
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
		var tool = optionalTool.get();
		if (EventUtil.isCustomAction(evt, CustomAction.HIT_ANY)) {
			if (tool.tryUse()) {
				evt.setCancelled(true);
				remove(tool);
			}else if (tool.tryPlace()) {
				evt.setCancelled(true);
			}
		}
	}
	
	@EventHandler
	private void onPlayerMove(PlayerMoveEvent evt) {
		var player = evt.getPlayer();
		var ite = getTools().iterator();
		var wrappingModule = NekotineCore.MODULES.get(WrappingModule.class);
		while (ite.hasNext()) {
			var tool = ite.next();
			var wrap = wrappingModule.getWrapperOptional(tool.getOwner(), PlayerWrapper.class);
			if (tool.getPlaced() == null || !wrap.isPresent() || !wrap.get().ennemiTeamInMap().anyMatch(p -> p.equals(evt.getPlayer()))) {
				continue;
			}
			if (tool.getPlaced().getLocation().distanceSquared(player.getLocation()) <= 100) {
				tool.getOwner().damage(1000, evt.getPlayer());
				Vi6Sound.SHADOW_KILL.play(player.getWorld());
				remove(tool);
			}
		}
	}
	
	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		if (evt.timeStampReached(TickTimeStamp.QuartSecond)){
			for (var tool : getTools()) {
				tool.lowTick();
			}
		}
	}
	
}
