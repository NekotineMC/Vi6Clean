package fr.nekotine.vi6clean.impl.tool.personal.emp;

import fr.nekotine.core.inventory.ItemStackBuilder;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.status.effect.StatusEffect;
import fr.nekotine.core.status.effect.StatusEffectModule;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.impl.status.effect.EmpStatusEffectType;
import fr.nekotine.vi6clean.impl.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

@ToolCode("emp")
public class EmpHandler extends ToolHandler<Emp> {

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

		//
		var player = evt.getPlayer();
		if (Ioc.resolve(StatusFlagModule.class).hasAny(player, EmpStatusFlag.get())) {
			return;
		}
		if (EventUtil.isCustomAction(evt, CustomAction.HIT_ANY)) {

			var statusEffectModule = Ioc.resolve(StatusEffectModule.class);
			var opt = Ioc.resolve(WrappingModule.class).getWrapperOptional(player, PlayerWrapper.class);
			opt.get().ennemiTeamInMap().forEach(p -> statusEffectModule.addEffect(p, empEffect));
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

	@Override
	protected ItemStack makeItem(Emp tool) {
		return new ItemStackBuilder(Material.BEACON).name(getDisplayName()).lore(getLore()).unstackable().build();
	}
}
