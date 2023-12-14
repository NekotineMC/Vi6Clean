package fr.nekotine.vi6clean.impl.tool.personal.tazer;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.status.effect.StatusEffect;
import fr.nekotine.core.status.effect.StatusEffectModule;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.util.EntityUtil;
import fr.nekotine.core.util.SpatialUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.impl.status.effect.TazedStatusEffectType;
import fr.nekotine.vi6clean.impl.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;

public class Tazer extends Tool{
	
	private static final StatusEffect tazedEffect = new StatusEffect(TazedStatusEffectType.get(), 30);//1.5sec
	
	private int cooldown;
	
	//private int friendlyHits;
	
	@Override
	protected ItemStack makeInitialItemStack() {
		return TazerHandler.ITEM;
	}
	
	@Override
	protected void cleanup() {
	}

	public boolean shot() {
		var player = getOwner();
		if (player == null) {
			return false;
		}
		if (cooldown > 0) {
			return false;
		}
		var statusFlagModule = Ioc.resolve(StatusFlagModule.class);
		if(statusFlagModule.hasAny(getOwner(), EmpStatusFlag.get())) {
			return false;
		}
		var optWrap = Ioc.resolve(WrappingModule.class).getWrapperOptional(player, PlayerWrapper.class);
		if (optWrap.isEmpty()) {
			return false;
		}
		var eyeLoc = player.getEyeLocation();
		var eyeDir = eyeLoc.getDirection();
		var world = player.getWorld();
		var range = 100d;
		var trace = world.rayTrace(eyeLoc, eyeDir, range, FluidCollisionMode.NEVER, true,1.0, e -> !e.equals(player) && e instanceof LivingEntity);
		if (trace == null) {
			return true; // No hit, le joueur à tiré en l'air
		}
		var hite = trace.getHitEntity();
		if (hite != null && hite instanceof LivingEntity hit) {
			EntityUtil.fakeDamage(hit);
			if (optWrap.get().ennemiTeamInMap().anyMatch(e -> e.equals(hit))) {
				Ioc.resolve(StatusEffectModule.class).addEffect(hit, tazedEffect);
			}
		}
		var hitp = trace.getHitPosition();
		if (hitp != null) {
			range = hitp.distance(eyeLoc.toVector());
		}
		SpatialUtil.line3DFromDir(eyeLoc.toVector(), eyeLoc.getDirection(), range, 4,
				(vec) -> world.spawnParticle(Particle.FIREWORKS_SPARK, vec.getX(), vec.getY(), vec.getZ(), 0,
						0, 0, 0, 0f));
		cooldown = TazerHandler.COOLDOWN_TICK;
		player.setCooldown(getItemStack().getType(), cooldown);
		return true;
	}
	
	public void updateCooldown() {
		if (cooldown > 0) {
			cooldown--;
		}
	}

	//

	@Override
	protected void onEmpStart() {
		setItemStack(TazerHandler.EMP_ITEM);
	}
	@Override
	protected void onEmpEnd() {
		setItemStack(TazerHandler.EMP_ITEM);
	}
}
