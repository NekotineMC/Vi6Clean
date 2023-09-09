package fr.nekotine.vi6clean.impl.tool;

import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.vi6clean.impl.tool.personal.InviSneakHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public enum ToolType {

	INVISNEAK(
			ItemStackUtil.make(Material.GLASS_PANE, Component.text("InviSneak", NamedTextColor.GOLD), InviSneakHandler.LORE),
			InviSneakHandler::new,
			400, 		// PRICE
			1 			// LIMIT
			);
	
	private final ItemStack menuItem;
	
	private final Supplier<ToolHandler<?>> handlerSupplier;
	
	private final int price;
	
	private final int limite;
	
	private ToolType(ItemStack menuItem, Supplier<ToolHandler<?>> handlerSupplier, int price, int limite) {
		this.menuItem = menuItem;
		this.handlerSupplier = handlerSupplier;
		this.price = price;
		this.limite = limite;
	}
	
	public ItemStack getMenuItem() {
		return menuItem;
	}
	
	public int getLimite() {
		return limite;
	}
	
	public Supplier<ToolHandler<?>> getHandlerSupplier(){
		return handlerSupplier;
	}

	public int getPrice() {
		return price;
	}
	
}
