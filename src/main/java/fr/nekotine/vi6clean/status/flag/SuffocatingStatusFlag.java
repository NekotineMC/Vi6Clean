package fr.nekotine.vi6clean.status.flag;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.status.flag.StatusFlag;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.vi6clean.configuration.ConfigManager;
import io.papermc.paper.util.Tick;

public class SuffocatingStatusFlag implements StatusFlag, Listener {

	private ConfigManager configuration = Ioc.resolve(ConfigManager.class);

	private final Set<LivingEntity> suffocating = new HashSet<>();
	private static SuffocatingStatusFlag instance;
	private final PotionEffect darknessEffect = new PotionEffect(PotionEffectType.DARKNESS, -1, 0, false, false, false);
	private int damageTickCount = 0;

	private SuffocatingStatusFlag() {
		EventUtil.register(this);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
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
		var it = suffocating.iterator();
		var statusFlagModule = Ioc.resolve(StatusFlagModule.class);

		var depletionSpeed = configuration.getConfig().game().mechanics().suffocation().airTicksRemovedPerTicks();
		var damageAmount = configuration.getConfig().game().mechanics().suffocation().damageAmount();

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
			int newAir = Math.max(-20, entry.getRemainingAir() - depletionSpeed);
			entry.setRemainingAir(newAir);

			// Damage if out of air
			if (newAir <= 0 && damageTickCount == 0) {
				entry.damage(damageAmount, DamageSource.builder(DamageType.DROWN).build());
			}
		}
		if (++damageTickCount > Tick.tick()
				.fromDuration(configuration.getConfig().game().mechanics().suffocation().damageInterval())) {
			damageTickCount = 0;
		}
	}

	@EventHandler
	private void onPlayerQuit(PlayerQuitEvent evt) {
		suffocating.remove(evt.getPlayer());
	}
}
