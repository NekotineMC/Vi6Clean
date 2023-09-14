package fr.nekotine.vi6clean.impl.wrapper;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import fr.nekotine.core.NekotineCore;
import fr.nekotine.core.inventory.menu.MenuInventory;
import fr.nekotine.core.inventory.menu.item.BooleanInputMenuItem;
import fr.nekotine.core.inventory.menu.layout.ToolbarMenuLayout;
import fr.nekotine.core.inventory.menu.layout.WrapMenuLayout;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.core.wrapper.WrapperBase;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.Vi6Main;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.game.phase.Vi6PhasePreparation;
import fr.nekotine.vi6clean.impl.map.Entrance;
import fr.nekotine.vi6clean.impl.tool.ToolType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class PreparationPhasePlayerWrapper extends WrapperBase<Player> {

	private int money = 1000;
	
	private MenuInventory menu;
	
	private boolean readyForNextPhase;
	
	private Entrance selectedEntrance;
	
	public PreparationPhasePlayerWrapper(Player wrapped) {
		super(wrapped);
		var readyItem = new BooleanInputMenuItem(ItemStackUtil.make(Material.EMERALD_BLOCK, Component.text("Prêt", NamedTextColor.GREEN)),
				ItemStackUtil.make(Material.REDSTONE_BLOCK, Component.text("En attente", NamedTextColor.RED)),
				this::isReadyForNextPhase,
				this::setReadyForNextPhase);
		var wrapLayout = new WrapMenuLayout();
		for (var tool : ToolType.values()){
			wrapLayout.addMenuElement(tool.getShopMenuItem());
		}
		var toolbar = new ToolbarMenuLayout(ItemStackUtil.make(Material.ORANGE_STAINED_GLASS_PANE,Component.empty()), wrapLayout);
		toolbar.addTool(readyItem);
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
		if (readyForNextPhase && selectedEntrance == null && !getParentWrapper().isInside()) {
			wrapped.sendMessage(Component.text("Vous devez sélectionner une entrée.", NamedTextColor.RED));
			return;
		}
		this.readyForNextPhase = readyForNextPhase;
		if (readyForNextPhase) {
			Vi6Main.IOC.resolve(Vi6Game.class).getPhaseMachine().getPhase(Vi6PhasePreparation.class).checkForCompletion();
		}
	}

	public Entrance getSelectedEntrance() {
		return selectedEntrance;
	}

	public void setSelectedEntrance(Entrance selectedEntrance) {
		this.selectedEntrance = selectedEntrance;
		wrapped.sendMessage(Component.text("Entrée "+selectedEntrance.getName()+" sélectionnée", NamedTextColor.DARK_GREEN));
	}
	
	public InMapPhasePlayerWrapper getParentWrapper() {
		return NekotineCore.MODULES.get(WrappingModule.class).getWrapper(wrapped, InMapPhasePlayerWrapper.class);
	}

	public int getMoney() {
		return money;
	}

	public void setMoney(int money) {
		this.money = money;
	}
	
}
