package fr.nekotine.vi6clean.status.effect;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.status.effect.StatusEffectType;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.vi6clean.status.flag.SuffocatingStatusFlag;

import org.bukkit.entity.LivingEntity;

public class SuffocatingStatusEffectType implements StatusEffectType {

	private static SuffocatingStatusEffectType instance;

	public static final SuffocatingStatusEffectType get() {
		if (instance == null) {
			instance = new SuffocatingStatusEffectType();
		}
		return instance;
	}

	private SuffocatingStatusEffectType() {
		Ioc.resolve(ModuleManager.class).tryLoad(StatusFlagModule.class);
	}

	@Override
	public void applyEffect(LivingEntity target) {
		Ioc.resolve(StatusFlagModule.class).addFlag(target, SuffocatingStatusFlag.get());
	}

	@Override
	public void removeEffect(LivingEntity target) {
		Ioc.resolve(StatusFlagModule.class).removeFlag(target, SuffocatingStatusFlag.get());
	}
}
