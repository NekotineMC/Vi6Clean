package fr.nekotine.vi6clean.impl.status.effect;

import org.bukkit.entity.LivingEntity;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.status.effect.StatusEffectType;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.vi6clean.impl.status.flag.TazedStatusFlag;

public class TazedStatusEffectType implements StatusEffectType{

	private static TazedStatusEffectType instance;
	
	public static final TazedStatusEffectType get() {
		if (instance == null) {
			instance = new TazedStatusEffectType();
		}
		return instance;
	}
	
	private TazedStatusEffectType() {
		Ioc.resolve(ModuleManager.class).tryLoad(StatusFlagModule.class);
	}
	
	@Override
	public void applyEffect(LivingEntity target) {
		Ioc.resolve(StatusFlagModule.class).addFlag(target, TazedStatusFlag.get());
	}

	@Override
	public void removeEffect(LivingEntity target) {
		Ioc.resolve(StatusFlagModule.class).removeFlag(target, TazedStatusFlag.get());
	}
}
