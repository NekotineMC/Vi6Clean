package fr.nekotine.vi6clean.impl.majordom;

import java.util.LinkedList;
import java.util.List;

import org.bukkit.block.Block;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.type.Door;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import fr.nekotine.core.util.EventUtil;

public final class Majordom implements Listener {

	private final List<Block> toClose = new LinkedList<>();
	
	public final void enable() {
		EventUtil.register(this);
	}
	
	public final void disable() {
		EventUtil.unregister(this);
	}
	
	public final void revertThenDisable() {
		for (var block : new LinkedList<>(toClose)) {
			tryToggle(block);
		}
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
		if (toClose.contains(block)) {
			toClose.remove(block);
		}else {
			toClose.add(block);
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
