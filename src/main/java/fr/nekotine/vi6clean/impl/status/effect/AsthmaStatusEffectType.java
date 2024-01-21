package fr.nekotine.vi6clean.impl.status.effect;

import org.bukkit.entity.LivingEntity;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.status.effect.StatusEffectType;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.vi6clean.impl.status.flag.AsthmaStatusFlag;

public class AsthmaStatusEffectType implements StatusEffectType{
	private static AsthmaStatusEffectType instance;
	public static final AsthmaStatusEffectType get() {
		if (instance == null) {
			instance = new AsthmaStatusEffectType();
		}
		return instance;
	}
	
	//
	
	private AsthmaStatusEffectType() {
		Ioc.resolve(ModuleManager.class).tryLoad(StatusFlagModule.class);
	}
	@Override
	public void applyEffect(LivingEntity target) {
		Ioc.resolve(StatusFlagModule.class).addFlag(target, AsthmaStatusFlag.get());
	}
	@Override
	public void removeEffect(LivingEntity target) {
		Ioc.resolve(StatusFlagModule.class).removeFlag(target, AsthmaStatusFlag.get());
	}
}
