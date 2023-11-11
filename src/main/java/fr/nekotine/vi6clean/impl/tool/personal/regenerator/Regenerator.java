package fr.nekotine.vi6clean.impl.tool.personal.regenerator;

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
		if(healing) {
			if(++tickCount >= RegeneratorHandler.DELAY_BETWEEN_HEALING_TICKS) {
				tickCount = 0;
				getOwner().setHealth(Math.min(getOwner().getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue(), getOwner().getHealth() + RegeneratorHandler.REGENERATION_AMOUNT));
			}
		}else {
			if(++tickCount >= RegeneratorHandler.DELAY_BEFORE_REGENERATING_TICKS) {
				tickCount = 0;
				healing = true;
				setItemStack(RegeneratorHandler.HEALING_ITEM());
			}
		}
	}
	public void onDamage() {
		tickCount = 0;
		healing = false;
		setItemStack(RegeneratorHandler.IDLE_ITEM());
	}
}
