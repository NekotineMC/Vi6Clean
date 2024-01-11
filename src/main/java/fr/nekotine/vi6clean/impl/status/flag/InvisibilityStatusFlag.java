package fr.nekotine.vi6clean.impl.status.flag;

import javax.annotation.Nullable;

import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.status.effect.StatusEffectModule;
import fr.nekotine.core.status.effect.StatusEffectType;
import fr.nekotine.core.status.flag.StatusFlag;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.impl.status.effect.invisibility.InvisibilityStatusEffectType;
import fr.nekotine.vi6clean.impl.status.effect.invisibility.SilentInvisibilityStatusEffectType;
import fr.nekotine.vi6clean.impl.status.effect.invisibility.TrueInvisibilityStatusEffectType;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;

public class InvisibilityStatusFlag implements StatusFlag, Listener{
	private static final PotionEffect invisibleEffect = new PotionEffect(PotionEffectType.INVISIBILITY, -1, 0, false, false, true);
	private static InvisibilityStatusFlag instance;

	public static final String getStatusName() {
		return "invisible";
	}
	public static final InvisibilityStatusFlag get() {
		if (instance == null) {
			instance = new InvisibilityStatusFlag();
		}
		return instance;
	}
	
	//
	
	private final double STEP_DISTANCE = Ioc.resolve(JavaPlugin.class).getConfig().getDouble("invisibility.step_distance",0.75);
	private final double SQUARED_STEP_DISTANCE = STEP_DISTANCE * STEP_DISTANCE;
	private final int PARTICLE_COUNT = Ioc.resolve(JavaPlugin.class).getConfig().getInt("invisibility.particle_count",2);
	public InvisibilityStatusFlag() {
		EventUtil.register(this);
	}
	
	//
	
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
	
	//
	
	public void addFlag(LivingEntity target) {
		var fModule = Ioc.resolve(StatusFlagModule.class);
		if(!fModule.hasAny(target, this)) {
			fModule.addFlag(target, this);
		}
	}
	public void removeFlag(LivingEntity target) {
		if(getType(target)==null) {
			Ioc.resolve(StatusFlagModule.class).removeFlag(target, this);
		}
	}
	public @Nullable StatusEffectType getType(LivingEntity appliedTo) {
		var sModule = Ioc.resolve(StatusEffectModule.class);
		if(sModule.hasEffect(appliedTo, TrueInvisibilityStatusEffectType.get())) {
			return TrueInvisibilityStatusEffectType.get();
		}
		if(sModule.hasEffect(appliedTo, SilentInvisibilityStatusEffectType.get())) {
			return SilentInvisibilityStatusEffectType.get();
		}
		if(sModule.hasEffect(appliedTo, InvisibilityStatusEffectType.get())) {
			return InvisibilityStatusEffectType.get();
		}
		return null;
	}
	
	//
	
	@EventHandler
	public void onPlayerMove(PlayerMoveEvent evt) {
		if(!evt.hasChangedPosition()) {
			return;
		}
		var evtP = evt.getPlayer();
		if(!Ioc.resolve(StatusFlagModule.class).hasAny(evtP, this)) {
			return;
		}
		var type = getType(evtP);
		if(TrueInvisibilityStatusEffectType.get().equals(type)) {
			return;
		}
		var wrapper = Ioc.resolve(WrappingModule.class).getWrapper(evtP, PlayerWrapper.class);
		var distance = wrapper.getSquaredWalkedDistance() + evt.getFrom().distanceSquared(evt.getTo());
		var block_under = evt.getTo().clone().subtract(0, 0.1, 0).getBlock();
		if(block_under.isSolid() && distance >= SQUARED_STEP_DISTANCE) {
			distance = 0;
			var pLoc = evt.getTo().clone().add(0, 0.2, 0);
			var sound = InvisibilityStatusEffectType.get().equals(type);
			for(Player enemy : wrapper.ennemiTeam()) {
				enemy.spawnParticle(
						Particle.BLOCK_CRACK,
						pLoc,
						PARTICLE_COUNT, 
						0, 0, 0, 
						0, 
						block_under.getBlockData());
				if(sound) {
					Vi6Sound.INVISIBILITY_WALK.play(enemy, pLoc);
				}
			}
		}
		wrapper.setSquaredWalkedDistance(distance);
	}
}
