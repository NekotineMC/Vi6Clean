package fr.nekotine.vi6clean.impl.tool.personal.regenerator;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.inventory.ItemStackBuilder;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

@ToolCode("regenerator")
public class RegeneratorHandler extends ToolHandler<Regenerator>{
	protected final int DELAY_BEFORE_REGENERATING_TICKS = (int)(20 * getConfiguration().getDouble("delay_before_regen", 5));
	protected final int DELAY_BETWEEN_HEALING_TICKS = (int)(20 * getConfiguration().getDouble("delay_between_heal", 1));
	protected final int REGENERATION_AMOUNT=(int)(20 * getConfiguration().getDouble("heal_amount", 1));
	protected final ItemStack IDLE_ITEM() {
		return new ItemStackBuilder(Material.CAMPFIRE)
		.name(Component.text("Régénérateur",NamedTextColor.GOLD).append(Component.text(" - ").append(Component.text("Désactivé",NamedTextColor.RED))))
		.lore(getLore())
		.unstackable()
		.flags(ItemFlag.values())
		.build();
	}
	protected final ItemStack HEALING_ITEM() {
		return new ItemStackBuilder(Material.CAMPFIRE)
		.name(Component.text("Régénérateur",NamedTextColor.GOLD).append(Component.text(" - ").append(Component.text("Activé",NamedTextColor.GREEN))))
		.lore(getLore())
		.unstackable()
		.flags(ItemFlag.values())
		.enchant()
		.build();
	}

	private int healingTick = 0;
	//
	
	public RegeneratorHandler() {
		super(Regenerator::new);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
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
		var heal = ++healingTick >= DELAY_BETWEEN_HEALING_TICKS ? true : false;
		for (var tool : getTools()) {
			tool.tick();
			if(heal) {
				tool.heal();
				healingTick = 0;
			}		
		}
	}
	@EventHandler
	private void onDamage(EntityDamageEvent evt) {
		getTools().stream().filter(t -> evt.getEntity().equals(t.getOwner())).forEach(t -> t.onDamage());
	}
}
