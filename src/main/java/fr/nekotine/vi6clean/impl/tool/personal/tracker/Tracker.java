package fr.nekotine.vi6clean.impl.tool.personal.tracker;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

import fr.nekotine.core.ioc.Ioc;
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
		var ownWrap = Ioc.resolve(WrappingModule.class).getWrapper(getOwner(), PlayerWrapper.class);
		var eyeLoc = getOwner().getEyeLocation();
		RayTraceResult res = getOwner().getWorld().rayTrace(
				eyeLoc, 
				eyeLoc.getDirection(), 
				TrackerHandler.RAY_DISTANCE, 
				FluidCollisionMode.NEVER, 
				true, 
				TrackerHandler.RAY_SIZE, 
				hit -> ownWrap.ennemiTeamInMap().anyMatch(ennemi -> ennemi.equals(hit)));
		
		var range = TrackerHandler.RAY_DISTANCE;
		if(res!=null) {
			var hitP = res.getHitPosition();
			range = eyeLoc.distance(new Location(eyeLoc.getWorld(), hitP.getX(), hitP.getY(), hitP.getZ()));
		}
		SpatialUtil.line3DFromDir(
			eyeLoc.getX(),
			eyeLoc.getY(),
			eyeLoc.getZ(),
			eyeLoc.getDirection(),
			range, 
			4, 
			(vec) -> getOwner().spawnParticle(Particle.FIREWORKS_SPARK, vec.getX(), vec.getY(), vec.getZ(), 0, 0, 0, 0, 0f));
		
		if(res == null || res.getHitEntity()==null) {
			Vi6Sound.TRACKER_FAIL.play(getOwner());
			handler.detachFromOwner(this);
			handler.remove(this);
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
