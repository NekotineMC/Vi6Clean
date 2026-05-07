package fr.nekotine.vi6clean.impl.status.flag;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import fr.nekotine.core.damage.DamageModule;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.status.flag.StatusFlag;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.EventUtil;

public class SuffocatingStatusFlag implements StatusFlag, Listener {

	private static SuffocatingStatusFlag instance;

	private final Map<Player, Integer> suffocating = new HashMap<>();

	private final PotionEffect darknessEffect = new PotionEffect(PotionEffectType.DARKNESS, -1, 0, false, false, false);

	private int damageTickCount = 0;

	private final int depletionSpeed;

	private final int damageInterval;

	private final double damageAmount;

	private SuffocatingStatusFlag() {
		EventUtil.register(this);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
		var config = Ioc.resolve(JavaPlugin.class).getConfig();
		depletionSpeed = config.getInt("suffocation.depletion_speed", 3);
		damageInterval = config.getInt("suffocation.damage_interval", 20);
		damageAmount = config.getDouble("suffocation.damage_amount", 1.0);
	}

	public static SuffocatingStatusFlag get() {
		if (instance == null) {
			instance = new SuffocatingStatusFlag();
		}
		return instance;
	}

	public Set<Player> getSuffocatingPlayers() {
		return suffocating.keySet();
	}

	@Override
	public void applyStatus(LivingEntity appliedTo) {
		if (appliedTo instanceof Player player) {
			// Force air slightly below max to ensure bar shows up if possible
			int startAir = Math.min(player.getRemainingAir(), 299);
			suffocating.put(player, startAir);
			player.setRemainingAir(startAir);
			player.addPotionEffect(darknessEffect);
		}
	}

	@Override
	public void removeStatus(LivingEntity appliedTo) {
		if (appliedTo instanceof Player player) {
			suffocating.remove(player);
			player.removePotionEffect(PotionEffectType.DARKNESS);
			player.setRemainingAir(300); // Restore air
		}
	}

	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		damageTickCount++;
		var it = suffocating.entrySet().iterator();
		var statusFlagModule = Ioc.resolve(StatusFlagModule.class);
		while (it.hasNext()) {
			var entry = it.next();
			var player = entry.getKey();
			if (!player.isOnline()) {
				it.remove();
				continue;
			}
			if (!statusFlagModule.hasAny(player, this)) {
				it.remove();
				player.removePotionEffect(PotionEffectType.DARKNESS);
				continue;
			}
			// Deplete air
			int currentAir = entry.getValue();
			int newAir = Math.max(-20, currentAir - depletionSpeed);
			entry.setValue(newAir);
			player.setRemainingAir(newAir);

			// Damage if out of air
			if (newAir <= 0 && damageTickCount % damageInterval == 0) {
				Ioc.resolve(DamageModule.class).Damage(player, null, null, DamageCause.DROWNING, damageAmount, true,
						false, null);
			}
		}
	}

	@EventHandler
	private void onPlayerQuit(PlayerQuitEvent evt) {
		suffocating.remove(evt.getPlayer());
	}
}
