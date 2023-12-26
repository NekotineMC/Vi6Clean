package fr.nekotine.vi6clean.impl.tool.personal.smokepool;

import java.util.ArrayList;
import java.util.Random;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.status.effect.StatusEffectModule;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.impl.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.impl.tool.Tool;

public class SmokePool extends Tool{
	private Location placedLoc;
	private ArrayList<Player> inside = new ArrayList<Player>();
	private boolean placed = false;
	private int life = 0;
	private int cooldownLeft = 0;
	private final double AIR = Ioc.resolve(SmokePoolHandler.class).getAir();
	private final double DIAMETER = Ioc.resolve(SmokePoolHandler.class).getDiameter();
	private final double RADIUS = Ioc.resolve(SmokePoolHandler.class).getRadius();
	private final Random RNG = SmokePoolHandler.RNG;
	@Override
	protected ItemStack makeInitialItemStack() {
		return Ioc.resolve(SmokePoolHandler.class).getItem();
	}
	
	protected boolean cast() {
		if(placed || cooldownLeft > 0) {
			return false;
		}
		if(Ioc.resolve(StatusFlagModule.class).hasAny(getOwner(), EmpStatusFlag.get())) {
			return false;
		}
		var handler = Ioc.resolve(SmokePoolHandler.class);
		life = handler.getDurationTick();
		placed = true;
		placedLoc = getOwner().getLocation();
		Vi6Sound.SMOKEPOOL.play(placedLoc.getWorld(), placedLoc);
		getOwner().setCooldown(handler.getItem().getType(), handler.getDurationTick());
		return true;
	}
	protected void tickCooldown() {
		if(placed) {
			if(--life <= 0) {
				cleanup();
			}
		}else if(cooldownLeft > 0){
			cooldownLeft--;
			if(cooldownLeft == 0 && !Ioc.resolve(StatusFlagModule.class).hasAny(getOwner(), EmpStatusFlag.get())) {
				setItemStack(Ioc.resolve(SmokePoolHandler.class).getItem());
			}
		}
	}
	protected void tickParticle() {
		if(!placed) {
			return;
		}
		Location l = new Location(placedLoc.getWorld(),0,0,0);
		for (int i=0;i<AIR;i++) {
			double angle = RNG.nextFloat()*DIAMETER;
			double point = RNG.nextFloat()*RADIUS;
			l.setX(placedLoc.getX()+(Math.cos(angle)*point));
			l.setZ(placedLoc.getZ()+(Math.sin(angle)*point));
			l.setY(placedLoc.getY()-1);
			double maxy = l.getY()+2.5;
			while (l.getY()<maxy) {
				if (l.getBlock().getBoundingBox().contains(l.toVector())) {
					l.add(0, 0.1, 0);
				}else{
					l.getWorld().spawnParticle(Particle.SMOKE_NORMAL, l, 1,0,0,0,0);
					break;
				};
			}
		}
	}
	
	//
	
	protected boolean isPlaced() {
		
		return placed;
	}
	protected ArrayList<Player> getInside(){
		return inside;
	}
	protected Location getPlacedLocation() {
		return placedLoc;
	}
	
	//

	@Override
	protected void cleanup() {
		var handler = Ioc.resolve(SmokePoolHandler.class);
		var statusModule = Ioc.resolve(StatusEffectModule.class);
		inside.forEach(p -> statusModule.removeEffect(p, handler.getEffect()));
		inside.clear();
		placed = false;
		cooldownLeft = handler.getCooldownTick();
		setItemStack(handler.getCooldownItem());
		getOwner().setCooldown(handler.getCooldownItem().getType(), handler.getCooldownTick());
	
	}

	@Override
	protected void onEmpStart() {
		if(placed) {
			cleanup();
		}
	}

	@Override
	protected void onEmpEnd() {
		if(cooldownLeft == 0) {
			setItemStack(Ioc.resolve(SmokePoolHandler.class).getItem());
		}
	}
}
