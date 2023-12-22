package fr.nekotine.vi6clean.impl.tool.personal.emp;

import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.vi6clean.constant.Vi6ToolLoreText;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

@ToolCode("emp")
public class EmpHandler extends ToolHandler<Emp>{
	protected static final int EMP_DURATION_TICKS = 100;
	public static final List<Component> LORE = Vi6ToolLoreText.EMP.make(
			Placeholder.unparsed("duration", (EMP_DURATION_TICKS/20)+"s"));
	
	//
	
	public EmpHandler() {
		super(Emp::new);
	}
	@Override
	protected void onAttachedToPlayer(Emp tool, Player player) {
	}
	@Override
	protected void onDetachFromPlayer(Emp tool, Player player) {
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
		if (EventUtil.isCustomAction(evt, CustomAction.HIT_ANY) && optionalTool.get().trigger()) {
			remove(optionalTool.get());
			evt.setCancelled(true);
		}
	}
}
