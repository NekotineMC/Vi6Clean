package fr.nekotine.vi6clean.impl.tool.personal;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.NekotineCore;
import fr.nekotine.core.status.effect.StatusEffect;
import fr.nekotine.core.status.effect.StatusEffectModule;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.core.util.SpatialUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.impl.status.effect.TazedStatusEffectType;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class Tazer extends Tool{
	
	private static final StatusEffect tazedEffect = new StatusEffect(TazedStatusEffectType.get(), 30);//1.5sec
	
	private int cooldown;
	
	//private int friendlyHits;
	
	@Override
	protected ItemStack makeInitialItemStack() {
		return ItemStackUtil.make(Material.SHEARS, Component.text("Tazer", NamedTextColor.GOLD), TazerHandler.LORE);
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
		var optWrap = NekotineCore.MODULES.get(WrappingModule.class).getWrapperOptional(player, PlayerWrapper.class);
		if (optWrap.isEmpty()) {
			return false;
		}
		var eyeLoc = player.getEyeLocation();
		var eyeDir = eyeLoc.getDirection();
		var world = player.getWorld();
		var range = 100d;
		var trace = world.rayTrace(eyeLoc, eyeDir, range, 1.1, e -> !e.equals(player) && e instanceof LivingEntity);
		if (trace != null && trace.getHitEntity() != null) {
			var hit = (LivingEntity)trace.getHitEntity();
			range = hit.getLocation().distance(eyeLoc);
			NekotineCore.MODULES.get(StatusEffectModule.class).addEffect(hit, tazedEffect);
			if (optWrap.get().ennemiTeamInMap().anyMatch(e -> e.equals(hit))) {
				//NekotineCore.MODULES.get(StatusEffectModule.class).addEffect(hit, tazedEffect);
			}
		}
		SpatialUtil.line3DFromDir(eyeLoc.toVector(), eyeLoc.getDirection(), range, 0.5,
				(vec) -> world.spawnParticle(Particle.FIREWORKS_SPARK, vec.getX(), vec.getY(), vec.getZ(), 0,
						eyeDir.getX(), eyeDir.getY(), eyeDir.getZ(), 1f));
		cooldown = TazerHandler.COOLDOWN_TICK;
		player.setCooldown(getItemStack().getType(), cooldown);
		return true;
	}
	
	public void updateCooldown() {
		if (cooldown > 0) {
			cooldown--;
		}
	}
	
}
