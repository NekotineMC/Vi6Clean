package fr.nekotine.vi6clean.impl.status.effect;

import org.bukkit.entity.LivingEntity;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.status.effect.StatusEffectType;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.vi6clean.impl.status.flag.OmniCaptedStatusFlag;

public class OmniCaptedStatusEffectType implements StatusEffectType{

	private static OmniCaptedStatusEffectType instance;
	
	public static final OmniCaptedStatusEffectType get() {
		if (instance == null) {
			instance = new OmniCaptedStatusEffectType();
		}
		return instance;
	}
	
	private OmniCaptedStatusEffectType() {
		Ioc.resolve(ModuleManager.class).tryLoad(StatusFlagModule.class);
	}
	
	@Override
	public void applyEffect(LivingEntity target) {
		Ioc.resolve(StatusFlagModule.class).addFlag(target, OmniCaptedStatusFlag.get());
	}

	@Override
	public void removeEffect(LivingEntity target) {
		Ioc.resolve(StatusFlagModule.class).removeFlag(target, OmniCaptedStatusFlag.get());
	}

}
