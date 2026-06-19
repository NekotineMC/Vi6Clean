package fr.nekotine.vi6clean.impl.status.flag;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.status.flag.StatusFlag;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.EventUtil;

public class SuffocatingStatusFlag implements StatusFlag, Listener {

	private final Set<LivingEntity> suffocating = new HashSet<>();
	private static SuffocatingStatusFlag instance;
	private final PotionEffect darknessEffect = new PotionEffect(PotionEffectType.DARKNESS, -1, 0, false, false, false);
	private int damageTickCount = 0;

	private final int DEPLETION_SPEED;
	private final int DAMAGE_INTERVAL;
	private final double DAMAGE_AMOUNT;

	private SuffocatingStatusFlag() {
		EventUtil.register(this);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
		var config = Ioc.resolve(JavaPlugin.class).getConfig();
		DEPLETION_SPEED = config.getInt("suffocation.depletion_speed", 8);
		DAMAGE_INTERVAL = config.getInt("suffocation.damage_interval", 20);
		DAMAGE_AMOUNT = config.getDouble("suffocation.damage_amount", 1.0);
	}

	public static SuffocatingStatusFlag get() {
		if (instance == null) {
			instance = new SuffocatingStatusFlag();
		}
		return instance;
	}

	@Override
	public void applyStatus(LivingEntity appliedTo) {
		suffocating.add(appliedTo);
		appliedTo.setRemainingAir(appliedTo.getRemainingAir() - 1);
		appliedTo.addPotionEffect(darknessEffect);

	}

	@Override
	public void removeStatus(LivingEntity appliedTo) {
		suffocating.remove(appliedTo);
		appliedTo.removePotionEffect(PotionEffectType.DARKNESS);
		// appliedTo.setRemainingAir(appliedTo.getMaximumAir());
	}

	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		damageTickCount++;
		var it = suffocating.iterator();
		var statusFlagModule = Ioc.resolve(StatusFlagModule.class);
		while (it.hasNext()) {
			var entry = it.next();
			if (entry instanceof Player player && !player.isOnline()) {
				it.remove();
				continue;
			}
			if (!statusFlagModule.hasAny(entry, this)) {
				it.remove();
				entry.removePotionEffect(PotionEffectType.DARKNESS);
				continue;
			}
			// Deplete air
			int newAir = Math.max(-20, entry.getRemainingAir() - DEPLETION_SPEED);
			entry.setRemainingAir(newAir);

			// Damage if out of air
			if (newAir <= 0 && damageTickCount % DAMAGE_INTERVAL == 0) {
				entry.damage(DAMAGE_AMOUNT, DamageSource.builder(DamageType.DROWN).build());
				damageTickCount = 0;
			}
		}
	}

	@EventHandler
	private void onPlayerQuit(PlayerQuitEvent evt) {
		suffocating.remove(evt.getPlayer());
	}
}
