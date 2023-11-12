package fr.nekotine.vi6clean.impl.tool.personal.tracker;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

import fr.nekotine.core.NekotineCore;
import fr.nekotine.core.util.SpatialUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;

public class Tracker extends Tool{
	private boolean hit = false;
	private Player playerHit;
	private void setCompassItem() {
		setItemStack(TrackerHandler.COMPASS_ITEM(getOwner().getLocation(), playerHit.getLocation()));
	}
	@Override
	protected ItemStack makeInitialItemStack() {
		return TrackerHandler.GUN_ITEM();
	}
	@Override
	protected void cleanup() {
	}
	
	//
	
	protected boolean shoot(TrackerHandler handler) {
		if(hit)
			return false;
		var ennemiTeam = NekotineCore.MODULES.get(WrappingModule.class).getWrapper(getOwner(), PlayerWrapper.class).ennemiTeamInMap();
		var eyeLoc = getOwner().getEyeLocation();
		RayTraceResult res = getOwner().getWorld().rayTrace(
				eyeLoc, 
				eyeLoc.getDirection(), 
				TrackerHandler.RAY_DISTANCE, 
				FluidCollisionMode.NEVER, 
				true, 
				TrackerHandler.RAY_SIZE, 
				hit -> ennemiTeam.anyMatch(ennemi -> ennemi.equals(hit)));
		
		var hitB = res.getHitBlock();
		var hitE = res.getHitEntity();
		var range = hitB==null ?
					(hitE==null ?
					TrackerHandler.RAY_DISTANCE :
					hitE.getLocation().distance(getOwner().getLocation())):
					hitB.getLocation().distance(getOwner().getLocation());
		SpatialUtil.line3DFromDir(
				eyeLoc.getX(),
				eyeLoc.getY(),
				eyeLoc.getZ(),
				eyeLoc.getDirection(),
				range, 
				4, 
				(x,y,z) -> getOwner().spawnParticle(Particle.FIREWORKS_SPARK, x, y, z, 1));
		if(hitE==null) {
			handler.detachFromOwner(this);
			handler.remove(this);
			Vi6Sound.TRACKER_FAIL.play(getOwner());
			return true;
		}
		playerHit = (Player)res.getHitEntity();
		hit = true;
		Vi6Sound.TRACKER_SUCCESS.play(getOwner());
		
		//kb & animation
		playerHit.playHurtAnimation(getOwner().getEyeLocation().getYaw() + playerHit.getLocation().getYaw());
		
		setCompassItem();
		return true;
		
	}
	protected void tickRefresh() {
		if(hit) 
			setCompassItem();
	}
}
