package fr.nekotine.vi6clean.impl.tool.personal.sonar;

import org.bukkit.Particle;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.util.SpatialUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.impl.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;

public class Sonar extends Tool{
	
	@Override
	protected ItemStack makeInitialItemStack() {
		return Ioc.resolve(SonarHandler.class).getItem();
	}

	public void pulse() {
		var player = getOwner();
		if (player == null) {
			return;
		}
		var statusFlagModule = Ioc.resolve(StatusFlagModule.class);
		if(statusFlagModule.hasAny(getOwner(), EmpStatusFlag.get())) {
			return;
		}
		var opt = Ioc.resolve(WrappingModule.class).getWrapperOptional(getOwner(), PlayerWrapper.class);
		if (opt.isEmpty()) {
			return;
		}
		var loc = player.getLocation();
		var x = loc.getX();
		var y = loc.getY() + 0.1;
		var z = loc.getZ();
		var handler = Ioc.resolve(SonarHandler.class);
		if (opt.get().ennemiTeamInMap().anyMatch(e -> player.getLocation().distanceSquared(e.getLocation()) <= handler.getDetectionBlockRangeSquared())) {
			Vi6Sound.SONAR_POSITIVE.play(player.getLocation().getWorld(), player.getLocation());
			SpatialUtil.circle2DDensity(handler.getDetectionBlockRange(), 5, 0,
					(offsetX, offsetZ) -> {
						player.spawnParticle(Particle.CRIT, x + offsetX, y, z + offsetZ, 1, 0, 0, 0, 0, null);
					});
			return;
		}
		Vi6Sound.SONAR_NEGATIVE.play(player);
		SpatialUtil.circle2DDensity(handler.getDetectionBlockRange(), 5, 0,
				(offsetX, offsetZ) -> {
					player.spawnParticle(Particle.CRIT_MAGIC, x + offsetX, y, z + offsetZ, 1, 0, 0, 0, 0, null);
				});
		player.setCooldown(getItemStack().getType(), handler.getDelayTick());
	}
	
	@Override
	protected void cleanup() {
	}

	//

	@Override
	protected void onEmpStart() {
		setItemStack(Ioc.resolve(SonarHandler.class).getEmpItem());
	}
	@Override
	protected void onEmpEnd() {
		setItemStack(Ioc.resolve(SonarHandler.class).getItem());
	}
}
