package fr.nekotine.vi6clean.impl.tool.personal.dephaser;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.status.effect.StatusEffect;
import fr.nekotine.core.status.effect.StatusEffectModule;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.impl.status.effect.DarkenedStatusEffectType;
import fr.nekotine.vi6clean.impl.tool.Tool;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class Dephaser extends Tool{
	private static final StatusEffect effect = new StatusEffect(DarkenedStatusEffectType.get(), DephaserHandler.INVISIBILITY_DURATION_TICKS);
	
	//
	
	@Override
	protected ItemStack makeInitialItemStack() {
		return ItemStackUtil.make(
				Material.IRON_NUGGET,
				Component.text("Déphasage",NamedTextColor.GOLD),
				DephaserHandler.LORE);
	}
	@Override
	protected void cleanup() {
		var statusEffectModule = Ioc.resolve(StatusEffectModule.class);
		statusEffectModule.removeEffect(getOwner(), effect);
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
		Ioc.resolve(StatusEffectModule.class).addEffect(getOwner(), effect);
		Vi6Sound.DEPHASER_ACTIVATE.play(getOwner());
		getOwner().setCooldown(Material.IRON_NUGGET, DephaserHandler.INVISIBILITY_DURATION_TICKS);
	}
	protected void deactivate() {
		Vi6Sound.DEPHASER_DEACTIVATE.play(getOwner());
		getOwner().setCooldown(Material.IRON_NUGGET, DephaserHandler.DELAY_BETWEEN_INVISIBILITY_TICKS - DephaserHandler.INVISIBILITY_DURATION_TICKS);
	}

	//

	@Override
	protected void onEmpStart() {
	}
	@Override
	protected void onEmpEnd() {
	}
}
