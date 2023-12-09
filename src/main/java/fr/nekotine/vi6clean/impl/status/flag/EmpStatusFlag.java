package fr.nekotine.vi6clean.impl.status.flag;

import org.bukkit.entity.LivingEntity;

import fr.nekotine.core.status.flag.StatusFlag;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpEndEvent;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpStartEvent;

public class EmpStatusFlag implements StatusFlag{
	private static EmpStatusFlag instance;
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
	}
	@Override
	public void removeStatus(LivingEntity appliedTo) {
		EventUtil.call(new EntityEmpEndEvent(appliedTo));
	}
}
