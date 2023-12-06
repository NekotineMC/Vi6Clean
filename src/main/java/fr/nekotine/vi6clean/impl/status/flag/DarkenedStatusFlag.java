package fr.nekotine.vi6clean.impl.status.flag;

import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import fr.nekotine.core.status.flag.StatusFlag;

public class DarkenedStatusFlag implements StatusFlag{
	private static final PotionEffect darknessEffect = new PotionEffect(PotionEffectType.DARKNESS, -1, 0, false, false, true);
	private static final PotionEffect nightVisionEffect = new PotionEffect(PotionEffectType.NIGHT_VISION, -1, 0, false, false, true);
	private static DarkenedStatusFlag instance;
	public static final String getStatusName() {
		return "darkened";
	}
	public static final DarkenedStatusFlag get() {
		if (instance == null) {
			instance = new DarkenedStatusFlag();
		}
		return instance;
	}
	
	//
	
	@Override
	public void applyStatus(LivingEntity appliedTo) {
		appliedTo.addPotionEffect(darknessEffect);
		appliedTo.addPotionEffect(nightVisionEffect);
	}
	@Override
	public void removeStatus(LivingEntity appliedTo) {
		appliedTo.removePotionEffect(PotionEffectType.DARKNESS);
		appliedTo.removePotionEffect(PotionEffectType.NIGHT_VISION);
	}
}
