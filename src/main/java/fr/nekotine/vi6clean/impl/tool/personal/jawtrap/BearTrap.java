package fr.nekotine.vi6clean.impl.tool.personal.jawtrap;

import java.util.Collection;
import java.util.stream.Stream;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.inventory.ItemStack;

import com.google.common.base.Supplier;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.status.effect.StatusEffect;
import fr.nekotine.core.status.effect.StatusEffectModule;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.impl.status.effect.TazedStatusEffectType;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;

public class BearTrap extends Tool{
	private static final int FANG_ANIMATION_DURATION_TICK = 20;
	private static final StatusEffect EFFECT = new StatusEffect(
			TazedStatusEffectType.get(), FANG_ANIMATION_DURATION_TICK);
	private Location location;
	private ArmorStand trap;
	private Entity fang;
	private boolean placed = false;
	private boolean armed = false;
	private boolean triggered = false;
	private int n = 0;
	private Player hit;
	private Supplier<Stream<Player>> enemyTeam;
	private Supplier<Collection<Player>> ourTeam;
	
	public Location getLocation() {
		return location;
	}
	public boolean isArmed() {
		return armed;
	}
	public void trigger(Player hit) {
		this.hit = hit;
		armed = false;
		triggered = true;
		fang = location.getWorld().spawnEntity(
				location, EntityType.EVOKER_FANGS, SpawnReason.TRAP);
		trap.getEquipment().setHelmet(Ioc.resolve(BearTrapHandler.class).getTriggeredItem());
		Ioc.resolve(StatusEffectModule.class).addEffect(hit, EFFECT);
	}
	public void tickAnimation() {
		if(triggered && ++n >= FANG_ANIMATION_DURATION_TICK) {
			triggered = false;
		}
	}
	
	//
	
	@Override
	protected ItemStack makeInitialItemStack() {
		return Ioc.resolve(BearTrapHandler.class).getItem();
	}
	@Override
	protected void cleanup() {
		if(placed) {
			trap.remove();
		}
		placed = false;
		armed = false;
		triggered = false;
		n = 0;
	}
	@Override
	protected void onEmpStart() {
	}
	@Override
	protected void onEmpEnd() {
	}
	
	//

	public boolean tryPlace() {
		if(placed) {
			return false;
		}
		var pLoc = getOwner().getLocation();
		if(!pLoc.clone().subtract(0.0D, 0.1D, 0.0D).getBlock().getType().isSolid()) {
			return false;
		}
		placed = true;
		armed = true;
		location = getOwner().getLocation();
		trap = (ArmorStand)location.getWorld().spawnEntity(location.clone().subtract(0, 1.95, 0), EntityType.ARMOR_STAND);
		trap.setInvisible(true);
		trap.setMarker(true);
		trap.setGravity(false);
		trap.getEquipment().setHelmet(Ioc.resolve(BearTrapHandler.class).getItem());
		enemyTeam = Ioc.resolve(WrappingModule.class).getWrapper(getOwner(), PlayerWrapper.class)::ennemiTeamInMap;
		ourTeam = Ioc.resolve(WrappingModule.class).getWrapper(getOwner(), PlayerWrapper.class)::ourTeam;
		Ioc.resolve(BearTrapHandler.class).detachFromOwner(this);
		return true;
	}
	public boolean tryPickup(Player trying) {
		if(!placed) {
			return false;
		}
		if(!ourTeam.get().contains(trying)) {
			return false;
		}
		var handler = Ioc.resolve(BearTrapHandler.class);
		if(location.distanceSquared(trying.getLocation()) > handler.getSquaredPickupRange()) {
			return false;
		}
		handler.attachToPlayer(this, trying);
		setItemStack(getItemStack());
		cleanup();
		return true;
	}
	public boolean isPlaced() {
		return placed;
	}
	public boolean isTriggered() {
		return triggered;
	}
	public Player getHit() {
		return hit;
	}
	public Entity getFang() {
		return fang;
	}
	public Stream<Player> getEnemyTeam(){
		return enemyTeam.get();
	}
}
