package fr.nekotine.vi6clean.impl.status.flag;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.status.flag.StatusFlag;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;

public class InvisibleStatusFlag implements StatusFlag{

	private static final PotionEffect invisibleEffect = new PotionEffect(PotionEffectType.INVISIBILITY, -1, 0, false, false, true);
	
	public static final String getStatusName() {
		return "invisible";
	}
	
	private static InvisibleStatusFlag instance;
	
	public static final InvisibleStatusFlag get() {
		if (instance == null) {
			instance = new InvisibleStatusFlag();
		}
		return instance;
	}
	
	@Override
	public void applyStatus(LivingEntity appliedTo) {
		if (Ioc.resolve(StatusFlagModule.class).hasAny(appliedTo, OmniCaptedStatusFlag.get())) {
			return;
		}
		appliedTo.addPotionEffect(invisibleEffect);
		if (!(appliedTo instanceof Player player)) {
			return;
		}
		var optionalWrap = Ioc.resolve(WrappingModule.class).getWrapperOptional(player, PlayerWrapper.class);
		if (optionalWrap.isEmpty()) {
			return;
		}
		for (var ennemi : optionalWrap.get().ennemiTeam()) {
			ennemi.hideEntity(Ioc.resolve(JavaPlugin.class), player);
		}
	}

	@Override
	public void removeStatus(LivingEntity appliedTo) {
		appliedTo.removePotionEffect(PotionEffectType.INVISIBILITY);
		if (!(appliedTo instanceof Player player)) {
			return;
		}
		var optionalWrap = Ioc.resolve(WrappingModule.class).getWrapperOptional(player, PlayerWrapper.class);
		if (optionalWrap.isEmpty()) {
			return;
		}
		for (var ennemi : optionalWrap.get().ennemiTeam()) {
			ennemi.showEntity(Ioc.resolve(JavaPlugin.class), player);
		}
	}

}
