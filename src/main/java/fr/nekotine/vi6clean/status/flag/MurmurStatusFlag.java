package fr.nekotine.vi6clean.status.flag;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.status.flag.StatusFlag;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.vi6clean.status.event.EntityMurmurEndEvent;
import fr.nekotine.vi6clean.status.event.EntityMurmurStartEvent;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityAirChangeEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class MurmurStatusFlag implements StatusFlag, Listener {

	private static MurmurStatusFlag instance;
	private static final PotionEffect effect = new PotionEffect(PotionEffectType.BAD_OMEN, -1, 0, false, false);

	public MurmurStatusFlag() {
		EventUtil.register(this);
	}

	public static final MurmurStatusFlag get() {
		if (instance == null) {
			instance = new MurmurStatusFlag();
		}
		return instance;
	}

	//

	@Override
	public void applyStatus(LivingEntity appliedTo) {
		appliedTo.addPotionEffect(effect);
		EventUtil.call(new EntityMurmurStartEvent(appliedTo));
	}

	@Override
	public void removeStatus(LivingEntity appliedTo) {
		appliedTo.removePotionEffect(PotionEffectType.BAD_OMEN);
		EventUtil.call(new EntityMurmurEndEvent(appliedTo));
	}

	@EventHandler
	private void onAirChange(EntityAirChangeEvent evt) {
		if (!(evt.getEntity() instanceof LivingEntity entity)
				|| !Ioc.resolve(StatusFlagModule.class).hasAny(entity, this)) {
			return;
		}
		if (entity.getRemainingAir() < evt.getAmount()) {
			evt.setCancelled(true);
		}
	}
}
