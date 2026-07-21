package fr.nekotine.vi6clean.status.effect;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.status.effect.StatusEffectType;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.vi6clean.status.flag.DiarrheaStatusFlag;

import org.bukkit.entity.LivingEntity;

public class DiarrheaStatusEffectType implements StatusEffectType {

	private static DiarrheaStatusEffectType instance;

	public static final DiarrheaStatusEffectType get() {
		if (instance == null) {
			instance = new DiarrheaStatusEffectType();
		}
		return instance;
	}

	//

	private DiarrheaStatusEffectType() {
		Ioc.resolve(ModuleManager.class).tryLoad(StatusFlagModule.class);
	}

	@Override
	public void applyEffect(LivingEntity target) {
		Ioc.resolve(StatusFlagModule.class).addFlag(target, DiarrheaStatusFlag.get());
	}

	@Override
	public void removeEffect(LivingEntity target) {
		Ioc.resolve(StatusFlagModule.class).removeFlag(target, DiarrheaStatusFlag.get());
	}
}
