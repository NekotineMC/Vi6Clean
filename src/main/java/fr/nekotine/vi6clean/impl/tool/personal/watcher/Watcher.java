package fr.nekotine.vi6clean.impl.tool.personal.watcher;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Silverfish;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.status.effect.StatusEffectModule;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.core.util.MobAiUtil;
import fr.nekotine.core.util.SpatialUtil;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.impl.tool.Tool;

public class Watcher extends Tool{

	private List<Silverfish> watchers = new LinkedList<>();
	
	private boolean isSneaking;
	
	private Collection<Player> ennemiesInRange = new LinkedList<>();
	
	private boolean isEmp = false;
	
	
	@Override
	protected ItemStack makeInitialItemStack() {
		var handler = Ioc.resolve(WatcherHandler.class);
		return ItemStackUtil.make(
				Material.SILVERFISH_SPAWN_EGG,
				handler.getNbMaxWatcher(),
				handler.getDisplayName(),
				handler.getLore());
	}
	
	public void lowTick() {
		var handler = Ioc.resolve(WatcherHandler.class);
		if (watchers.size() < handler.getNbMaxWatcher()) {
			var player = getOwner();
			if (player == null || !isSneaking || !player.getInventory().getItemInMainHand().isSimilar(getItemStack())) {
				return;
			}
			var loc = player.getLocation();
			var x = loc.getX();
			var y = loc.getY();
			var z = loc.getZ();
			SpatialUtil.circle2DDensity(handler.getDetectionBlockRange(), 5, 0,
					(offsetX, offsetZ) -> {
						player.spawnParticle(Particle.FIREWORKS_SPARK, x + offsetX, y, z + offsetZ, 1, 0, 0, 0, 0, null);
					});
		}
	}
	
	public boolean tryDropWatcher() {
		if (watchers.size() >= Ioc.resolve(WatcherHandler.class).getNbMaxWatcher()) {
			return false;
		}
		var owner = getOwner();
		if (owner == null) {
			return false;
		}
		var ownerLoc = owner.getLocation();
		var sf = (Silverfish)ownerLoc.getWorld().spawnEntity(ownerLoc, EntityType.SILVERFISH);
		MobAiUtil.clearBrain(sf);
		sf.setInvulnerable(true);
		sf.setSilent(true);
		watchers.add(sf);
		itemUpdate();
		return true;
	}
	
	public boolean tryPickupWatcher() {
		var owner = getOwner();
		if (owner == null) {
			return false;
		}
		var ownerLoc = owner.getLocation();
		var worked = false;
		for (var sf : watchers.stream().filter(sf -> sf.getLocation().distanceSquared(ownerLoc) <= Ioc.resolve(WatcherHandler.class).getDetectionBlockRangeSquared())
				.collect(Collectors.toCollection(LinkedList::new))) {
			watchers.remove(sf);
			sf.remove();
			worked = true;
		}
		if (worked) {
			itemUpdate();
		}
		return worked;
	}

	public List<Silverfish> getWatcherList(){
		return watchers;
	}
	
	@Override
	protected void cleanup() {
		for (var watcher : watchers) {
			watcher.remove();
		}
		watchers.clear();
	}
	
	public void setSneaking(boolean sneaking) {
		isSneaking = sneaking;
	}
	
	public boolean isSneaking() {
		return isSneaking;
	}
	
	public Collection<Player> getEnnemiesInRange(){
		return ennemiesInRange;
	}
	
	public void itemUpdate() {
		var handler = Ioc.resolve(WatcherHandler.class);
		var nbAvailable = handler.getNbMaxWatcher()-watchers.size();
		if (nbAvailable <= 0) {
			setItemStack(handler.getNoWatcherItemStack());
		}else {
			var mat = isEmp ? Material.EVOKER_SPAWN_EGG : Material.SILVERFISH_SPAWN_EGG;
			setItemStack(ItemStackUtil.make(
					mat,
					nbAvailable,
					handler.getDisplayName(),
					handler.getLore()));
		}
	}
	
	//

	@Override
	protected void onEmpStart() {
		isEmp = true;
		var statusModule = Ioc.resolve(StatusEffectModule.class);
		for(Player p : ennemiesInRange) {
			statusModule.removeEffect(p, WatcherHandler.glowEffect);
		}
		itemUpdate();
	}
	@Override
	protected void onEmpEnd() {
		isEmp = false;
		var statusModule = Ioc.resolve(StatusEffectModule.class);
		for(Player p : ennemiesInRange) {
			statusModule.addEffect(p, WatcherHandler.glowEffect);
			Vi6Sound.OMNICAPTEUR_DETECT.play(p);
		}
		if(ennemiesInRange.size() > 0) {
			Vi6Sound.OMNICAPTEUR_DETECT.play(getOwner());
		}
		itemUpdate();
	}
}
