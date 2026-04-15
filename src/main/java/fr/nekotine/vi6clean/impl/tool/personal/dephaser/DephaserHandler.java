package fr.nekotine.vi6clean.impl.tool.personal.dephaser;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.status.effect.StatusEffect;
import fr.nekotine.core.status.effect.StatusEffectModule;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.InventoryUtil;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.core.util.TimeUtil;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.impl.status.effect.invisibility.InvisibilityStatusEffectType;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpStartEvent;
import fr.nekotine.vi6clean.impl.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import io.papermc.paper.util.Tick;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.ItemStack;

@ToolCode("dephaser")
public class DephaserHandler extends ToolHandler<Dephaser> {

	private final int DELAY_BETWEEN_INVISIBILITY_TICKS = Tick.tick()
			.fromDuration(TimeUtil.fromSeconds(getConfiguration().getDouble("inv_delay", 20)));

	private final int INVISIBILITY_DURATION_TICKS = Tick.tick()
			.fromDuration(TimeUtil.fromSeconds(getConfiguration().getDouble("inv_duration", 2)));

	private final int DELAY_BETWEEN_WARNING_SOUND = Tick.tick().fromDuration(TimeUtil.fromSeconds(0.5));

	private int count = DELAY_BETWEEN_INVISIBILITY_TICKS;

	private final StatusEffect effect = new StatusEffect(InvisibilityStatusEffectType.get(),
			INVISIBILITY_DURATION_TICKS);

	public DephaserHandler() {
		super(Dephaser::new);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
	}

	//

	@EventHandler
	public void onTick(TickElapsedEvent evt) {
		count--;
		if (count == DELAY_BETWEEN_INVISIBILITY_TICKS - INVISIBILITY_DURATION_TICKS + DELAY_BETWEEN_WARNING_SOUND * 2) {
			getTools().stream().filter(t -> t.isActive())
					.forEach(t -> Vi6Sound.DEPHASER_WARNING_HIGH.play(t.getOwner()));
			return;
		}
		if (count == DELAY_BETWEEN_INVISIBILITY_TICKS - INVISIBILITY_DURATION_TICKS + DELAY_BETWEEN_WARNING_SOUND) {
			getTools().stream().filter(t -> t.isActive())
					.forEach(t -> Vi6Sound.DEPHASER_WARNING_MID.play(t.getOwner()));
			return;
		}
		if (count == DELAY_BETWEEN_INVISIBILITY_TICKS - INVISIBILITY_DURATION_TICKS) {
			getTools().stream().filter(t -> t.isActive()).forEach(t -> deactivate(t));
			return;
		}
		if (count == DELAY_BETWEEN_WARNING_SOUND * 2) {
			getTools().forEach(t -> Vi6Sound.DEPHASER_WARNING_LOW.play(t.getOwner()));
			return;
		}
		if (count == DELAY_BETWEEN_WARNING_SOUND) {
			getTools().forEach(t -> Vi6Sound.DEPHASER_WARNING_MID.play(t.getOwner()));
			return;
		}
		if (count == 0) {
			getTools().forEach(t -> activate(t));
			count = DELAY_BETWEEN_INVISIBILITY_TICKS;
			return;
		}
	}

	private void activate(Dephaser tool) {
		if (Ioc.resolve(StatusFlagModule.class).hasAny(tool.getOwner(), EmpStatusFlag.get())) {
			return;
		}
		Ioc.resolve(StatusEffectModule.class).addEffect(tool.getOwner(), effect);
		Vi6Sound.DEPHASER_ACTIVATE.play(tool.getOwner());
		tool.getOwner().setCooldown(Material.IRON_NUGGET, INVISIBILITY_DURATION_TICKS);
		tool.setActive(true);
	}

	private void deactivate(Dephaser tool) {
		Ioc.resolve(StatusEffectModule.class).removeEffect(tool.getOwner(), effect);
		Vi6Sound.DEPHASER_DEACTIVATE.play(tool.getOwner());
		tool.getOwner().setCooldown(Material.IRON_NUGGET, count);
		tool.setActive(false);
	}

	@Override
	protected void onAttachedToPlayer(Dephaser tool) {
	}

	@Override
	protected void onDetachFromPlayer(Dephaser tool) {
		deactivate(tool);
	}

	@Override
	protected void onToolCleanup(Dephaser tool) {
	}

	@Override
	protected ItemStack makeItem(Dephaser tool) {
		return ItemStackUtil.make(Material.IRON_NUGGET, Ioc.resolve(DephaserHandler.class).getDisplayName(),
				Ioc.resolve(DephaserHandler.class).getLore());
	}

	@EventHandler
	private void onEmpStart(EntityEmpStartEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).stream()
					.map(this::getToolFromItem).filter(t -> t != null && t.getOwner() != null)
					.forEach(this::deactivate);
		}
	}
}
