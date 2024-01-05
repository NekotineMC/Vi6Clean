package fr.nekotine.vi6clean.impl.tool.personal.recall;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.track.ClientTrackModule;
import fr.nekotine.vi6clean.impl.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.impl.tool.Tool;

public class Recall extends Tool{
	private final RecallHandler handler = Ioc.resolve(RecallHandler.class);
	private boolean isPlaced = false;
	private boolean onCooldown = false;
	private Location placedLocation;
	private Location particleLocation;
	private int n = 0;
	public boolean use() {
		if(onCooldown) {
			return false;
		}
		var statusModule = Ioc.resolve(StatusFlagModule.class);
		if(statusModule.hasAny(getOwner(), EmpStatusFlag.get())) {
			return false;
		}
		if(isPlaced) {
			recall();
		}else {
			var trackModule = Ioc.resolve(ClientTrackModule.class);
			trackModule.untrack(getOwner());
			n=0;
			isPlaced = true;
			placedLocation = getOwner().getLocation();
			particleLocation = placedLocation.clone().subtract(0, 0.1, 0);
			getOwner().setCooldown(Material.POPPED_CHORUS_FRUIT, handler.getTeleportDelayTicks());
		}
		updateItem();
		return true;
	}
	public void tickCooldown() {
		if(onCooldown) {
			if(++n >= handler.getCooldownTick()) {
				onCooldown = false;
				updateItem();
			}
		}else if(isPlaced) {
			if(++n >= handler.getTeleportDelayTicks()) {
				recall();
			}
		}
	}
	public void tickParticle() {
		if(isPlaced) {
			getOwner().getWorld().spawnParticle(Particle.GLOW, particleLocation, handler.getParticleNumber(), 0.1, 0, 0.1, 0);
		}
	}
		
	private void updateItem() {
		var statusModule = Ioc.resolve(StatusFlagModule.class);
		if(onCooldown || statusModule.hasAny(getOwner(), EmpStatusFlag.get())) {
			setItemStack(handler.getCooldown());
		}else if(isPlaced) {
			setItemStack(handler.getPlaced());
		}else {
			setItemStack(handler.getUnplaced());
		}
	}
	private void recall() {
		var trackModule = Ioc.resolve(ClientTrackModule.class);
		n = 0;
		isPlaced = false;
		onCooldown = true;
		trackModule.track(getOwner());
		getOwner().teleport(placedLocation);
		getOwner().setCooldown(handler.getCooldown().getType(), handler.getCooldownTick());
		updateItem();
	}
	
	//
	
	@Override
	protected ItemStack makeInitialItemStack() {
		return handler.getUnplaced();
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
	}
	@Override
	protected void onEmpEnd() {
		updateItem();
	}
}
