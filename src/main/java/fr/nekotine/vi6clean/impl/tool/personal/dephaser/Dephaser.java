package fr.nekotine.vi6clean.impl.tool.personal.dephaser;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.impl.status.flag.InvisibleStatusFlag;
import fr.nekotine.vi6clean.impl.tool.Tool;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class Dephaser extends Tool{
	@Override
	protected ItemStack makeInitialItemStack() {
		return ItemStackUtil.make(
				Material.IRON_NUGGET,
				Component.text("Déphasage",NamedTextColor.GOLD),
				DephaserHandler.LORE);
	}
	@Override
	protected void cleanup() {
		var flagModule = Ioc.resolve(StatusFlagModule.class);
		flagModule.removeFlag(getOwner(), InvisibleStatusFlag.get());
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
		var flagModule = Ioc.resolve(StatusFlagModule.class);
		flagModule.addFlag(getOwner(), InvisibleStatusFlag.get());
		Vi6Sound.DEPHASER_ACTIVATE.play(getOwner());
		getOwner().setCooldown(Material.IRON_NUGGET, DephaserHandler.INVISIBILITY_DURATION_TICKS);
	}
	protected void deactivate() {
		cleanup();
		Vi6Sound.DEPHASER_DEACTIVATE.play(getOwner());
		getOwner().setCooldown(Material.IRON_NUGGET, DephaserHandler.DELAY_BETWEEN_INVISIBILITY_TICKS - DephaserHandler.INVISIBILITY_DURATION_TICKS);
	}
}
