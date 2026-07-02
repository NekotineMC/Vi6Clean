package fr.nekotine.vi6clean.impl.tool.personal.emp;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.status.effect.StatusEffect;
import fr.nekotine.core.status.effect.StatusEffectModule;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.util.InventoryUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.impl.status.effect.EmpStatusEffectType;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpEndEvent;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpStartEvent;
import fr.nekotine.vi6clean.impl.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

@ToolCode("emp")
public class EmpHandler extends ToolHandler<EmpHandler.Emp> {

	private final int EMP_DURATION_TICKS = (int) (20 * getConfiguration().getDouble("duration", 5));

	private final StatusEffect empEffect = new StatusEffect(EmpStatusEffectType.get(), EMP_DURATION_TICKS);

	public EmpHandler() {
		super(Emp::new);
	}

	@EventHandler
	private void onPlayerInteract(PlayerInteractEvent evt) {
		if (evt.getHand() != EquipmentSlot.HAND) {
			return;
		}
		var tool = getToolFromItem(evt.getItem());
		if (tool == null) {
			return;
		}

		var player = evt.getPlayer();
		if (Ioc.resolve(StatusFlagModule.class).hasAny(player, EmpStatusFlag.get())) {
			return;
		}
		if (EventUtil.isCustomAction(evt, CustomAction.HIT_ANY)) {

			var statusEffectModule = Ioc.resolve(StatusEffectModule.class);
			var opt = Ioc.resolve(WrappingModule.class).getWrapperOptional(player, PlayerWrapper.class);
			opt.get().enemyTeamInMap().forEach(p -> statusEffectModule.addEffect(p, empEffect));
			remove(tool);
			evt.setCancelled(true);
		}
	}

	@Override
	protected void onAttachedToPlayer(Emp tool) {
	}

	@Override
	protected void onDetachFromPlayer(Emp tool) {
	}

	@Override
	protected void onToolCleanup(Emp tool) {
	}

	@EventHandler
	private void onEmpStart(EntityEmpStartEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				item.setData(DataComponentTypes.ITEM_MODEL, Material.CONDUIT.key());
				item.editMeta(m -> m.displayName(getDisplayName().decorate(TextDecoration.STRIKETHROUGH)
						.append(Component.text(" - ")).append(Component.text("Brouillé", NamedTextColor.RED))));
			});
		}
	}

	@EventHandler
	private void onEmpEnd(EntityEmpEndEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				item.resetData(DataComponentTypes.ITEM_MODEL);
				item.editMeta(m -> m.displayName(getDisplayName()));
			});
		}
	}

	public static class Emp extends Tool {

		public Emp(ToolHandler<?> handler) {
			super(handler);
		}
	}
}
