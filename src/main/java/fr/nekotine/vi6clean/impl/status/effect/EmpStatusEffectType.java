package fr.nekotine.vi6clean.impl.status.effect;

import org.bukkit.entity.LivingEntity;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.status.effect.StatusEffectType;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.vi6clean.impl.status.flag.EmpStatusFlag;

public class EmpStatusEffectType implements StatusEffectType{
	private static EmpStatusEffectType instance;
	public static final EmpStatusEffectType get() {
		if (instance == null) {
			instance = new EmpStatusEffectType();
		}
		return instance;
	}
	
	//
	
	private EmpStatusEffectType() {
		Ioc.resolve(ModuleManager.class).tryLoad(StatusFlagModule.class);
	}
	@Override
	public void applyEffect(LivingEntity target) {
		Ioc.resolve(StatusFlagModule.class).addFlag(target, EmpStatusFlag.get());
	}
	@Override
	public void removeEffect(LivingEntity target) {
		Ioc.resolve(StatusFlagModule.class).removeFlag(target, EmpStatusFlag.get());
	}
}
