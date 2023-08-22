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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class PreparationPhasePlayerWrapper extends WrapperBase<Player> {

	private boolean isReady;
	
	private MenuInventory menu;
	
	public PreparationPhasePlayerWrapper(Player wrapped) {
		super(wrapped);
		var launchGameItem = new BooleanInputMenuItem(ItemStackUtil.make(Material.EMERALD_BLOCK, Component.text("Prêt", NamedTextColor.GREEN)),
				ItemStackUtil.make(Material.REDSTONE_BLOCK, Component.text("En attente", NamedTextColor.RED)),
				this::setReady);
		var wrapLayout = new WrapMenuLayout();
		wrapLayout.addMenuElement(launchGameItem);
		var border = new BorderMenuLayout(ItemStackUtil.make(Material.ORANGE_STAINED_GLASS_PANE,Component.empty()), wrapLayout);
		menu = new MenuInventory(border,3);
	}

	public boolean isReady() {
		return isReady;
	}

	public void setReady(boolean isReady) {
		this.isReady = isReady;
		if (this.isReady && Vi6Main.IOC.resolve(Vi6Game.class).getCurrentPhase() instanceof Vi6PhasePreparation prepPhase) {
			prepPhase.checkForCompletion();
		}
	}

	public MenuInventory getMenu() {
		return menu;
	}

	public void setMenu(MenuInventory menu) {
		this.menu = menu;
	}
	
}
