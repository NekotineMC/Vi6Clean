package fr.nekotine.vi6clean.impl.tool.personal.tazer;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.inventory.ItemStackBuilder;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

@ToolCode("tazer")
public class TazerHandler extends ToolHandler<Tazer>{
	private final ItemStack EMP_ITEM = new ItemStackBuilder(
			Material.IRON_INGOT)
			.name(getDisplayName().append(Component.text(" - ")).append(Component.text("Brouillé" , NamedTextColor.RED)))
			.lore(getLore())
			.build();
	private final ItemStack ITEM = ItemStackUtil.make(
			Material.SHEARS, 
			getDisplayName(), 
			getLore());
	private final int COOLDOWN_TICK = (int)(20*getConfiguration().getDouble("cooldown", 10));

	
	public TazerHandler() {
		super(Tazer::new);
	}
	
	@Override
	protected void onAttachedToPlayer(Tazer tool, Player player) {
	}

	@Override
	protected void onDetachFromPlayer(Tazer tool, Player player) {
	}
	
	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		for (var tool : getTools()) {
			tool.updateCooldown();
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
		if (EventUtil.isCustomAction(evt, CustomAction.HIT_ANY) && optionalTool.get().shot()) {
			evt.setCancelled(true);
		}
	}
	
	public ItemStack getItem() {
		return ITEM;
	}
	public ItemStack getEmpItem() {
		return EMP_ITEM;
	}
	public int getCooldownTick() {
		return COOLDOWN_TICK;
	}
	
}
