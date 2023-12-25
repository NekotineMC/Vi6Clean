package fr.nekotine.vi6clean.impl.tool.personal.emp;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;

@ToolCode("emp")
public class EmpHandler extends ToolHandler<Emp>{
	private final int EMP_DURATION_TICKS = (int)(20 * getConfiguration().getDouble("duration",5));

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
	
	public int getEmpDuration() {
		return EMP_DURATION_TICKS;
	}
}
