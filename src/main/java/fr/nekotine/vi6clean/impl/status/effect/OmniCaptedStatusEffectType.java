package fr.nekotine.vi6clean.impl.status.effect;

import org.bukkit.entity.LivingEntity;

import fr.nekotine.core.NekotineCore;
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
		NekotineCore.MODULES.tryLoad(StatusFlagModule.class);
	}
	
	@Override
	public void applyEffect(LivingEntity target) {
		NekotineCore.MODULES.get(StatusFlagModule.class).addFlag(target, OmniCaptedStatusFlag.get());
	}

	@Override
	public void removeEffect(LivingEntity target) {
		NekotineCore.MODULES.get(StatusFlagModule.class).removeFlag(target, OmniCaptedStatusFlag.get());
	}

}
