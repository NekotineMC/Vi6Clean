package fr.nekotine.vi6clean.impl.status.flag;

import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import fr.nekotine.core.status.flag.StatusFlag;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpEndEvent;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpStartEvent;

public class EmpStatusFlag implements StatusFlag{
	
	private static EmpStatusFlag instance;
	
	private static PotionEffect potionEffect = new PotionEffect(PotionEffectType.OOZING, -1, 0, false, false, true);
	
	public static final EmpStatusFlag get() {
		if (instance == null) {
			instance = new EmpStatusFlag();
		}
		return instance;
	}
	
	//
	
	@Override
	public void applyStatus(LivingEntity appliedTo) {
		EventUtil.call(new EntityEmpStartEvent(appliedTo));
		appliedTo.addPotionEffect(potionEffect);
	}
	@Override
	public void removeStatus(LivingEntity appliedTo) {
		appliedTo.removePotionEffect(PotionEffectType.OOZING);
		EventUtil.call(new EntityEmpEndEvent(appliedTo));
	}
}
