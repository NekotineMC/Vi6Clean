package fr.nekotine.vi6clean.impl.wrapper;
/*
import java.util.Map;
import java.util.WeakHashMap;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import fr.nekotine.core.inventory.menu.MenuInventory;
import fr.nekotine.core.util.MathUtil;
import fr.nekotine.core.wrapper.WrapperBase;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;

public class PlayerWrapper extends WrapperBase<Player> {

	private final Map<ToolHandler<?>, Integer> selectedItems = new WeakHashMap<>();
	
	private int money = -1;
	
	private boolean isReadyForInfiltration = false;
	
	private MenuInventory shopMenu;
	
	
	public PlayerWrapper(Player wrapped) {
		super(wrapped);
	}
	
	
	public Map<ToolHandler<?>, Integer> getSelectedItemsMap(){
		return selectedItems;
	}
	
	public int getMoney() {
		return money;
	}
	
	public void setMoney(int money) {
		this.money = MathUtil.clamp(money, 0, Integer.MAX_VALUE);
	}
	
	public void addMoney(int value) {
		money = MathUtil.clamp(money + value, 0, Integer.MAX_VALUE);
	}
	
	public boolean isReadyForInfiltration() {
		return isReadyForInfiltration;
	}
	
	public void setReadyForInfiltration(boolean readyForInfiltration) {
		isReadyForInfiltration = readyForInfiltration;
	}

	public @Nullable MenuInventory getShopMenu() {
		return shopMenu;
	}
	
	public void setShopMenu(@Nullable MenuInventory shopMenu) {
		this.shopMenu = shopMenu;
	}
	
}
*/