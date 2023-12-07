package fr.nekotine.vi6clean.impl.status.effect;

import org.bukkit.entity.LivingEntity;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.status.effect.StatusEffectType;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.vi6clean.impl.status.flag.InvisibleStatusFlag;

public class InvisibleStatusEffectType implements StatusEffectType{

	private static InvisibleStatusEffectType instance;
	
	public static final InvisibleStatusEffectType get() {
		if (instance == null) {
			instance = new InvisibleStatusEffectType();
		}
		return instance;
	}
	
	private InvisibleStatusEffectType() {
		Ioc.resolve(ModuleManager.class).tryLoad(StatusFlagModule.class);
	}
	
	@Override
	public void applyEffect(LivingEntity target) {
		Ioc.resolve(StatusFlagModule.class).addFlag(target, InvisibleStatusFlag.get());
	}

	@Override
	public void removeEffect(LivingEntity target) {
		Ioc.resolve(StatusFlagModule.class).removeFlag(target, InvisibleStatusFlag.get());
	}

}
