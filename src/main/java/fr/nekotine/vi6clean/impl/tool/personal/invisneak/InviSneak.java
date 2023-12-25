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
	
	private boolean emp;
	
	private final StatusEffect invisibleEffect = new StatusEffect(InvisibleStatusEffectType.get(), -1);
	
	@Override
	protected ItemStack makeInitialItemStack() {
		return Ioc.resolve(InviSneakHandler.class).getVisibleItem();
	}

	public boolean isSneaking() {
		return sneaking;
	}

	public void setSneaking(boolean sneaking) {
		if (this.sneaking != sneaking && !emp) {
			this.sneaking = sneaking;
			statusUpdate();
		}
	}

	public boolean isRevealed() {
		return revealed;
	}

	public void setRevealed(boolean revealed) {
		if (this.revealed != revealed && !emp) {
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
		var range = Ioc.resolve(InviSneakHandler.class).getDetectionBlockRange();
		if (revealed) {
			var world = Ioc.resolve(Vi6Game.class).getWorld();
			Vi6Sound.INVISNEAK_REVEALED.play(world, loc.getX(), loc.getY(), loc.getZ());
			SpatialUtil.circle2DDensity(range, 5, 0,
					(offsetX, offsetZ) -> {
						player.spawnParticle(Particle.FALLING_DUST, x + offsetX, y, z + offsetZ, 1, 0, 0, 0, 0, Bukkit.createBlockData(Material.REDSTONE_BLOCK));
					});
		}else {
			SpatialUtil.circle2DDensity(range, 5, 0,
					(offsetX, offsetZ) -> {
						player.spawnParticle(Particle.SMOKE_NORMAL, x + offsetX, y, z + offsetZ, 1, 0, 0, 0, 0, null);
					});
		}
	}
	
	private void statusUpdate() {
		var statusEffectModule = Ioc.resolve(StatusEffectModule.class);
		if (sneaking) {
			if (revealed) {
				setItemStack(Ioc.resolve(InviSneakHandler.class).getRevealedItem());
				statusEffectModule.removeEffect(getOwner(), invisibleEffect);
			}else {
				setItemStack(Ioc.resolve(InviSneakHandler.class).getInvisibleItem());
				statusEffectModule.addEffect(getOwner(), invisibleEffect);
			}
		}else {
			setItemStack(Ioc.resolve(InviSneakHandler.class).getVisibleItem());
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
		setRevealed(true);
		emp = true;
	}
	@Override
	protected void onEmpEnd() {
		emp = false;
	}
}
