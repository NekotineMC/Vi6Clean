package fr.nekotine.vi6clean.impl.tool;

import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.NekotineCore;
import fr.nekotine.core.inventory.menu.element.ActionMenuItem;
import fr.nekotine.core.inventory.menu.element.MenuElement;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6ToolLoreText;
import fr.nekotine.vi6clean.impl.tool.personal.doublejump.DoubleJumpHandler;
import fr.nekotine.vi6clean.impl.tool.personal.invisneak.InviSneakHandler;
import fr.nekotine.vi6clean.impl.tool.personal.lantern.LanternHandler;
import fr.nekotine.vi6clean.impl.tool.personal.omnicaptor.OmniCaptorHandler;
import fr.nekotine.vi6clean.impl.tool.personal.radar.RadarHandler;
import fr.nekotine.vi6clean.impl.tool.personal.regenerator.RegeneratorHandler;
import fr.nekotine.vi6clean.impl.tool.personal.shadow.ShadowHandler;
import fr.nekotine.vi6clean.impl.tool.personal.sonar.SonarHandler;
import fr.nekotine.vi6clean.impl.tool.personal.tazer.TazerHandler;
import fr.nekotine.vi6clean.impl.tool.personal.tracker.TrackerHandler;
import fr.nekotine.vi6clean.impl.tool.personal.warner.WarnerHandler;
import fr.nekotine.vi6clean.impl.tool.personal.watcher.WatcherHandler;
import fr.nekotine.vi6clean.impl.wrapper.PreparationPhasePlayerWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public enum ToolType {

	INVISNEAK(
			ItemStackUtil.make(Material.GLASS_PANE, Component.text("InviSneak", NamedTextColor.GOLD), InviSneakHandler.LORE),
			InviSneakHandler::new,
			400, 		// PRICE
			1 			// LIMIT
			),
	OMNICAPTOR(
			ItemStackUtil.make(Material.REDSTONE_TORCH, Component.text("OmniCapteur", NamedTextColor.GOLD), OmniCaptorHandler.LORE),
			OmniCaptorHandler::new,
			250, 		// PRICE
			2 			// LIMIT
			),
	SONAR(
			ItemStackUtil.make(Material.CLOCK, Component.text("Sonar", NamedTextColor.GOLD), SonarHandler.LORE),
			SonarHandler::new,
			400, 		// PRICE
			1 			// LIMIT
			),
	DOUBLEJUMP(
			ItemStackUtil.make(Material.GOLDEN_BOOTS, Component.text("Double Saut", NamedTextColor.GOLD), Vi6ToolLoreText.DOUBLEJUMP.make()),
			DoubleJumpHandler::new,
			300, 		// PRICE
			1 			// LIMIT
			),
	TAZER(
			ItemStackUtil.make(Material.SHEARS, Component.text("Tazer", NamedTextColor.GOLD), TazerHandler.LORE),
			TazerHandler::new,
			300, 		// PRICE
			1 			// LIMIT
			),
	LANTERN(
			ItemStackUtil.make(Material.LANTERN, Component.text("Lanterne", NamedTextColor.GOLD), LanternHandler.LORE),
			LanternHandler::new,
			200, 		// PRICE
			1 			// LIMIT
			),
	TRACKER (
			ItemStackUtil.make(Material.RECOVERY_COMPASS, Component.text("Traceur", NamedTextColor.GOLD), Vi6ToolLoreText.TRACKER.make()),
			TrackerHandler::new,
			100,		// PRICE
			-1
			),
	RADAR (
			ItemStackUtil.make(Material.CALIBRATED_SCULK_SENSOR, Component.text("Radar", NamedTextColor.GOLD), RadarHandler.LORE),
			RadarHandler::new,
			200,		// PRICE
			1 			// LIMIT
			),
	SHADOW (
			ItemStackUtil.make(Material.WITHER_SKELETON_SKULL, Component.text("Ombre", NamedTextColor.GOLD), Vi6ToolLoreText.SHADOW.make()),
			ShadowHandler::new,
			250,		// PRICE
			2 			// LIMIT
			),
	WATCHER (
			ItemStackUtil.make(Material.SILVERFISH_SPAWN_EGG, Component.text("Observateur", NamedTextColor.GOLD), Vi6ToolLoreText.WATCHER.make()),
			WatcherHandler::new,
			200,		// PRICE
			1 			// LIMIT
			),
	WARNER (
			ItemStackUtil.make(Material.ENDER_EYE, Component.text("Avertisseur", NamedTextColor.GOLD), WarnerHandler.LORE),
			WarnerHandler::new,
			150,		// PRICE
			0
			),
	REGENERATOR (
			ItemStackUtil.make(Material.CAMPFIRE, Component.text("Régénérateur", NamedTextColor.GOLD), RegeneratorHandler.LORE),
			RegeneratorHandler::new,
			150,		// PRICE
			0 			// LIMIT
			),
	;
	
	private ToolHandler<?> handler;
	
	private final MenuElement shopItem;
	
	private final Supplier<ToolHandler<?>> handlerSupplier;
	
	private final int price;
	
	private final int limite;
	
	private ToolType(ItemStack menuItem, Supplier<ToolHandler<?>> handlerSupplier, int price, int limite) {
		var lore = menuItem.lore();
		lore.add(0,Component.empty());
		lore.add(0, Component.text("Prix: "+price,NamedTextColor.GOLD));
		menuItem.lore(lore);
		shopItem = new ActionMenuItem(menuItem, this::tryBuy);
		this.handlerSupplier = handlerSupplier;
		this.price = price;
		this.limite = limite;
	}
	
	public MenuElement getShopMenuItem() {
		return shopItem;
	}
	
	public int getLimite() {
		return limite;
	}
	
	public ToolHandler<?> getHandler(){
		if (handler == null) {
			handler = handlerSupplier.get();
		}
		return handler;
	}

	public int getPrice() {
		return price;
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
		var optionalWrap = NekotineCore.MODULES.get(WrappingModule.class).getWrapperOptional(player, PreparationPhasePlayerWrapper.class);
		if (optionalWrap.isEmpty()) {
			return false;
		}
		var wrap = optionalWrap.get();
		if (wrap.getMoney() >= price && getHandler().attachNewToPlayer(player)) {
			wrap.setMoney(wrap.getMoney() - price);
			return true;
		}
		return false;
	}
	
}
