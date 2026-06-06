package fr.nekotine.vi6clean.impl.status.effect;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.status.effect.StatusEffectType;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.vi6clean.impl.status.flag.MurmurStatusFlag;

import org.bukkit.entity.LivingEntity;

public class MurmurStatusEffectType implements StatusEffectType {
	private static MurmurStatusEffectType instance;

	public static final MurmurStatusEffectType get() {
		if (instance == null) {
			instance = new MurmurStatusEffectType();
		}
		return instance;
	}

	//

	private MurmurStatusEffectType() {
		Ioc.resolve(ModuleManager.class).tryLoad(StatusFlagModule.class);
	}

	@Override
	public void applyEffect(LivingEntity target) {
		Ioc.resolve(StatusFlagModule.class).addFlag(target, MurmurStatusFlag.get());
	}

	@Override
	public void removeEffect(LivingEntity target) {
		Ioc.resolve(StatusFlagModule.class).removeFlag(target, MurmurStatusFlag.get());
	}
}
