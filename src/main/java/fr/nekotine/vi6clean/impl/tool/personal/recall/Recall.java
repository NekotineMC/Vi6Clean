package fr.nekotine.vi6clean.impl.tool.personal.recall;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.track.ClientTrackModule;
import fr.nekotine.core.util.SpatialUtil;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.impl.tool.Tool;

public class Recall extends Tool{
	private final RecallHandler handler = Ioc.resolve(RecallHandler.class);
	private boolean isPlaced = false;
	private boolean onCooldown = false;
	private Location placedLocation;
	private Location particleLocation;
	private int n = 0;
	
	private boolean activated = false;
	private int cpt = 0;
	private int cpt2 = 0;
	private List<Location> l1 = new ArrayList<Location>();
	private List<Location> l2 = new ArrayList<Location>();
	
	
	public boolean use() {
		l1 = new ArrayList<Location>();
		l2 = new ArrayList<Location>();
		var w = Ioc.resolve(Vi6Game.class).getWorld();
		SpatialUtil.helix(3, 0.75, 3, 0, 0.25, 
		v -> l1.add(v.toLocation(w)));
		SpatialUtil.helix(3, 0.75, 3, Math.PI, 0.25, 
		v -> l2.add(v.toLocation(w)));
		activated = true;
		
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
		Ioc.resolve(ClientTrackModule.class).track(getOwner());
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
	
	
	public void tickTest() {
		var w = Ioc.resolve(Vi6Game.class).getWorld();
		if(activated) {
			
			//
			if(cpt == l1.size()) {
				activated = false;
				cpt = 0;
			}else {
				for(int i = 0 ; i <= cpt ; i++) {
					w.spawnParticle(Particle.GLOW, l1.get(i).clone().add(0.5, 100, 0.5), 1,0,0,0,0,null);
					w.spawnParticle(Particle.GLOW, l2.get(i).clone().add(0.5, 100, 0.5), 1,0,0,0,0,null);
				}
			}
			cpt2++;
			if(cpt2==4) {
				cpt2 = 0;
				cpt++;
			}
		}
	}
}
