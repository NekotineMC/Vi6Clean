package fr.nekotine.vi6clean.impl.tool.personal.regenerator;

import fr.nekotine.core.inventory.ItemStackBuilder;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.InventoryUtil;
import fr.nekotine.vi6clean.impl.map.artefact.ArtefactStealEvent;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpEndEvent;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpStartEvent;
import fr.nekotine.vi6clean.impl.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

@ToolCode("regenerator")
public class RegeneratorHandler extends ToolHandler<RegeneratorHandler.Regenerator> {

	private final int DELAY_BETWEEN_HEALING_TICKS = (int) (20 * getConfiguration().getDouble("delay_between_heal", 1));

	private final int REGEN_DURATION_TICKS = (int) (20 * getConfiguration().getDouble("regen_duration", 4.0));

	private final double REGENERATION_AMOUNT = getConfiguration().getDouble("heal_amount", 1);

	private final StatusFlagModule flagModule;

	public RegeneratorHandler() {
		super(Regenerator::new);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
		flagModule = Ioc.resolve(StatusFlagModule.class);
	}

	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		for (var tool : getTools()) {
			if (!tool.isActive) {
				continue;
			}
			var maxHealth = tool.getOwner().getAttribute(Attribute.MAX_HEALTH).getValue();
			if (tool.regenTicked % DELAY_BETWEEN_HEALING_TICKS == 0 && tool.getOwner().getHealth() < maxHealth
					&& !flagModule.hasAny(tool.getOwner(), EmpStatusFlag.get())) {
				tool.getOwner().heal(REGENERATION_AMOUNT, RegainReason.MAGIC_REGEN);
			}
			tool.regenTicked++;
			if (tool.regenTicked >= REGEN_DURATION_TICKS) {
				tool.isActive = false;
				editItem(tool, item -> {
					item.resetData(DataComponentTypes.ITEM_MODEL);
					item.editMeta(m -> m.displayName(getDisplayName()));
				});
			}
		}
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

	@EventHandler
	private void onArtefactSteal(ArtefactStealEvent evt) {
		for (var tool : getTools()) {
			if (evt.getThief().equals(tool.getOwner())) {
				editItem(tool, item -> {
					item.setData(DataComponentTypes.ITEM_MODEL, Material.SOUL_CAMPFIRE.key());
					if (!flagModule.hasAny(tool.getOwner(), EmpStatusFlag.get())) {
						item.editMeta(m -> m.displayName(getDisplayName().append(Component.text(" - "))
								.append(Component.text("Activé", NamedTextColor.GREEN))));
					}
					tool.getOwner().setCooldown(item, REGEN_DURATION_TICKS);
				});
				tool.isActive = true;
				tool.regenTicked = 0;
			}
		}
	}

	@Override
	protected ItemStack makeItem(Regenerator tool) {
		return new ItemStackBuilder(Material.CAMPFIRE).name(Component.text("Régénérateur", NamedTextColor.GOLD))
				.lore(getLore()).unstackable().flags(ItemFlag.values()).build();
	}

	@EventHandler
	private void onEmpStart(EntityEmpStartEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				var tool = getToolFromItem(item);
				editItem(tool, i -> {
					i.editMeta(m -> m.displayName(getDisplayName().decorate(TextDecoration.STRIKETHROUGH)
							.append(Component.text(" - ")).append(Component.text("Brouillé", NamedTextColor.RED))));
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
					i.editMeta(m -> m.displayName(getDisplayName()));
					if (tool.isActive) {
						item.editMeta(m -> m.displayName(getDisplayName().append(Component.text(" - "))
								.append(Component.text("Activé", NamedTextColor.GREEN))));
					} else {
						item.editMeta(m -> m.displayName(getDisplayName()));
					}
				});
			});
		}
	}

	public static class Regenerator extends Tool {

		private boolean isActive = false;
		private int regenTicked;

		public Regenerator(ToolHandler<?> handler) {
			super(handler);
		}
	}
}
