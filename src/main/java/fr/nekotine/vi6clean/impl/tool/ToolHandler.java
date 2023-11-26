package fr.nekotine.vi6clean.impl.tool;

import java.util.Collection;
import java.util.LinkedList;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.logging.NekotineLogger;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.impl.wrapper.PreparationPhasePlayerWrapper;

/**
 * Gestionnaire d'outil pour une partie de Vi6. Une instance par partie.
 * L'objectif de cette classe est de fournir le comportemant attendu à tous les
 * outils d'un même type.
 * 
 * @author XxGoldenbluexX
 *
 * @param <T>
 */
public abstract class ToolHandler<T extends Tool> implements Listener {

	protected final Logger logger = new NekotineLogger(getClass());
	
	private final ToolType type;

	private final Supplier<T> toolSupplier;

	private final Collection<T> tools = new LinkedList<>();

	public ToolHandler(ToolType type, Supplier<T> toolSupplier) {
		this.type = type;
		this.toolSupplier = toolSupplier;
	}

	public final void startHandling() {
		onStartHandling();
		EventUtil.register(this);
	}

	public final void stopHandling() {
		EventUtil.unregister(this);
		onStopHandling();
	}

	public final void removeAll() {
		for (var tool : tools) {
			try {
				detachFromOwner(tool);
			}catch(Exception e) {
				logger.log(Level.SEVERE, "Une erreur est survenue lors du detachement d'un outil "+type, e);
			}
			try {
				tool.cleanup();
			}catch(Exception e) {
				logger.log(Level.SEVERE, "Une erreur est survenue lors du cleanup d'un outil "+type, e);
			}
		}
		tools.clear();
	}

	/**
	 * Try to make and attach new tool to player. Attachment can be denied if there
	 * is a limit on this type of tool. If tool attachment is denied, the new tool
	 * is discarded.
	 * 
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
	 * Try to attach tool to player. Attachment can be denied if there is a limit on
	 * this type of tool
	 * 
	 * @param player
	 * @return If tool could be attached.
	 */
	public final boolean attachToPlayer(T tool, Player player) {
		var lim = type.getLimite();
		if (lim >=0 && lim <= tools.stream().filter(t -> player.equals(t.getOwner())).count()) {
			return false;
		}
		tool.setOwner(player);
		onAttachedToPlayer(tool, player);
		return true;
	}

	public final void detachFromOwner(T tool) {
		var owner = tool.getOwner();
		if (owner == null) {
			return;
		}
		owner.getInventory().remove(tool.getItemStack());
		onDetachFromPlayer(tool, owner);
		tool.setOwner(null);
	}

	public final void remove(T tool) {
		detachFromOwner(tool);
		tool.cleanup();
		tools.remove(tool);
	}
	
	protected void onStartHandling() {
	}
	
	protected void onStopHandling() {
	}

	protected abstract void onAttachedToPlayer(T tool, Player player);

	protected abstract void onDetachFromPlayer(T tool, Player player);

	public Collection<T> getTools() {
		return tools;
	}

	public ToolType getType() {
		return type;
	}

	@EventHandler
	public void onPlayerDrop(PlayerDropItemEvent evt) {
		var optionalTool = tools.stream()
				.filter(t -> evt.getItemDrop().getItemStack().equals(t.getItemStack()))
				.findFirst();
		if (optionalTool.isEmpty()) {
			return;
		}
		var tool = optionalTool.get();
		if (evt.getPlayer().equals(tool.getOwner())) {
			detachFromOwner(tool);
		}
	}

	@EventHandler
	public void onPlayerPickup(EntityPickupItemEvent evt) {
		if (!(evt.getEntity() instanceof Player player)) {
			return;
		}
		var optionalTool = tools.stream()
				.filter(t -> t.getOwner() == null && evt.getItem().getItemStack().equals(t.getItemStack())).findFirst();
		if (optionalTool.isEmpty()) {
			return;
		}
		if (!attachToPlayer(optionalTool.get(), player)) {
			evt.setCancelled(true);
		}
	}
	
	@EventHandler
	public void onInventoryClick(InventoryClickEvent evt) {
		if (!(evt.getWhoClicked() instanceof Player player) || evt.getAction() != InventoryAction.PICKUP_HALF) {
			return;
		}
		var optWrap = Ioc.resolve(WrappingModule.class).getWrapperOptional(player, PreparationPhasePlayerWrapper.class);
		if (optWrap.isEmpty() || optWrap.get().getMenu().getInventory() != evt.getInventory()) {
			return;
		}
		var match = tools.stream().filter(t -> t.getItemStack().equals(evt.getCurrentItem())).findFirst();
		if (match.isEmpty()) {
			return;
		}
		var wrap = optWrap.get();
		remove(match.get());
		wrap.setMoney(wrap.getMoney() + type.getPrice());
	}
}
