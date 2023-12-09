package fr.nekotine.vi6clean.impl.status.effect;

import org.bukkit.entity.LivingEntity;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.status.effect.StatusEffectType;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.vi6clean.impl.status.flag.DarkenedStatusFlag;

public class DarkenedStatusEffectType implements StatusEffectType{
	private static DarkenedStatusEffectType instance;
	public static final DarkenedStatusEffectType get() {
		if (instance == null) {
			instance = new DarkenedStatusEffectType();
		}
		return instance;
	}
	
	//
	
	private DarkenedStatusEffectType() {
		Ioc.resolve(ModuleManager.class).tryLoad(StatusFlagModule.class);
	}
	@Override
	public void applyEffect(LivingEntity target) {
		Ioc.resolve(StatusFlagModule.class).addFlag(target, DarkenedStatusFlag.get());
	}
	@Override
	public void removeEffect(LivingEntity target) {
		Ioc.resolve(StatusFlagModule.class).removeFlag(target, DarkenedStatusFlag.get());
	}
}
