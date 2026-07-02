package fr.nekotine.vi6clean.impl.status.flag;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.status.flag.StatusFlag;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.EventUtil;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class DiarrheaStatusFlag implements StatusFlag, Listener {

	private static final PotionEffect EFFECT = new PotionEffect(PotionEffectType.NAUSEA, -1, 0, false, false, true);
	private static DiarrheaStatusFlag instance;

	public static final DiarrheaStatusFlag get() {
		if (instance == null) {
			instance = new DiarrheaStatusFlag();
		}
		return instance;
	}

	public DiarrheaStatusFlag() {
		EventUtil.register(this);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
	}

	//

	@Override
	public void applyStatus(LivingEntity appliedTo) {
		appliedTo.addPotionEffect(EFFECT);
	}

	@Override
	public void removeStatus(LivingEntity appliedTo) {
		appliedTo.removePotionEffect(PotionEffectType.NAUSEA);
	}

	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		var statusModule = Ioc.resolve(StatusFlagModule.class);
		for (var player : Bukkit.getOnlinePlayers()) {
			if (!statusModule.hasAny(player, this)) {
				continue;
			}
			player.getWorld().spawnParticle(Particle.DRAGON_BREATH, player.getLocation(), 2, .1, 0, .1, 0, 0f);
		}
	}
}
