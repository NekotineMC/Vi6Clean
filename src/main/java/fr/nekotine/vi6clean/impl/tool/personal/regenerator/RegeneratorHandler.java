package fr.nekotine.vi6clean.impl.tool.personal.regenerator;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.NekotineCore;
import fr.nekotine.core.inventory.ItemStackBuilder;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.vi6clean.constant.Vi6ToolLoreText;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.tool.ToolType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public class RegeneratorHandler extends ToolHandler<Regenerator>{
	protected final static int DELAY_BEFORE_REGENERATING_TICKS = 100;
	protected final static int DELAY_BETWEEN_HEALING_TICKS = 20;
	protected final static int REGENERATION_AMOUNT=1;
	protected static final ItemStack IDLE_ITEM() {
		return new ItemStackBuilder(Material.CAMPFIRE)
		.name(Component.text("Régénérateur",NamedTextColor.GOLD).append(Component.text(" - ").append(Component.text("Désactivé",NamedTextColor.RED))))
		.lore(RegeneratorHandler.LORE)
		.unstackable()
		.flags(ItemFlag.values())
		.build();
	}
	protected static final ItemStack HEALING_ITEM() {
		return new ItemStackBuilder(Material.CAMPFIRE)
		.name(Component.text("Régénérateur",NamedTextColor.GOLD).append(Component.text(" - ").append(Component.text("Activé",NamedTextColor.GREEN))))
		.lore(RegeneratorHandler.LORE)
		.unstackable()
		.flags(ItemFlag.values())
		.enchant()
		.build();
	}
	public static final List<Component> LORE = Vi6ToolLoreText.REGENERATOR.make(
			Placeholder.unparsed("delay", (int)(DELAY_BEFORE_REGENERATING_TICKS/20)+" secondes"));
			
	//
	
	public RegeneratorHandler() {
		super(ToolType.REGENERATOR, Regenerator::new);
		NekotineCore.MODULES.tryLoad(TickingModule.class);
	}
	@Override
	protected void onAttachedToPlayer(Regenerator tool, Player player) {
	}
	@Override
	protected void onDetachFromPlayer(Regenerator tool, Player player) {
	}
	
	//
	
	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		for (var tool : getTools()) {
			tool.tick();
		}
	}
	@EventHandler
	private void onDamage(EntityDamageEvent evt) {
		getTools().stream().filter(t -> evt.getEntity().equals(t.getOwner())).forEach(t -> t.onDamage());
	}
}
