package fr.nekotine.vi6clean.impl.tool.personal.tracker;

import org.bukkit.FluidCollisionMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

import fr.nekotine.core.NekotineCore;
import fr.nekotine.core.wrapper.WrappingModule;
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
		RayTraceResult res = getOwner().getWorld().rayTrace(
				getOwner().getEyeLocation(), 
				getOwner().getEyeLocation().getDirection(), 
				TrackerHandler.RAY_DISTANCE, 
				FluidCollisionMode.NEVER, 
				true, 
				TrackerHandler.RAY_SIZE, 
				hit -> ennemiTeam.anyMatch(ennemi -> ennemi.equals(hit)));
		if(res.getHitEntity()==null) {
			handler.detachFromOwner(this);
			handler.remove(this);
			return true;
		}
		playerHit = (Player)res.getHitEntity();
		hit = true;
		
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
