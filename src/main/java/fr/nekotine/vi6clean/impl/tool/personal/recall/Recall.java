package fr.nekotine.vi6clean.impl.tool.personal.recall;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.inventory.ItemStack;

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
			isPlaced = true;
			placedLocation = getOwner().getLocation();
			particleLocation = placedLocation.clone().subtract(0, 0.1, 0);
			getOwner().setCooldown(Material.POPPED_CHORUS_FRUIT, RecallHandler.TELEPORT_DELAY_TICKS);
		}
		updateItem();
		return true;
	}
	public void tickCooldown() {
		if(!isPlaced)
			return;
		if(++n >= RecallHandler.TELEPORT_DELAY_TICKS) {
			recall();
		}
	}
	public void tickParticle() {
		if(!isPlaced)
			return;
		getOwner().getWorld().spawnParticle(Particle.GLOW, particleLocation, RecallHandler.PARTICLE_NUMBER, 0.1, 0, 0.1, 0);
	}
	private void updateItem() {
		if(isPlaced) {
			setItemStack(RecallHandler.PLACED());
		}else {
			setItemStack(RecallHandler.UNPLACED());
		}
	}
	private void recall() {
		n = 0;
		isPlaced = false;
		getOwner().teleport(placedLocation);
		updateItem();
	}
	
	//
	
	@Override
	protected ItemStack makeInitialItemStack() {
		return RecallHandler.UNPLACED();
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
