package fr.nekotine.vi6clean.impl.wrapper;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import fr.nekotine.core.inventory.menu.MenuInventory;
import fr.nekotine.core.inventory.menu.item.BooleanInputMenuItem;
import fr.nekotine.core.inventory.menu.layout.BorderMenuLayout;
import fr.nekotine.core.inventory.menu.layout.WrapMenuLayout;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.core.wrapper.WrapperBase;
import fr.nekotine.vi6clean.Vi6Main;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.game.phase.Vi6PhasePreparation;
import fr.nekotine.vi6clean.impl.map.Entrance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class PreparationPhasePlayerWrapper extends WrapperBase<Player> {

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
		wrapLayout.addMenuElement(readyItem);
		var border = new BorderMenuLayout(ItemStackUtil.make(Material.ORANGE_STAINED_GLASS_PANE,Component.empty()), wrapLayout);
		menu = new MenuInventory(border,3);
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
		if (readyForNextPhase && selectedEntrance == null) {
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
	
}
