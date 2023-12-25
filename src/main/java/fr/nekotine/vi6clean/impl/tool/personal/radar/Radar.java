package fr.nekotine.vi6clean.impl.tool.personal.radar;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.impl.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;

public class Radar extends Tool{
	private boolean placed = false;
	private boolean sneaking = false;
	private boolean inHand = false;
	private ItemDisplay bottom;
	private ItemDisplay mid;
	private ItemDisplay top;
	private int chargeTime;
	private int cooldown;
	
	//

	@Override
	protected ItemStack makeInitialItemStack() {
		return Ioc.resolve(RadarHandler.class).getUnplaced();
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
		if(cooldown > 0)
			return false;

		Location ploc = getOwner().getLocation();
		var flagModule = Ioc.resolve(StatusFlagModule.class);
		if (placed || !ploc.subtract(0, 0.1, 0).getBlock().getType().isSolid() || flagModule.hasAny(getOwner(), EmpStatusFlag.get()))
			return false;
			
		bottom = (ItemDisplay)getOwner().getWorld().spawnEntity(ploc.add(0, 0.6, 0), EntityType.ITEM_DISPLAY);
		mid = (ItemDisplay)getOwner().getWorld().spawnEntity(ploc.add(0, 1, 0), EntityType.ITEM_DISPLAY);
		top = (ItemDisplay)getOwner().getWorld().spawnEntity(ploc.add(0,0.8,0), EntityType.ITEM_DISPLAY);

		float y = getOwner().getEyeLocation().getYaw();
		bottom.setRotation(y + 180, 0);
		mid.setRotation(y + 180, 0);
		top.setRotation(y + 180, 0);
		
		bottom.setItemStack(new ItemStack(Material.ORANGE_SHULKER_BOX));;
		mid.setItemStack(new ItemStack(Material.LIGHTNING_ROD));
		top.setItemStack(new ItemStack(Material.CALIBRATED_SCULK_SENSOR));
		
		placed = true;
		chargeTime = 0;
		Vi6Sound.RADAR_POSE.play(bottom.getWorld(), bottom.getLocation());
		updateItem();
		return true;
	}
	protected void updateItem() {
		var handler = Ioc.resolve(RadarHandler.class);
		if(placed) {
			setItemStack(handler.getPlaced());
		}else {
			setItemStack(handler.getUnplaced());
		}
	}
	protected void detect() {
		if(placed) {
			var handler = Ioc.resolve(RadarHandler.class);
			var opt = Ioc.resolve(WrappingModule.class).getWrapperOptional(getOwner(), PlayerWrapper.class);
			int ennemiNear = (int)opt.get().ennemiTeamInMap().filter(e -> bottom.getLocation().distanceSquared(e.getLocation()) <= handler.getDetectionRangeSquared()).count();
			
			//Son
			if(ennemiNear > 0) {
				Vi6Sound.RADAR_POSITIVE.play(bottom.getWorld(), bottom.getLocation());
			}else {
				Vi6Sound.RADAR_NEGATIVE.play(bottom.getWorld(), bottom.getLocation());
			}
			
			//Message
			getOwner().sendMessage(handler.getDetectionMessage(ennemiNear));
			
			//Particules
			Location loc = bottom.getLocation();
			var x = loc.getX();
			var y = loc.getY();
			var z = loc.getZ();
			
			RadarHandler.BALL.forEach(
				triplet -> {loc.getWorld().spawnParticle(
						(ennemiNear>0 ? Particle.COMPOSTER:Particle.REDSTONE), 
						x + triplet.a(), y + triplet.b(), z + triplet.c(), 1, 0, 0, 0, 0, 
						(ennemiNear>0 ? null:new DustOptions(Color.RED, 2)));
			});
			
			cooldown = handler.getCooldownTick();
			getOwner().setCooldown(getItemStack().getType(), cooldown);
			
			cleanup();
			placed = false;
			updateItem();
		}
	}
	protected void tickCharge() {
		if(placed) {
			var handler = Ioc.resolve(RadarHandler.class);
			if(chargeTime == 1) {
				top.setInterpolationDelay(0);
				top.setInterpolationDuration(handler.getDelayTick() - 10);
			    top.setTransformation(RadarHandler.TOP_TRANSFORMATION);
			}
			if(++chargeTime>=handler.getDelayTick()) {
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
				triplet -> {loc.getWorld().spawnParticle(Particle.SPELL_WITCH, x + triplet.a(), y + triplet.b(), z + triplet.c(), 1, 0, 0, 0, 0, null);
			});
			
			
		}else if(sneaking && inHand) {
			Location loc = getOwner().getLocation();
			var x = loc.getX();
			var y = loc.getY();
			var z = loc.getZ();
			RadarHandler.SPHERE.forEach(
				triplet -> {getOwner().spawnParticle(Particle.SPELL_WITCH, x + triplet.a(), y + triplet.b(), z + triplet.c(), 1, 0, 0, 0, 0, null);
			});
		}
	}
	protected void tickSound() {
		if(placed) {
			//Faire un son custom
			Vi6Sound.RADAR_SCAN.play(bottom.getWorld(), bottom.getLocation());
			
		}
	}
	protected void tickCooldown() {
		if(cooldown > 0)
			cooldown--;
	}
	protected boolean isPlaced() {
		return placed;
	}
	protected void setSneaking(boolean sneaking) {
		this.sneaking = sneaking;
	}
	protected void setInHand(boolean inHand) {
		this.inHand = inHand;
	}

	//

	@Override
	protected void onEmpStart() {
		var handler = Ioc.resolve(RadarHandler.class);
		if (placed) {
			cooldown = handler.getCooldownTick();
			getOwner().setCooldown(getItemStack().getType(), cooldown);
			
			cleanup();
			placed = false;
		}
		setItemStack(handler.getEmped());
	}
	@Override
	protected void onEmpEnd() {
		updateItem();
	}
}
