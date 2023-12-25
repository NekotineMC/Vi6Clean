package fr.nekotine.vi6clean.impl.tool.personal.recall;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.track.ClientTrackModule;
import fr.nekotine.vi6clean.impl.tool.Tool;

public class Recall extends Tool{
	private boolean isPlaced = false;
	private boolean isEmp = false;
	private Location placedLocation;
	private Location particleLocation;
	private int n = 0;
	public boolean use() {
		if(isEmp) {
			return false;
		}
		if(isPlaced) {
			recall();
		}else {
			var trackModule = Ioc.resolve(ClientTrackModule.class);
			trackModule.untrack(getOwner());
			
			isPlaced = true;
			placedLocation = getOwner().getLocation();
			particleLocation = placedLocation.clone().subtract(0, 0.1, 0);
			getOwner().setCooldown(Material.POPPED_CHORUS_FRUIT, Ioc.resolve(RecallHandler.class).getTeleportDelayTicks());
		}
		updateItem();
		return true;
	}
	public void tickCooldown() {
		if(!isPlaced)
			return;
		if(++n >= Ioc.resolve(RecallHandler.class).getTeleportDelayTicks()) {
			recall();
		}
	}
	public void tickParticle() {
		if(!isPlaced)
			return;
		getOwner().getWorld().spawnParticle(Particle.GLOW, particleLocation, Ioc.resolve(RecallHandler.class).getParticleNumber(), 0.1, 0, 0.1, 0);
	}
	private void updateItem() {
		var handler = Ioc.resolve(RecallHandler.class);
		if(isPlaced) {
			setItemStack(handler.getPlaced());
		}else {
			setItemStack(handler.getUnplaced());
		}
	}
	private void recall() {
		n = 0;
		isPlaced = false;
		var trackModule = Ioc.resolve(ClientTrackModule.class);
		trackModule.track(getOwner());
		getOwner().teleport(placedLocation);
		updateItem();
	}
	
	//
	
	@Override
	protected ItemStack makeInitialItemStack() {
		return Ioc.resolve(RecallHandler.class).getUnplaced();
	}
	@Override
	protected void cleanup() {
	}
	@Override
	protected void onEmpStart() {
		if(isPlaced) {
			recall();
			updateItem();
		}
		isEmp = true;
	}
	@Override
	protected void onEmpEnd() {
		isEmp = false;
	}
}
