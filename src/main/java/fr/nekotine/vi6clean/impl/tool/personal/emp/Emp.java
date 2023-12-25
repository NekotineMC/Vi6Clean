package fr.nekotine.vi6clean.impl.tool.personal.emp;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.status.effect.StatusEffect;
import fr.nekotine.core.status.effect.StatusEffectModule;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.impl.status.effect.EmpStatusEffectType;
import fr.nekotine.vi6clean.impl.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class Emp extends Tool{
	private static final StatusEffect effect = new StatusEffect(
			EmpStatusEffectType.get(), Ioc.resolve(EmpHandler.class).getEmpDuration());
	
	//
	
	@Override
	protected ItemStack makeInitialItemStack() {
		return ItemStackUtil.make(
				Material.BEACON,Component.text("IEM",NamedTextColor.GOLD), 
				Ioc.resolve(EmpHandler.class).getLore());
	}
	@Override
	protected void cleanup() {
		var statusEffectModule = Ioc.resolve(StatusEffectModule.class);
		statusEffectModule.removeEffect(getOwner(), effect);
	}
	
	//
	
	protected boolean trigger() {
		var flagModule = Ioc.resolve(StatusFlagModule.class);
		if(flagModule.hasAny(getOwner(), EmpStatusFlag.get())) {
			return false;
		}
		var statusEffectModule = Ioc.resolve(StatusEffectModule.class);
		var opt = Ioc.resolve(WrappingModule.class).getWrapperOptional(getOwner(), PlayerWrapper.class);
		opt.get().ennemiTeamInMap().forEach(p -> statusEffectModule.addEffect(p, effect));
		return true;
	}

	//

	@Override
	protected void onEmpStart() {
	}
	@Override
	protected void onEmpEnd() {
	}
}
