package fr.nekotine.vi6clean.impl.tool;

import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.NekotineCore;
import fr.nekotine.core.inventory.menu.element.ActionMenuItem;
import fr.nekotine.core.inventory.menu.element.MenuElement;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.impl.tool.personal.InviSneakHandler;
import fr.nekotine.vi6clean.impl.wrapper.PreparationPhasePlayerWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public enum ToolType {

	INVISNEAK(
			ItemStackUtil.make(Material.GLASS_PANE, Component.text("InviSneak", NamedTextColor.GOLD), InviSneakHandler.LORE),
			InviSneakHandler::new,
			400, 		// PRICE
			1 			// LIMIT
			);
	
	private ToolHandler<?> handler;
	
	private final MenuElement shopItem;
	
	private final Supplier<ToolHandler<?>> handlerSupplier;
	
	private final int price;
	
	private final int limite;
	
	private ToolType(ItemStack menuItem, Supplier<ToolHandler<?>> handlerSupplier, int price, int limite) {
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
	public boolean tryBuy(Player player) {
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