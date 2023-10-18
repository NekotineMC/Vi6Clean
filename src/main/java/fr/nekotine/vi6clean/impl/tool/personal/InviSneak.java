package fr.nekotine.vi6clean.impl.tool.personal;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.NekotineCore;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.util.SpatialUtil;
import fr.nekotine.vi6clean.Vi6Main;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.status.flag.InvisibleStatusFlag;
import fr.nekotine.vi6clean.impl.tool.Tool;

public class InviSneak extends Tool{

	private boolean sneaking;
	
	private boolean revealed;
	
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
		if (revealed) {
			var world = Vi6Main.IOC.resolve(Vi6Game.class).getWorld();
			Vi6Sound.INVISNEAK_REVEALED.play(world, loc.getX(), loc.getY(), loc.getZ());
			SpatialUtil.circle2DDensity(loc.getX(), loc.getZ(), InviSneakHandler.DETECTION_BLOCK_RANGE, 5, 0,
					(x, z) -> {
						player.spawnParticle(Particle.FALLING_DUST, x, y, z, 1, 0, 0, 0, 0, Bukkit.createBlockData(Material.REDSTONE_BLOCK));
					});
		}else {
			SpatialUtil.circle2DDensity(loc.getX(), loc.getZ(), InviSneakHandler.DETECTION_BLOCK_RANGE, 5, 0,
					(x, z) -> {
						player.spawnParticle(Particle.SMOKE_NORMAL, x, y, z, 1, 0, 0, 0, 0, null);
					});
		}
	}
	
	private void statusUpdate() {
		var flagModule = NekotineCore.MODULES.get(StatusFlagModule.class);
		if (sneaking) {
			if (revealed) {
				setItemStack(InviSneakHandler.REVEALED_ITEM);
				flagModule.removeFlag(getOwner(), InvisibleStatusFlag.get());
			}else {
				setItemStack(InviSneakHandler.INVISIBLE_ITEM);
				flagModule.addFlag(getOwner(), InvisibleStatusFlag.get());
			}
		}else {
			setItemStack(InviSneakHandler.VISIBLE_ITEM);
			flagModule.removeFlag(getOwner(), InvisibleStatusFlag.get());
		}
	}

	@Override
	protected void cleanup() {
		var flagModule = NekotineCore.MODULES.get(StatusFlagModule.class);
		flagModule.removeFlag(getOwner(), InvisibleStatusFlag.get());
	}
}
