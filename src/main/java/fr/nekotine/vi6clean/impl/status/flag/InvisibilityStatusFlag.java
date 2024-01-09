package fr.nekotine.vi6clean.impl.status.flag;

import java.util.HashMap;

import org.bukkit.Material;
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
import fr.nekotine.core.status.flag.StatusFlag;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.tuple.Pair;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.impl.status.InvisibilityType;
import fr.nekotine.vi6clean.impl.status.effect.invisibility.InvisibilityStatusEffectType;
import fr.nekotine.vi6clean.impl.status.effect.invisibility.SilentInvisibilityStatusEffectType;
import fr.nekotine.vi6clean.impl.status.effect.invisibility.TrueInvisibilityStatusEffectType;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;

public class InvisibilityStatusFlag implements StatusFlag, Listener{

	private static final PotionEffect invisibleEffect = new PotionEffect(PotionEffectType.INVISIBILITY, -1, 0, false, false, true);
	private static InvisibilityStatusFlag instance;
	private HashMap<LivingEntity, Pair<InvisibilityType,Double>> invType = new HashMap<LivingEntity, Pair<InvisibilityType,Double>>();
	
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
		invType.remove(appliedTo);
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
	
	public void updateType(LivingEntity appliedTo) {
		var sModule = Ioc.resolve(StatusEffectModule.class);
		var fModule = Ioc.resolve(StatusFlagModule.class);
		if(!fModule.hasAny(appliedTo, this)) {
			System.out.println("NEW EFFECT");
			fModule.addFlag(appliedTo, this);
		}
		if(sModule.hasEffect(appliedTo, TrueInvisibilityStatusEffectType.get())) {
			invType.put(appliedTo, Pair.from(InvisibilityType.True,0d));
			System.out.println("TRUE");
			return;
		}
		if(sModule.hasEffect(appliedTo, SilentInvisibilityStatusEffectType.get())) {
			invType.put(appliedTo, Pair.from(InvisibilityType.Silent,0d));
			System.out.println("SILENT");
			return;
		}
		if(sModule.hasEffect(appliedTo, InvisibilityStatusEffectType.get())) {
			invType.put(appliedTo, Pair.from(InvisibilityType.Default,0d));
			System.out.println("DEFAULT");
			return;
		}
		System.out.println("REMOVE");
		fModule.removeFlag(appliedTo, this);
	}
	
	//
	
	@EventHandler
	public void onPlayerMove(PlayerMoveEvent evt) {
		if(!evt.hasChangedPosition()) {
			return;
		}
		var evtP = evt.getPlayer();
		if(!invType.containsKey(evtP) || invType.get(evtP).a()==InvisibilityType.True) {
			return;
		}
		var val = invType.get(evtP);
		var d = val.b();
		var toAdd = evt.getFrom().distanceSquared(evt.getTo());
		if(d+toAdd > 0.14) {
			evt.getTo().getWorld().spawnParticle(Particle.BLOCK_CRACK, evt.getTo(), 5, 0, 0, 0, 1, Material.SANDSTONE.createBlockData());
			invType.put(evtP, Pair.from(val.a(),0d));
		}else {
			invType.put(evtP, Pair.from(val.a(),d+toAdd));
		}
		
	}
}
