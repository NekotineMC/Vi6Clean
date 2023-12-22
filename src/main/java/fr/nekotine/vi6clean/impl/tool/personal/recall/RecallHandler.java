package fr.nekotine.vi6clean.impl.tool.personal.recall;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.inventory.ItemStackBuilder;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.track.EntityTrackModule;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.vi6clean.constant.Vi6ToolLoreText;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.tool.ToolType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public class RecallHandler extends ToolHandler<Recall>{
	protected static final int TELEPORT_DELAY_TICKS = 20*6;
	protected static final int PARTICLE_NUMBER = 2;
	protected static final ItemStack UNPLACED() {
		return new ItemStackBuilder(Material.CHORUS_FRUIT)
		.name(Component.text("Retour",NamedTextColor.GOLD))
		.lore(RecallHandler.LORE)
		.unstackable()
		.flags(ItemFlag.values())
		.build();
	}
	protected static final ItemStack PLACED() {
		return new ItemStackBuilder(Material.POPPED_CHORUS_FRUIT)
		.name(Component.text("Retour",NamedTextColor.GOLD))
		.lore(RecallHandler.LORE)
		.unstackable()
		.flags(ItemFlag.values())
		.build();
	}
	
	public static final List<Component> LORE = Vi6ToolLoreText.RECALL.make(
			Placeholder.parsed("duration", (TELEPORT_DELAY_TICKS/20)+"s")
	);
	public RecallHandler() {
		super(ToolType.RECALL, Recall::new);
		Ioc.resolve(ModuleManager.class).tryLoad(EntityTrackModule.class);
	}

	@Override
	protected void onAttachedToPlayer(Recall tool, Player player) {
	}
	@Override
	protected void onDetachFromPlayer(Recall tool, Player player) {
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
		
		if(EventUtil.isCustomAction(evt, CustomAction.INTERACT_ANY)) {
			evt.setCancelled(true);
		}
		if (EventUtil.isCustomAction(evt, CustomAction.HIT_ANY) && optionalTool.get().use()) {
			evt.setCancelled(true);
		}
	}
	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		for(var tool : getTools()) {
			tool.tickCooldown();
			tool.tickParticle();
		}
	}
}
