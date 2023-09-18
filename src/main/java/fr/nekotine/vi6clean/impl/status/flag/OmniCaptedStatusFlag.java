package fr.nekotine.vi6clean.impl.status.flag;

import java.time.Duration;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import fr.nekotine.core.NekotineCore;
import fr.nekotine.core.glow.EntityGlowModule;
import fr.nekotine.core.status.flag.StatusFlag;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import io.papermc.paper.util.Tick;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;

public class OmniCaptedStatusFlag implements StatusFlag{
	
	private static final Title title = Title.title(Component.empty(), Component.text(getStatusName(), NamedTextColor.RED),
			Title.Times.times(Tick.of(2), Duration.ofDays(1000), Tick.of(2)));
	
	public static final String getStatusName() {
		return "omni-capté";
	}
	
	private static OmniCaptedStatusFlag instance;
	
	public static final OmniCaptedStatusFlag get() {
		if (instance == null) {
			instance = new OmniCaptedStatusFlag();
		}
		return instance;
	}
	
	@Override
	public void applyStatus(LivingEntity appliedTo) {
		if (!(appliedTo instanceof Player player)) {
			return;
		}
		var optionalWrap = NekotineCore.MODULES.get(WrappingModule.class).getWrapperOptional(player, PlayerWrapper.class);
		if (optionalWrap.isEmpty()) {
			return;
		}
		var glowModule = NekotineCore.MODULES.get(EntityGlowModule.class);
		for (var ennemi : optionalWrap.get().ennemiTeam()) {
			glowModule.glowEntityFor(appliedTo, ennemi);
		}
		player.showTitle(title);
		if (NekotineCore.MODULES.get(StatusFlagModule.class).hasAny(appliedTo, InvisibleStatusFlag.get())) {
			InvisibleStatusFlag.get().removeStatus(appliedTo); // Remove status without removing flag
		}
	}

	@Override
	public void removeStatus(LivingEntity appliedTo) {
		if (!(appliedTo instanceof Player player)) {
			return;
		}
		var optionalWrap = NekotineCore.MODULES.get(WrappingModule.class).getWrapperOptional(player, PlayerWrapper.class);
		if (optionalWrap.isEmpty()) {
			return;
		}
		var glowModule = NekotineCore.MODULES.get(EntityGlowModule.class);
		for (var ennemi : optionalWrap.get().ennemiTeam()) {
			glowModule.unglowEntityFor(appliedTo, ennemi);
		}
		player.clearTitle();
		if (NekotineCore.MODULES.get(StatusFlagModule.class).hasAny(appliedTo, InvisibleStatusFlag.get())) {
			InvisibleStatusFlag.get().applyStatus(appliedTo); // Add status without reset flag
		}
	}

}
