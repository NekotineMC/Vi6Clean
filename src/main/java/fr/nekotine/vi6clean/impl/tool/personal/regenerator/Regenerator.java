package fr.nekotine.vi6clean.impl.tool.personal.regenerator;

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.vi6clean.impl.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.impl.tool.Tool;

public class Regenerator extends Tool{
	private boolean healing = false;
	private int tickCount = 0;
	@Override
	protected ItemStack makeInitialItemStack() {
		return RegeneratorHandler.IDLE_ITEM();
	}
	@Override
	protected void cleanup() {
	}
	public void tick() {
		var flagModule = Ioc.resolve(StatusFlagModule.class);
		if(healing || flagModule.hasAny(getOwner(), EmpStatusFlag.get()))
			return;
		if(++tickCount >= RegeneratorHandler.DELAY_BEFORE_REGENERATING_TICKS) {
			healing = true;
			setItemStack(RegeneratorHandler.HEALING_ITEM());
		}
	}
	public void heal() {
		if(!healing) {
			return;
		}
		double maxHealth = getOwner().getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
		double newHealth = Math.min(maxHealth, getOwner().getHealth() + RegeneratorHandler.REGENERATION_AMOUNT);
		getOwner().setHealth(newHealth);
		if(newHealth!=maxHealth) {
			getOwner().setCooldown(Material.CAMPFIRE, RegeneratorHandler.DELAY_BETWEEN_HEALING_TICKS);
		}
	}
	public void onDamage() {
		getOwner().setCooldown(Material.CAMPFIRE, RegeneratorHandler.DELAY_BEFORE_REGENERATING_TICKS);
		tickCount = 0;
		healing = false;
		setItemStack(RegeneratorHandler.IDLE_ITEM());
	}

	//

	@Override
	protected void onEmpStart() {
		healing = false;
		tickCount = 0;
	}
	@Override
	protected void onEmpEnd() {
	}
}
