package fr.nekotine.vi6clean.impl.tool;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;

import org.bukkit.Material;
import org.bukkit.configuration.Configuration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.EquipmentSlot;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;

import fr.nekotine.core.inventory.menu.element.ActionMenuItem;
import fr.nekotine.core.inventory.menu.element.MenuElement;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.logging.NekotineLogger;
import fr.nekotine.core.text.TextModule;
import fr.nekotine.core.text.tree.Leaf;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6Styles;
import fr.nekotine.vi6clean.impl.wrapper.PreparationPhasePlayerWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

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

	private final Supplier<T> toolSupplier;

	private final Collection<T> tools = new LinkedList<>();
	
	private final Random random = new Random();
	
	private String code;
	
	private final MenuElement shopItem;
	
	private Material iconMaterial;
	
	private final Component displayName;
	
	private final int price;
	
	private final int limite;
	
	protected final List<Component> lore;
	
	private boolean active;

	public ToolHandler(Supplier<T> toolSupplier) {
		this.toolSupplier = toolSupplier;
		var an = getClass().getDeclaredAnnotation(ToolCode.class);
		code = an.value();
		var config = Ioc.resolve(Configuration.class).getConfigurationSection("tool."+code);
		// Lore construction
		var loreTagResolvers = new LinkedList<TagResolver>();
		for (var key : config.getKeys(false)) {
			var value = config.get(key,key);
			loreTagResolvers.add(Placeholder.unparsed(key, value.toString()));
		}
		var serializedLore = config.getStringList("lore");
		lore = Ioc.resolve(TextModule.class).message(
			Leaf.builder()
				.addLine(serializedLore.toArray(String[]::new))
				.addStyle(Vi6Styles.TOOL_LORE)
				.addStyle(loreTagResolvers.toArray(TagResolver[]::new))
			).build();
		// Values
		limite = config.getInt("amount_limit", -1);
		price = config.getInt("price", 9999);
		displayName = Ioc.resolve(TextModule.class).message(
			Leaf.builder()
				.addLine(config.getString("display_name", "Unnamed"))
			).buildFirst();
		// Shop item
		iconMaterial = Material.getMaterial(config.getString("shop_icon", Material.BARRIER.name()));
		var shopLore = new LinkedList<Component>(lore);
		shopLore.add(Component.empty());
		shopLore.add(Component.text("Prix: "+price,NamedTextColor.GOLD));
		var menuItem = ItemStackUtil.make(iconMaterial, displayName, shopLore.toArray(Component[]::new));
		shopItem = new ActionMenuItem(menuItem, this::tryBuy);
	}

	public final void startHandling() {
		if (active) {
			return;
		}
		onStartHandling();
		EventUtil.register(this);
		active = true;
	}

	public final void stopHandling() {
		if (!active) {
			return;
		}
		EventUtil.unregister(this);
		onStopHandling();
		active = false;
	}

	public final void removeAll() {
		for (var tool : tools) {
			try {
				detachFromOwner(tool);
			}catch(Exception e) {
				logger.log(Level.SEVERE, "Une erreur est survenue lors du detachement d'un outil "+code, e);
			}
			try {
				tool.cleanup();
			}catch(Exception e) {
				logger.log(Level.SEVERE, "Une erreur est survenue lors du cleanup d'un outil "+code, e);
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
		if (limite >=0 && limite <= tools.stream().filter(t -> player.equals(t.getOwner())).count()) {
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
	
	public MenuElement getShopMenuItem() {
		return shopItem;
	}
	
	/**
	 * 
	 * @param player
	 * @return buy succesfull
	 */
	public boolean tryBuy(InventoryClickEvent evt) {
		if (!(evt.getWhoClicked() instanceof Player player)) {
			return false;
		}
		var optionalWrap = Ioc.resolve(WrappingModule.class).getWrapperOptional(player, PreparationPhasePlayerWrapper.class);
		if (optionalWrap.isEmpty()) {
			return false;
		}
		var wrap = optionalWrap.get();
		if (wrap.getMoney() >= price && attachNewToPlayer(player)) {
			wrap.setMoney(wrap.getMoney() - price);
			return true;
		}
		return false;
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
		var match = tools.stream().filter(t -> t.getItemStack().isSimilar(evt.getCurrentItem()) && t.getOwner() == evt.getWhoClicked()).findFirst();
		if (match.isEmpty()) {
			return;
		}
		var wrap = optWrap.get();
		remove(match.get());
		wrap.setMoney(wrap.getMoney() + price);
	}
	
	@EventHandler
	public void onPlayerArmorChange(PlayerArmorChangeEvent evt) {
		var match = tools.stream().filter(t -> t.getItemStack().equals(evt.getNewItem())).findFirst();
		if (match.isEmpty()) {
			return;
		}
		var player = evt.getPlayer();
		var storage = player.getInventory().getStorageContents();
		var emptySlots = IntStream
			.range(0, storage.length)
			.filter(i -> storage[i] == null)
			.mapToObj(i -> i)
			.toArray();
		if(emptySlots.length == 0) {
			return;
		}
		var slot = (int)emptySlots[random.nextInt(0, emptySlots.length)];
		player.getEquipment().setItem(EquipmentSlot.valueOf(evt.getSlotType().name()), evt.getOldItem(), true);
		player.getInventory().setItem(slot, evt.getNewItem());
	}

	public boolean isActive() {
		return active;
	}
}
