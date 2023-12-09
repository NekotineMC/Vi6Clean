package fr.nekotine.vi6clean.impl.tool.personal.invisneak;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.status.effect.StatusEffect;
import fr.nekotine.core.status.effect.StatusEffectModule;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.util.SpatialUtil;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.status.effect.InvisibleStatusEffectType;
import fr.nekotine.vi6clean.impl.status.flag.InvisibleStatusFlag;
import fr.nekotine.vi6clean.impl.tool.Tool;

public class InviSneak extends Tool{

	private boolean sneaking;
	
	private boolean revealed;
	
	private final StatusEffect invisibleEffect = new StatusEffect(InvisibleStatusEffectType.get(), -1);
	
	@Override
	protected ItemStack makeInitialItemStack() {
		return InviSneakHandler.VISIBLE_ITEM;
	}

	public boolean isSneaking() {
		return sneaking;
	}

	public void setSneaking(boolean sneaking) {
		if (this.sneaking != sneaking) {
			this.sneaking = sneaking;
			statusUpdate();
		}
	}

	public boolean isRevealed() {
		return revealed;
	}

	public void setRevealed(boolean revealed) {
		if (this.revealed != revealed) {
			this.revealed = revealed;
			statusUpdate();
		}
	}
	
	public void lowTick() {
		if (!sneaking) {
			return;
		}
		var player = getOwner();
		if (player == null) {
			return;
		}
		var loc = player.getLocation();
		var y = loc.getZ();
		var x = loc.getX();
		var z = loc.getZ();
		if (revealed) {
			var world = Ioc.resolve(Vi6Game.class).getWorld();
			Vi6Sound.INVISNEAK_REVEALED.play(world, loc.getX(), loc.getY(), loc.getZ());
			SpatialUtil.circle2DDensity(InviSneakHandler.DETECTION_BLOCK_RANGE, 5, 0,
					(offsetX, offsetZ) -> {
						player.spawnParticle(Particle.FALLING_DUST, x + offsetX, y, z + offsetZ, 1, 0, 0, 0, 0, Bukkit.createBlockData(Material.REDSTONE_BLOCK));
					});
		}else {
			SpatialUtil.circle2DDensity(InviSneakHandler.DETECTION_BLOCK_RANGE, 5, 0,
					(offsetX, offsetZ) -> {
						player.spawnParticle(Particle.SMOKE_NORMAL, x + offsetX, y, z + offsetZ, 1, 0, 0, 0, 0, null);
					});
		}
	}
	
	private void statusUpdate() {
		var statusEffectModule = Ioc.resolve(StatusEffectModule.class);
		if (sneaking) {
			if (revealed) {
				setItemStack(InviSneakHandler.REVEALED_ITEM);
				statusEffectModule.removeEffect(getOwner(), invisibleEffect);
			}else {
				setItemStack(InviSneakHandler.INVISIBLE_ITEM);
				statusEffectModule.addEffect(getOwner(), invisibleEffect);
			}
		}else {
			setItemStack(InviSneakHandler.VISIBLE_ITEM);
			statusEffectModule.removeEffect(getOwner(), invisibleEffect);
		}
	}

	@Override
	protected void cleanup() {
		var flagModule = Ioc.resolve(StatusFlagModule.class);
		flagModule.removeFlag(getOwner(), InvisibleStatusFlag.get());
	}

	//

	@Override
	protected void onEmpStart() {
	}
	@Override
	protected void onEmpEnd() {
	}
}
