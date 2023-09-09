package fr.nekotine.vi6clean.impl.tool;

import java.util.Collection;
import java.util.LinkedList;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

import fr.nekotine.core.util.EventUtil;

/**
 * Gestionnaire d'outil pour une partie de Vi6.
 * Une instance par partie.
 * L'objectif de cette classe est de fournir le comportemant attendu à tous les outils d'un même type.
 * @author XxGoldenbluexX
 *
 * @param <T>
 */
public abstract class ToolHandler<T extends Tool> implements Listener{

	private final ToolType type;
	
	private final Supplier<T> toolSupplier;
	
	private final Collection<T> tools = new LinkedList<>();
	
	public ToolHandler(ToolType type, Supplier<T> toolSupplier) {
		this.type = type;
		this.toolSupplier = toolSupplier;
	}

	public final void startHandling() {
		EventUtil.register(this);
	}
	
	public final void stopHandling() {
		for (var tool : tools) {
			detachFromOwner(tool);
		}
		tools.clear();
		EventUtil.unregister(this);
	}
	
	/**
	 * Try to make and attach new tool to player. Attachment can be denied if there is a limit on this type of tool.
	 * If tool attachment is denied, the new tool is discarded.
	 * @param player
	 * @return If tool could be attached.
	 */
	public final boolean attachNewToPlayer(Player player) {
		var tool = toolSupplier.get();
		if (attachToPlayer(tool, player)) {
			player.getInventory().addItem(tool.getItemStack());
			tools.add(tool);
			return true;
		}
		return false;
	}
	
	/**
	 * Try to attach tool to player. Attachment can be denied if there is a limit on this type of tool
	 * @param player
	 * @return If tool could be attached.
	 */
	public final boolean attachToPlayer(T tool, Player player) {
		if (type.getLimite() <= tools.stream().filter(t -> player.equals(t.getOwner())).count()) {
			return false;
		}
		tool.setOwner(player);
		onAttachedToPlayer(tool, player);
		return true;
	}
	
	public final void detachFromOwner(T tool) {
		if (tool.getOwner() == null) {
			return;
		}
		onDetachFromPlayer(tool, tool.getOwner());
		tool.setOwner(null);
	}
	
	public final void remove(T tool) {
		detachFromOwner(tool);
		tools.remove(tool);
	}
	
	protected abstract void onAttachedToPlayer(T tool, Player player);
	
	protected abstract void onDetachFromPlayer(T tool, Player player);
	
	public Collection<T> getTools(){
		return tools;
	}

	public ToolType getType() {
		return type;
	}
	
	@EventHandler
	private void onPlayerDrop(PlayerDropItemEvent evt) {
		var optionalTool = tools.stream().filter(t -> evt.getItemDrop().getItemStack().equals(t.getItemStack())).findFirst();
		if (optionalTool.isEmpty()) {
			return;
		}
		var tool = optionalTool.get();
		if (evt.getPlayer().equals(tool.getOwner())) {
			detachFromOwner(tool);
		}
	}
	
	@EventHandler
	private void onPlayerPickup(EntityPickupItemEvent evt) {
		if (!(evt.getEntity() instanceof Player player)) {
			return;
		}
		var optionalTool = tools.stream().filter(t -> t.getOwner() == null && evt.getItem().getItemStack().equals(t.getItemStack())).findFirst();
		if (optionalTool.isEmpty()) {
			return;
		}
		if (!attachToPlayer(optionalTool.get(), player)) {
			evt.setCancelled(true);
		}
	}
}
