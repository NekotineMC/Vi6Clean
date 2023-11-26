package fr.nekotine.vi6clean.impl.tool.personal.sonar;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.core.util.SpatialUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class Sonar extends Tool{
	
	@Override
	protected ItemStack makeInitialItemStack() {
		return ItemStackUtil.make(Material.TARGET, Component.text("Sonar", NamedTextColor.GOLD), SonarHandler.LORE);
	}

	public void pulse() {
		var player = getOwner();
		if (player == null) {
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
		if (opt.get().ennemiTeamInMap().anyMatch(e -> player.getLocation().distanceSquared(e.getLocation()) <= SonarHandler.DETECTION_RANGE_SQUARED)) {
			Vi6Sound.SONAR_POSITIVE.play(player.getLocation().getWorld(), player.getLocation());
			SpatialUtil.circle2DDensity(SonarHandler.DETECTION_BLOCK_RANGE, 5, 0,
					(offsetX, offsetZ) -> {
						player.spawnParticle(Particle.CRIT, x + offsetX, y, z + offsetZ, 1, 0, 0, 0, 0, null);
					});
			return;
		}
		Vi6Sound.SONAR_NEGATIVE.play(player);
		SpatialUtil.circle2DDensity(SonarHandler.DETECTION_BLOCK_RANGE, 5, 0,
				(offsetX, offsetZ) -> {
					player.spawnParticle(Particle.CRIT_MAGIC, x + offsetX, y, z + offsetZ, 1, 0, 0, 0, 0, null);
				});
		player.setCooldown(getItemStack().getType(), SonarHandler.DELAY_SECOND*20);
	}
	
	@Override
	protected void cleanup() {
	}

}
