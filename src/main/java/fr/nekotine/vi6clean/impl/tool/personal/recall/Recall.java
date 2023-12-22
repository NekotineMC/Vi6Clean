package fr.nekotine.vi6clean.impl.tool.personal.recall;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.track.EntityTrackModule;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;

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
			var trackModule = Ioc.resolve(EntityTrackModule.class);
			var opt = Ioc.resolve(WrappingModule.class).getWrapperOptional(getOwner(), PlayerWrapper.class);
			opt.get().ennemiTeam().forEach(p -> trackModule.untrackEntityFor(getOwner(), p));
			
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
		var trackModule = Ioc.resolve(EntityTrackModule.class);
		var opt = Ioc.resolve(WrappingModule.class).getWrapperOptional(getOwner(), PlayerWrapper.class);
		opt.get().ennemiTeam().forEach(p -> trackModule.trackEntityFor(getOwner(), p));
		
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
