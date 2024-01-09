package fr.nekotine.vi6clean.impl.status.effect.invisibility;

import org.bukkit.entity.LivingEntity;

import fr.nekotine.core.status.effect.StatusEffectType;
import fr.nekotine.vi6clean.impl.status.flag.InvisibilityStatusFlag;

public abstract class AbstractInvisibilityStatusEffectType implements StatusEffectType{
	@Override
	public void applyEffect(LivingEntity target) {
		InvisibilityStatusFlag.get().updateType(target);
	}

	@Override
	public void removeEffect(LivingEntity target) {
		InvisibilityStatusFlag.get().updateType(target);
	}
}
