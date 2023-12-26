package fr.nekotine.vi6clean.impl.tool.personal.dephaser;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.impl.tool.Tool;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class Dephaser extends Tool{
	
	private boolean emp;
	
	private boolean inv;
	
	private final PotionEffect effect = new PotionEffect(
			PotionEffectType.INVISIBILITY, 
			Ioc.resolve(DephaserHandler.class).getInvisibilityDurationTick(), 
			0, 
			false, 
			false, 
			true);
	
	//
	
	@Override
	protected ItemStack makeInitialItemStack() {
		return ItemStackUtil.make(
				Material.IRON_NUGGET,
				Component.text("Déphasage",NamedTextColor.GOLD),
				Ioc.resolve(DephaserHandler.class).getLore());
	}
	@Override
	protected void cleanup() {
	}

	//
	
	protected void playWarningLow() {
		Vi6Sound.DEPHASER_WARNING_LOW.play(getOwner());
	}
	protected void playerWarningMid() {
		Vi6Sound.DEPHASER_WARNING_MID.play(getOwner());
	}
	protected void playerWarningHigh() {
		Vi6Sound.DEPHASER_WARNING_HIGH.play(getOwner());
	}
	protected void activate() {
		if (emp) {
			return;
		}
		getOwner().addPotionEffect(effect);
		Vi6Sound.DEPHASER_ACTIVATE.play(getOwner());
		getOwner().setCooldown(Material.IRON_NUGGET,Ioc.resolve(DephaserHandler.class).getInvisibilityDurationTick());
		inv = true;
	}
	protected void deactivate() {
		var handler = Ioc.resolve(DephaserHandler.class);
		Vi6Sound.DEPHASER_DEACTIVATE.play(getOwner());
		getOwner().setCooldown(Material.IRON_NUGGET, handler.getDelayBetweenInvisibilityTick() - handler.getInvisibilityDurationTick());
		inv = false;
	}

	//

	@Override
	protected void onEmpStart() {
		deactivate();
		if (inv) {
			deactivate();
		}
		emp = true;
	}
	@Override
	protected void onEmpEnd() {
		emp = false;
	}
}
