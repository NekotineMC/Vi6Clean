package fr.nekotine.vi6clean.impl.tool.personal;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.NekotineCore;
import fr.nekotine.core.util.EntityUtil;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class Radar extends Tool{
	private boolean placed = false;
	private boolean sneaking = false;
	private ItemDisplay bottom;
	private ItemDisplay mid;
	private ItemDisplay top;
	private int chargeTime;
	
	//

	@Override
	protected ItemStack makeInitialItemStack() {
		return ItemStackUtil.make(Material.LIGHTNING_ROD, Component.text("Radar", NamedTextColor.GOLD), RadarHandler.LORE);
	}
	@Override
	protected void cleanup() {
		if(placed) {
			bottom.remove();
			mid.remove();
			top.remove();
		}
	}
	
	//
	
	protected boolean tryPlace() {
		Location ploc = getOwner().getLocation();
		
		if(placed || !EntityUtil.IsOnGround(getOwner()) || ploc.getBlock().getType().isSolid() || ploc.add(0, 1, 0).getBlock().getType().isSolid())
			return false;
		placed = true;
			
		bottom = (ItemDisplay)getOwner().getWorld().spawnEntity(ploc.subtract(0, 0.5, 0), EntityType.ITEM_DISPLAY);
		mid = (ItemDisplay)getOwner().getWorld().spawnEntity(ploc.add(0, 1, 0), EntityType.ITEM_DISPLAY);
		top = (ItemDisplay)getOwner().getWorld().spawnEntity(ploc.add(0,0.8,0), EntityType.ITEM_DISPLAY);

		float y = getOwner().getEyeLocation().getYaw();
		bottom.setRotation(y + 180, 0);
		mid.setRotation(y + 180, 0);
		top.setRotation(y + 180, 0);
		
		bottom.setItemStack(new ItemStack(Material.ORANGE_SHULKER_BOX));;
		mid.setItemStack(new ItemStack(Material.LIGHTNING_ROD));
		top.setItemStack(new ItemStack(Material.DAYLIGHT_DETECTOR));
		
		//Faire un son
		
		updateItem();
		
		chargeTime = 0;
		
		return true;
	}
	protected void updateItem() {
		if(placed) {
			setItemStack(RadarHandler.PLACED);
		}else {
			setItemStack(RadarHandler.UNPLACED);
		}
	}
	protected void detect() {
		if(placed) {
			
			var opt = NekotineCore.MODULES.get(WrappingModule.class).getWrapperOptional(getOwner(), PlayerWrapper.class);
			long ennemiNear = opt.get().ennemiTeamInMap().filter(e -> bottom.getLocation().distanceSquared(e.getLocation()) <= RadarHandler.DETECTION_RANGE_SQUARED).count();
			
			//Faire un son custom
			if(ennemiNear > 0)
				Vi6Sound.SONAR_POSITIVE.play(bottom.getWorld(), bottom.getLocation());
			
			//Faire un message avec le nombre de joueurs
			
			cleanup();
			placed = false;
			updateItem();
		}
	}
	protected void tickCharge() {
		if(placed) {
			if(chargeTime == 1) {
				top.setInterpolationDelay(0);
				top.setInterpolationDuration(RadarHandler.DELAY_SECOND * 20 - 10);
			    top.setTransformation(RadarHandler.TOP_TRANSFORMATION);
			}
			if(++chargeTime>=RadarHandler.DELAY_SECOND * 20) {
				detect();
			}
		}
		
	}
	protected void tickParticle() {
		if(placed) {
			Location loc =  bottom.getLocation();
			var x = loc.getX();
			var y = loc.getY();
			var z = loc.getZ();
			RadarHandler.BALL.forEach(
				triplet -> {loc.getWorld().spawnParticle(Particle.COMPOSTER, x + triplet.a(), y + triplet.b(), z + triplet.c(), 1, 0, 0, 0, 0, null);
			});
			
			
		}else if(sneaking) {
			Location loc = getOwner().getLocation();
			var x = loc.getX();
			var y = loc.getY();
			var z = loc.getZ();
			RadarHandler.SPHERE.forEach(
				triplet -> {getOwner().spawnParticle(Particle.COMPOSTER, x + triplet.a(), y + triplet.b(), z + triplet.c(), 1, 0, 0, 0, 0, null);
			});
		}
	}
	protected void tickSound() {
		if(placed) {
			//Faire un son custom
			Vi6Sound.SONAR_NEGATIVE.play(bottom.getWorld(), bottom.getLocation());
			
		}
	}
	protected boolean isPlaced() {
		return placed;
	}
	protected void setSneaking(boolean sneaking) {
		this.sneaking = sneaking;
	}
}
