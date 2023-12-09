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

import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.core.util.MobAiUtil;
import fr.nekotine.core.util.SpatialUtil;
import fr.nekotine.vi6clean.constant.Vi6ToolLoreText;
import fr.nekotine.vi6clean.impl.tool.Tool;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class Watcher extends Tool{

	private List<Silverfish> watchers = new LinkedList<>();
	
	private boolean isSneaking;
	
	private Collection<Player> ennemiesInRange = new LinkedList<>();
	
	private static final ItemStack NOWATCHER_ITEMSTACK = ItemStackUtil.make(Material.ENDERMITE_SPAWN_EGG, 1,
			Component.text("Observateur", NamedTextColor.GOLD), Vi6ToolLoreText.WATCHER.make());
	
	@Override
	protected ItemStack makeInitialItemStack() {
		return ItemStackUtil.make(Material.SILVERFISH_SPAWN_EGG,WatcherHandler.NB_MAX_WATCHER,
				Component.text("Observateur",NamedTextColor.GOLD),
				Vi6ToolLoreText.WATCHER.make());
	}
	
	public void lowTick() {
		if (watchers.size() < WatcherHandler.NB_MAX_WATCHER) {
			var player = getOwner();
			if (player == null || !isSneaking || !player.getInventory().getItemInMainHand().isSimilar(getItemStack())) {
				return;
			}
			var loc = player.getLocation();
			var x = loc.getX();
			var y = loc.getY();
			var z = loc.getZ();
			SpatialUtil.circle2DDensity(WatcherHandler.DETECTION_BLOCK_RANGE, 5, 0,
					(offsetX, offsetZ) -> {
						player.spawnParticle(Particle.FIREWORKS_SPARK, x + offsetX, y, z + offsetZ, 1, 0, 0, 0, 0, null);
					});
		}
	}
	
	public boolean tryDropWatcher() {
		if (watchers.size() >= WatcherHandler.NB_MAX_WATCHER) {
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
		for (var sf : watchers.stream().filter(sf -> sf.getLocation().distanceSquared(ownerLoc) <= WatcherHandler.DETECTION_RANGE_SQUARED)
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
		var nbAvailable = WatcherHandler.NB_MAX_WATCHER-watchers.size();
		if (nbAvailable <= 0) {
			setItemStack(NOWATCHER_ITEMSTACK);
		}else {
			setItemStack(ItemStackUtil.make(Material.SILVERFISH_SPAWN_EGG,nbAvailable,
				Component.text("Observateur",NamedTextColor.GOLD),
				Vi6ToolLoreText.WATCHER.make()));
		}
		
	}
	
	//

	@Override
	protected void onEmpStart() {
	}
	@Override
	protected void onEmpEnd() {
	}
}
