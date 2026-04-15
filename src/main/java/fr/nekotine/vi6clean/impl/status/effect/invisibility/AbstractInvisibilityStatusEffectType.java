package fr.nekotine.vi6clean.impl.status.effect.invisibility;

import fr.nekotine.core.status.effect.StatusEffectType;
import fr.nekotine.vi6clean.impl.status.flag.InvisibilityStatusFlag;
import org.bukkit.entity.LivingEntity;

public abstract class AbstractInvisibilityStatusEffectType implements StatusEffectType {
	@Override
	public void applyEffect(LivingEntity target) {
		InvisibilityStatusFlag.get().addFlag(target);
	}

	@Override
	public void removeEffect(LivingEntity target) {
		InvisibilityStatusFlag.get().removeFlag(target);
	}
}
