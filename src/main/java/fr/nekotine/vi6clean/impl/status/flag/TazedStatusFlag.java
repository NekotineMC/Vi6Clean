package fr.nekotine.vi6clean.impl.status.flag;

import java.util.Collection;
import java.util.LinkedList;

import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.status.flag.StatusFlag;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.EntityUtil;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.vi6clean.constant.Vi6Sound;

public class TazedStatusFlag implements StatusFlag, Listener{

	private Collection<LivingEntity> tazed = new LinkedList<>();
	
	public static final String getStatusName() {
		return "tazé";
	}
	
	private static TazedStatusFlag instance;
	
	public static final TazedStatusFlag get() {
		if (instance == null) {
			instance = new TazedStatusFlag();
		}
		return instance;
	}
	
	private TazedStatusFlag() {
		EventUtil.register(this);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
	}
	
	@Override
	public void applyStatus(LivingEntity appliedTo) {
		tazed.add(appliedTo);
	}

	@Override
	public void removeStatus(LivingEntity appliedTo) {
		tazed.remove(appliedTo);
	}
	
	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		for (var e : tazed) {
			if (e instanceof Player player) {
				EntityUtil.fakeDamage(e,player);
			}
			e.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, e.getLocation().add(0, 0.2, 0), 10, 0, 0.6, 0);
			Vi6Sound.TAZER_SHOCKING.play(e.getWorld(),e);
		}
	}
	
	@EventHandler
	private void onPlayerMove(PlayerMoveEvent evt) {
		if (tazed.contains(evt.getPlayer())) {
			evt.setCancelled(true);
		}
	}

}
