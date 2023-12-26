package fr.nekotine.vi6clean.impl.tool.personal.lantern;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.ticking.TickTimeStamp;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.core.util.SpatialUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;

@ToolCode("lantern")
public class LanternHandler extends ToolHandler<Lantern>{
	private final ItemStack NO_LANTERN_ITEMSTACK = ItemStackUtil.make(
			Material.CHAIN, 1,
			getDisplayName(), 
			getLore());
	private final int MAX_LANTERN = getConfiguration().getInt("max_lantern",2);	
	private final double SQUARED_PICKUP_BLOCK_RANGE = getConfiguration().getDouble("squared_pickup_range", 2.25);

	public LanternHandler() {
		super(Lantern::new);
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
	
	public ItemStack getNoLanternItemstack() {
		return NO_LANTERN_ITEMSTACK;
	}
	public int getMaxLantern() {
		return MAX_LANTERN;
	}
	public double getSquaredPickupBlockRange() {
		return SQUARED_PICKUP_BLOCK_RANGE;
	}
}
