package fr.nekotine.vi6clean.impl.tool.personal.regenerator;

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.inventory.ItemStackBuilder;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.InventoryUtil;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpEndEvent;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpStartEvent;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

@ToolCode("regenerator")
public class RegeneratorHandler extends ToolHandler<RegeneratorHandler.Regenerator>{
	
	protected final int DELAY_BEFORE_REGENERATING_TICKS = (int)(20 * getConfiguration().getDouble("delay_before_regen", 5));
	
	protected final int DELAY_BETWEEN_HEALING_TICKS = (int)(20 * getConfiguration().getDouble("delay_between_heal", 1));
	
	protected final int REGENERATION_AMOUNT=(int)(20 * getConfiguration().getDouble("heal_amount", 1));
	
	public RegeneratorHandler() {
		super(Regenerator::new);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
	}
	
	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		for (var tool : getTools()) {
			if (tool.lastHitCounter > 0) {
				tool.lastHitCounter--;
			}
			var owner = tool.getOwner();
			var maxHealth = owner.getAttribute(Attribute.MAX_HEALTH).getValue();
			if (owner.getHealth() < maxHealth && tool.lastHitCounter <= 0) {
				owner.heal(REGENERATION_AMOUNT, RegainReason.MAGIC_REGEN);
				if (owner.getHealth() >= maxHealth) {
					editItem(tool, item -> {
						item.resetData(DataComponentTypes.ITEM_MODEL);
						item.editMeta(m -> m.displayName(getDisplayName().append(Component.text(" - ")).append(Component.text("Désactivé" , NamedTextColor.RED))));
					});
				}else {
					editItem(tool, item -> {
						item.setData(DataComponentTypes.ITEM_MODEL, Material.SOUL_CAMPFIRE.key());
						item.editMeta(m -> m.displayName(getDisplayName().append(Component.text(" - ")).append(Component.text("Activé" , NamedTextColor.GREEN))));
						owner.setCooldown(item, DELAY_BETWEEN_HEALING_TICKS);
					});
					tool.lastHitCounter = DELAY_BETWEEN_HEALING_TICKS;
				}
			}	
		}
	}
	@EventHandler
	private void onDamage(EntityDamageEvent evt) {
		getTools().stream().filter(t -> evt.getEntity().equals(t.getOwner())).forEach(tool -> {
			editItem(tool, item -> {
				item.resetData(DataComponentTypes.ITEM_MODEL);
				item.editMeta(m -> m.displayName(getDisplayName().append(Component.text(" - ")).append(Component.text("Désactivé" , NamedTextColor.RED))));
				tool.getOwner().setCooldown(item, DELAY_BEFORE_REGENERATING_TICKS);
			});
			tool.lastHitCounter = DELAY_BEFORE_REGENERATING_TICKS;
		});
	}
	@Override
	protected void onAttachedToPlayer(Regenerator tool) {
	}
	@Override
	protected void onDetachFromPlayer(Regenerator tool) {
	}
	@Override
	protected void onToolCleanup(Regenerator tool) {
	}
	@Override
	protected ItemStack makeItem(Regenerator tool) {
		return new ItemStackBuilder(Material.CAMPFIRE)
				.name(Component.text("Régénérateur",NamedTextColor.GOLD).append(Component.text(" - ").append(Component.text("Désactivé",NamedTextColor.RED))))
				.lore(getLore())
				.unstackable()
				.flags(ItemFlag.values())
				.build();
	}
	
	@EventHandler
	private void onEmpStart(EntityEmpStartEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				var tool = getToolFromItem(item);
				editItem(tool, i -> {
					i.resetData(DataComponentTypes.ITEM_MODEL);
					i.editMeta(m -> m.displayName(getDisplayName().decorate(TextDecoration.STRIKETHROUGH).append(Component.text(" - ")).append(Component.text("Brouillé" , NamedTextColor.RED))));
				});
			});
		}
	}
	
	@EventHandler
	private void onEmpStop(EntityEmpEndEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				var tool = getToolFromItem(item);
				editItem(tool, i -> {
					i.resetData(DataComponentTypes.ITEM_MODEL);
					i.editMeta(m -> m.displayName(getDisplayName().append(Component.text(" - ")).append(Component.text("Désactivé" , NamedTextColor.RED))));
				});
				tool.lastHitCounter = DELAY_BEFORE_REGENERATING_TICKS;
				p.setCooldown(item, DELAY_BEFORE_REGENERATING_TICKS);
			});
		}
	}
	
	public static class Regenerator extends Tool{

		private int lastHitCounter;
		
		public Regenerator(ToolHandler<?> handler) {
			super(handler);
		}
		
	}
	
}
