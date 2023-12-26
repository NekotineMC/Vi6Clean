package fr.nekotine.vi6clean.impl.tool.personal.emp;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.inventory.ItemStackBuilder;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.status.effect.StatusEffect;
import fr.nekotine.core.status.effect.StatusEffectModule;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.impl.status.effect.EmpStatusEffectType;
import fr.nekotine.vi6clean.impl.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;

public class Emp extends Tool{
	private static final StatusEffect effect = new StatusEffect(
			EmpStatusEffectType.get(), Ioc.resolve(EmpHandler.class).getEmpDuration());
	
	//
	
	@Override
	protected ItemStack makeInitialItemStack() {
		var handler = Ioc.resolve(EmpHandler.class);
		return new ItemStackBuilder(
				Material.BEACON)
				.name(handler.getDisplayName())
				.lore(handler.getLore())
				.unstackable().build();
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
