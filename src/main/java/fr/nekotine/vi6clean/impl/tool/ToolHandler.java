package fr.nekotine.vi6clean.impl.tool;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import fr.nekotine.core.configuration.ConfigurationUtil;
import fr.nekotine.core.inventory.ItemStackBuilder;
import fr.nekotine.core.inventory.menu.element.ActionMenuItem;
import fr.nekotine.core.inventory.menu.element.MenuElement;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.logging.NekotineLogger;
import fr.nekotine.core.text.TextModule;
import fr.nekotine.core.text.style.NekotineStyles;
import fr.nekotine.core.text.tree.Leaf;
import fr.nekotine.core.util.AssertUtil;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.util.InventoryUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6Styles;
import fr.nekotine.vi6clean.constant.Vi6Team;
import fr.nekotine.vi6clean.impl.wrapper.PreparationPhasePlayerWrapper;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * Gestionnaire d'outil pour une partie de Vi6. Une instance par partie.
 * L'objectif de cette classe est de fournir le comportemant attendu à tous les
 * outils d'un même type.
 *
 * @author XxGoldenbluexX
 * @param <T>
 */
public abstract class ToolHandler<T extends Tool> implements Listener {

	public static NamespacedKey TOOL_TYPE_KEY = new NamespacedKey(Ioc.resolve(JavaPlugin.class), "tool/type");

	public static NamespacedKey TOOL_ID_KEY = new NamespacedKey(Ioc.resolve(JavaPlugin.class), "tool/id");

	protected final ComponentLogger logger = NekotineLogger.make();

	private final Function<ToolHandler<?>, T> toolSupplier;

	private final Map<Integer, T> tools = new HashMap<>();

	private boolean active;

	private final String toolCode;

	// configuartion

	private Configuration configuration;

	private final Component displayName;

	private final List<Component> lore;

	private final int price;

	private final int limite;

	private final boolean isRune;

	private final Set<Vi6Team> forTeams = new HashSet<>(2);

