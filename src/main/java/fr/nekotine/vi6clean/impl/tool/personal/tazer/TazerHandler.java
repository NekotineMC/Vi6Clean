package fr.nekotine.vi6clean.impl.tool.personal.tazer;

import java.util.List;

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
import fr.nekotine.vi6clean.constant.Vi6ToolLoreText;
import fr.nekotine.vi6clean.impl.status.flag.TazedStatusFlag;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.tool.ToolType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public class TazerHandler extends ToolHandler<Tazer>{
	protected static final ItemStack EMP_ITEM = new ItemStackBuilder(Material.IRON_INGOT)
			.name(Component.text("Tazer", NamedTextColor.GOLD).append(Component.text(" - ")).append(Component.text("Brouillé" , NamedTextColor.RED)))
			.lore(TazerHandler.LORE)
			.build();
	
	protected static final ItemStack ITEM = ItemStackUtil.make(Material.SHEARS, Component.text("Tazer", NamedTextColor.GOLD), TazerHandler.LORE);
	
	public static final int COOLDOWN_TICK = 200;
	
	public static final List<Component> LORE = Vi6ToolLoreText.TAZER.make(
		Placeholder.unparsed("cooldown", ((int)COOLDOWN_TICK/20)+" secondes"),
		Placeholder.parsed("statusname", TazedStatusFlag.getStatusName())
	);
	
	public TazerHandler() {
		super(ToolType.TAZER, Tazer::new);
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
	
}
