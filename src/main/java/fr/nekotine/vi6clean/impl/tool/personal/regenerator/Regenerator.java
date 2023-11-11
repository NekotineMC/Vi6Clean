package fr.nekotine.vi6clean.impl.tool.personal.regenerator;

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.inventory.ItemStack;

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
		if(healing)
			return;
		if(++tickCount >= RegeneratorHandler.DELAY_BEFORE_REGENERATING_TICKS) {
			healing = true;
			setItemStack(RegeneratorHandler.HEALING_ITEM());
		}
	}
	public void heal() {
		if(!healing)
			return;
		double maxHealth = getOwner().getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
		double newHealth = Math.min(maxHealth, getOwner().getHealth() + RegeneratorHandler.REGENERATION_AMOUNT);
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
}
