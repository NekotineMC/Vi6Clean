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
		return Ioc.resolve(RegeneratorHandler.class).IDLE_ITEM();
	}
	@Override
	protected void cleanup() {
	}
	public void tick() {
		var flagModule = Ioc.resolve(StatusFlagModule.class);
		if(healing || flagModule.hasAny(getOwner(), EmpStatusFlag.get()))
			return;
		if(++tickCount >= Ioc.resolve(RegeneratorHandler.class).DELAY_BEFORE_REGENERATING_TICKS) {
			healing = true;
			setItemStack(Ioc.resolve(RegeneratorHandler.class).HEALING_ITEM());
		}
	}
	public void heal() {
		if(!healing) {
			return;
		}
		double maxHealth = getOwner().getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
		double newHealth = Math.min(maxHealth, getOwner().getHealth() + Ioc.resolve(RegeneratorHandler.class).REGENERATION_AMOUNT);
		getOwner().setHealth(newHealth);
		if(newHealth!=maxHealth) {
			getOwner().setCooldown(Material.CAMPFIRE, Ioc.resolve(RegeneratorHandler.class).DELAY_BETWEEN_HEALING_TICKS);
		}
	}
	public void onDamage() {
		getOwner().setCooldown(Material.CAMPFIRE, Ioc.resolve(RegeneratorHandler.class).DELAY_BEFORE_REGENERATING_TICKS);
		tickCount = 0;
		healing = false;
		setItemStack(Ioc.resolve(RegeneratorHandler.class).IDLE_ITEM());
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
