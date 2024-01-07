package fr.nekotine.vi6clean.impl.majordom;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.bukkit.block.Block;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.type.Door;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.util.EventUtil;

public final class Majordom implements Listener {

	private final Map<Block, BukkitTask> toClose = new HashMap<>();
	
	public final void enable() {
		EventUtil.register(this);
	}
	
	public final void disable() {
		EventUtil.unregister(this);
	}
	
	public final void revertThenDisable() {
		for (var block : new LinkedList<>(toClose.keySet())) {
			tryToggle(block);
		}
		disable();
	}
	
	private boolean tryToggle(Block block) {
		var bdata = block.getBlockData();
		if (!(bdata instanceof Openable openable)) {
			return false;
		}
		if (openable instanceof Door door && tryToggle(block.getLocation().add(0, -1, 0).getBlock())) {
			return true;
		}
		// TOGGLE STATUS
		if (toClose.containsKey(block)) {
			toClose.computeIfPresent(block, (b,t) -> {t.cancel();return null;});
			toClose.remove(block);
		}else {
			var plugin = Ioc.resolve(JavaPlugin.class);
			var task = new BukkitRunnable() {
				@Override
				public void run() {
					tryToggle(block);
				}
			}.runTaskLater(plugin, plugin.getConfig().getInt("majordom.delay", 40));
			toClose.put(block,task);
		}
		openable.setOpen(!openable.isOpen());
		block.setBlockData(openable);
		return true;
	}
	
	@EventHandler
	private void OnPlayerInteract(PlayerInteractEvent evt) {
		if (evt.getAction() != Action.RIGHT_CLICK_BLOCK || evt.getHand() != EquipmentSlot.HAND) {
			return;
		}
		var block = evt.getClickedBlock();
		if (block == null) {
			return;
		}
		evt.setCancelled(tryToggle(block));
	}
	
}