	public ToolHandler(Function<ToolHandler<?>, T> toolSupplier) {
		this.toolSupplier = toolSupplier;

		// Fetch code on annotation
		var an = getClass().getDeclaredAnnotation(ToolCode.class);
		toolCode = an.value();

		// load configuration
		try {
			if (Ioc.resolve(JavaPlugin.class).getConfig().getBoolean("replace_tool_configs", false)) {
				configuration = ConfigurationUtil.overrideAndLoadYaml("tools/" + toolCode + ".yml",
						"/tools/" + toolCode + ".yml");
			} else {
				configuration = ConfigurationUtil.updateAndLoadYaml("tools/" + toolCode + ".yml",
						"/tools/" + toolCode + ".yml");
			}
		} catch (IOException e) {
			logger.error("Erreur lors du chargement du fichier de configuration de l'outil " + toolCode, e);
			configuration = new YamlConfiguration();
		}

		// fetch values from configuration
		isRune = configuration.getBoolean("is_rune", false);
		limite = configuration.getInt("amount_limit", -1);
		price = configuration.getInt("price", 9999);
		displayName = Ioc
				.resolve(TextModule.class).message(Leaf.builder()
						.addLine(configuration.getString("display_name", "Unnamed")).addStyle(NekotineStyles.NEKOTINE))
				.buildFirst();
		// fetch lore
		var loreTagResolvers = new LinkedList<TagResolver>();
		for (var key : configuration.getKeys(false)) {
			if (key.equalsIgnoreCase("lore")) {
				continue;
			}
			var value = configuration.get(key, key);
			loreTagResolvers.add(Placeholder.unparsed(key, value.toString()));
		}
		var serializedLore = configuration.getStringList("lore");
		lore = Ioc.resolve(TextModule.class).message(Leaf.builder().addLine(serializedLore.toArray(String[]::new))
				.addStyle(Vi6Styles.TOOL_LORE).addStyle(loreTagResolvers.toArray(TagResolver[]::new))).build();
		// fetch available teams
		var teams = configuration.getStringList("team");
		if (teams.stream().anyMatch(s -> s.equalsIgnoreCase("guard"))) {
			forTeams.add(Vi6Team.GUARD);
		}
		if (teams.stream().anyMatch(s -> s.equalsIgnoreCase("thief"))) {
			forTeams.add(Vi6Team.THIEF);
		}
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

	public Configuration getConfiguration() {
		return configuration;
	}

	public final void removeAll() {
		tools.values().stream().collect(Collectors.toList()).forEach(this::remove);
		tools.clear();
	}

	@EventHandler
	public void onPlayerDrop(PlayerDropItemEvent evt) {
		var tool = getToolFromItem(evt.getItemDrop().getItemStack());
		if (tool != null) {
			detachFromOwner(tool);
		}
	}

	@EventHandler
	public void onPlayerPickup(EntityPickupItemEvent evt) {
		if (!(evt.getEntity() instanceof Player player)) {
			return;
		}

		var tool = getToolFromItem(evt.getItem().getItemStack());
		if (tool == null) {
			return;
		}
		// On limite le nombre d'items de ce type dans l'inventaire du joueur
		if (InventoryUtil.taggedItems(player.getInventory(), TOOL_TYPE_KEY, toolCode).size() >= limite && limite >= 0) {
			evt.setCancelled(true);
			return;
		}
		attachToPlayer(tool, player, false);
	}

	public final T makeNewTool() {
		var tool = toolSupplier.apply(this);
		tools.put(tool.getId(), tool);
		return tool;
	}

	/** Try to attach tool to player. remove previous attachment if existing */
	public final void attachToPlayer(T tool, Player player, boolean needToGiveItem) {
		detachFromOwner(tool);
		tool.setOwner(player);
		var inventory = player.getInventory();
		if (needToGiveItem) {
			var item = makeBaseItem();
			// Add some specification to item
			AssertUtil.nonNull(item);
			if (!item.getPersistentDataContainer().getKeys()
					.containsAll(Set.of(ToolHandler.TOOL_TYPE_KEY, ToolHandler.TOOL_ID_KEY))) {
				// Edit item's content
				item.editPersistentDataContainer(pdc -> {
					pdc.set(ToolHandler.TOOL_TYPE_KEY, PersistentDataType.STRING, toolCode);
					pdc.set(ToolHandler.TOOL_ID_KEY, PersistentDataType.INTEGER, tool.getId());
				});
				if (item.hasData(DataComponentTypes.EQUIPPABLE)) {
					var equippable = item.getData(DataComponentTypes.EQUIPPABLE);
					if (!Set.of(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND).contains(equippable.slot())) {
						item.unsetData(DataComponentTypes.EQUIPPABLE);
					}
				}
				if (item.hasData(DataComponentTypes.CONSUMABLE)) {
					item.unsetData(DataComponentTypes.CONSUMABLE);
				}
			}
			inventory.addItem(item);
		}
		onAttachedToPlayer(tool);
	}

	public final void attachToPlayer(T tool, Player player) {
		var needToGive = !InventoryUtil.anyMatch(player.getInventory(), i -> itemMatch(tool, i));
		attachToPlayer(tool, player, needToGive);
	}

	public final void detachFromOwner(T tool) {
		var owner = tool.getOwner();
		if (owner == null) {
			return;
		}
		onDetachFromPlayer(tool);
		InventoryUtil.removeIf(owner.getInventory(), item -> {
			var pdc = item.getPersistentDataContainer();
			return pdc.get(TOOL_TYPE_KEY, PersistentDataType.STRING) == toolCode
					&& pdc.get(TOOL_ID_KEY, PersistentDataType.INTEGER) == tool.getId();
		});
		tool.setOwner(null);
	}

	public final void remove(T tool) {
		detachFromOwner(tool);
		onToolCleanup(tool);
		tools.remove(tool.getId());
	}

	private final void removeGeneric(Tool tool) {
		@SuppressWarnings("unchecked")
		var typed = (T) tool;
		detachFromOwner(typed);
		onToolCleanup(typed);
		tools.remove(typed.getId());
	}

	protected void onStartHandling() {
	}

	protected void onStopHandling() {
	}

	protected abstract void onAttachedToPlayer(T tool);

	protected abstract void onDetachFromPlayer(T tool);

	protected abstract void onToolCleanup(T tool);

	protected ItemStack makeBaseItem() {
		var mat = Material.getMaterial(configuration.getString("shop_icon", Material.BARRIER.name()));
		return new ItemStackBuilder(mat).name(getDisplayName()).lore(getLore()).unstackable().flags(ItemFlag.values())
				.build();
	}

	public Collection<T> getTools() {
		return tools.values();
	}

	public MenuElement makeShopMenuItem() {
		// Shop item
		var shopLore = new LinkedList<Component>(getLore());
		shopLore.add(Component.empty());
		var madeItem = makeBaseItem();
		if (!isRune) {
			shopLore.add(Component.text("Prix: " + price, NamedTextColor.GOLD));
		}
		madeItem.editMeta(meta -> {
			meta.lore(shopLore);
			meta.displayName(getDisplayName());
		});
		//
		return new ActionMenuItem(madeItem, this::tryBuy);
	}

	public List<Component> getLore() {
		return lore;
	}

	public Component getDisplayName() {
		return displayName;
	}

	public int getPrice() {
		return price;
	}

	public Set<Vi6Team> getTeamsAvailableFor() {
		return forTeams;
	}

	public T getToolFromItem(ItemStack item) {
		if (item == null) {
			return null;
		}
		var pdc = item.getPersistentDataContainer();
		if (pdc.get(TOOL_TYPE_KEY, PersistentDataType.STRING) != toolCode) {
			return null;
		}
		var id = pdc.get(TOOL_ID_KEY, PersistentDataType.INTEGER);
		if (id == null) {
			return null;
		}
		return tools.get(id);
	}

	public void tryBuy(InventoryClickEvent evt) {
		if (!(evt.getWhoClicked() instanceof Player player)) {
			return;
		}
		if (InventoryUtil.taggedItems(player.getInventory(), TOOL_TYPE_KEY, toolCode).size() >= limite && limite >= 0) {
			evt.setCancelled(true);
			return;
		}
		var optionalWrap = Ioc.resolve(WrappingModule.class).getWrapperOptional(player,
				PreparationPhasePlayerWrapper.class);
		if (optionalWrap.isEmpty()) {
			return;
		}
		var wrap = optionalWrap.get();
		if (!isRune) {
			if (wrap.getMoney() >= price) {
				wrap.setMoney(wrap.getMoney() - price);
				var tool = makeNewTool();
				attachToPlayer(tool, player);
			}
		} else {
			var r = wrap.getRune();
			if (r != null) {
				r.getHandler().removeGeneric(r);
			}
			var tool = makeNewTool();
			attachToPlayer(tool, player);
			wrap.setRune(tool);
		}
	}

	@EventHandler
	public void onInventoryClick(InventoryClickEvent evt) {
		if (!(evt.getWhoClicked() instanceof Player player) || evt.getAction() != InventoryAction.PICKUP_HALF) {
			return;
		}
		var tool = getToolFromItem(evt.getCurrentItem());
		if (tool == null) {
			return;
		}
		// Check if inventory is the shop menu
		var optWrap = Ioc.resolve(WrappingModule.class).getWrapperOptional(player, PreparationPhasePlayerWrapper.class);
		if (optWrap.isEmpty() || optWrap.get().getMenu().getInventory() != evt.getInventory()) {
			return;
		}
		remove(tool);
		if (!isRune) {
			var wrap = optWrap.get();
			wrap.setMoney(wrap.getMoney() + price);
		}
	}

	@EventHandler
	private void onPlayerDeath(PlayerDeathEvent evt) {
		if (evt.isCancelled()) {
			return;
		}
		tools.values().stream().filter(tool -> evt.getPlayer().equals(tool.getOwner())).forEach(this::detachFromOwner);
	}

	public boolean isActive() {
		return active;
	}

	public boolean isRune() {
		return isRune;
	}

	public String getToolCode() {
		return toolCode;
	}

	public void editItem(Tool tool, Consumer<ItemStack> action) {
		AssertUtil.nonNull(tool);
		var owner = tool.getOwner();
		if (owner == null) {
			return;
		}
		Arrays.stream(owner.getInventory().getContents()).filter(i -> i != null && itemMatch(tool, i)).forEach(action);
	}

	public final boolean itemMatch(Tool tool, ItemStack item) {
		if (item == null) {
			return false;
		}
		var pdc = item.getPersistentDataContainer();
		return pdc.get(ToolHandler.TOOL_TYPE_KEY, PersistentDataType.STRING) == toolCode
				&& pdc.get(ToolHandler.TOOL_ID_KEY, PersistentDataType.INTEGER) == tool.getId();
	}
}
