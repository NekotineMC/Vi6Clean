package fr.nekotine.vi6clean.impl.wrapper;

import java.util.ArrayList;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.inventory.menu.MenuInventory;
import fr.nekotine.core.inventory.menu.element.BooleanInputMenuItem;
import fr.nekotine.core.inventory.menu.element.ComponentDisplayMenuItem;
import fr.nekotine.core.inventory.menu.layout.ToolbarMenuLayout;
import fr.nekotine.core.inventory.menu.layout.WrapMenuLayout;
import fr.nekotine.core.inventory.menu.layout.ToolbarMenuLayout.Direction;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.core.wrapper.WrapperBase;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.game.phase.Vi6PhasePreparation;
import fr.nekotine.vi6clean.impl.map.ThiefSpawn;
import fr.nekotine.vi6clean.impl.tool.ToolHandlerContainer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class PreparationPhasePlayerWrapper extends WrapperBase<Player> {

	private int money = 1000;
	
	private MenuInventory menu;
	
	private boolean readyForNextPhase;
	
	private ThiefSpawn selectedSpawn;
	
	private ComponentDisplayMenuItem moneyIndicator;
	
	public PreparationPhasePlayerWrapper(Player wrapped) {
		super(wrapped);
		if (wrapped==null) {
			return;
		}
		moneyIndicator = new ComponentDisplayMenuItem(new ItemStack(Material.GOLD_INGOT), this::getMoneyDisplay);
		var readyItem = new BooleanInputMenuItem(ItemStackUtil.make(Material.EMERALD_BLOCK, Component.text("Prêt", NamedTextColor.GREEN)),
				ItemStackUtil.make(Material.REDSTONE_BLOCK, Component.text("En attente", NamedTextColor.RED)),
				this::isReadyForNextPhase,
				this::setReadyForNextPhase);
		var wrapLayout = new WrapMenuLayout();
		var team =  Ioc.resolve(WrappingModule.class).getWrapper(wrapped, PlayerWrapper.class).getTeam();
		for (var tool : Ioc.resolve(ToolHandlerContainer.class).getHandlers().stream()
				.filter(t -> t.getTeamsAvailableFor().contains(team) && !t.isRune())
				.collect(Collectors.toCollection(ArrayList::new))){
			wrapLayout.addElement(tool.getShopMenuItem());
		}
		var toolbarRunes = new ToolbarMenuLayout(ItemStackUtil.make(Material.BLUE_STAINED_GLASS_PANE,Component.empty()), wrapLayout, Direction.DOWN);
		var toolbar = new ToolbarMenuLayout(ItemStackUtil.make(Material.ORANGE_STAINED_GLASS_PANE,Component.empty()), toolbarRunes);
		toolbar.addTool(readyItem);
		toolbar.addTool(moneyIndicator);
		toolbarRunes.addTool(readyItem);
		toolbarRunes.addTool(moneyIndicator);
		menu = new MenuInventory(toolbar,6);
	}

	public MenuInventory getMenu() {
		return menu;
	}

	public void setMenu(MenuInventory menu) {
		this.menu = menu;
	}

	public boolean isReadyForNextPhase() {
		return readyForNextPhase;
	}

	public void setReadyForNextPhase(boolean readyForNextPhase) {
		if (readyForNextPhase && selectedSpawn == null && !getParentWrapper().isInside()) {
			wrapped.sendMessage(Component.text("Vous devez sélectionner une entrée.", NamedTextColor.RED));
			return;
		}
		this.readyForNextPhase = readyForNextPhase;
		if (readyForNextPhase) {
			Ioc.resolve(Vi6Game.class).getPhaseMachine().getPhase(Vi6PhasePreparation.class).checkForCompletion();
		}
	}

	public ThiefSpawn getSelectedSpawn() {
		return selectedSpawn;
	}

	public void setSelectedSpawn(ThiefSpawn selectedSpawn) {
		this.selectedSpawn = selectedSpawn;
		wrapped.sendMessage(Component.text("Entrée "+selectedSpawn.getName()+" sélectionnée", NamedTextColor.DARK_GREEN));
	}
	
	public InMapPhasePlayerWrapper getParentWrapper() {
		return Ioc.resolve(WrappingModule.class).getWrapper(wrapped, InMapPhasePlayerWrapper.class);
	}

	public int getMoney() {
		return money;
	}
	
	public Component getMoneyDisplay() {
		return Component.text("Argent: "+money, NamedTextColor.GOLD);
	}

	public void setMoney(int money) {
		this.money = money;
		moneyIndicator.askRedraw();
	}
}
