package fr.nekotine.vi6clean.impl.status.effect;

import org.bukkit.entity.LivingEntity;

import fr.nekotine.core.NekotineCore;
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
		NekotineCore.MODULES.tryLoad(StatusFlagModule.class);
	}
	
	@Override
	public void applyEffect(LivingEntity target) {
		NekotineCore.MODULES.get(StatusFlagModule.class).addFlag(target, TazedStatusFlag.get());
	}

	@Override
	public void removeEffect(LivingEntity target) {
		NekotineCore.MODULES.get(StatusFlagModule.class).removeFlag(target, TazedStatusFlag.get());
	}

}
