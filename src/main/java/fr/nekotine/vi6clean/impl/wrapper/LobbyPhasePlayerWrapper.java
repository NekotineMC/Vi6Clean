package fr.nekotine.vi6clean.impl.wrapper;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import fr.nekotine.core.NekotineCore;
import fr.nekotine.core.inventory.menu.MenuInventory;
import fr.nekotine.core.inventory.menu.item.BooleanInputMenuItem;
import fr.nekotine.core.inventory.menu.layout.BorderMenuLayout;
import fr.nekotine.core.inventory.menu.layout.WrapMenuLayout;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.core.wrapper.WrapperBase;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.Vi6Main;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.game.phase.Vi6PhaseLobby;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class LobbyPhasePlayerWrapper extends WrapperBase<Player> {
	
	private MenuInventory menu;
	
	private boolean readyForNextPhase;
	
	public LobbyPhasePlayerWrapper(Player wrapped) {
		super(wrapped);
		var playerWrapper = getParentWrapper();
		var changeTeamItem = new BooleanInputMenuItem(
				ItemStackUtil.make(Material.RED_BANNER, Component.text("Voleur", NamedTextColor.RED)),
				ItemStackUtil.make(Material.BLUE_BANNER, Component.text("Garde", NamedTextColor.BLUE)),
				playerWrapper::isThief,
				(isThief) -> {
					var game = Vi6Main.IOC.resolve(Vi6Game.class);
					if (isThief) {
						game.addPlayerInThiefs(wrapped);
					}else {
						game.addPlayerInGuards(wrapped);
					}
				});
		var readyItem = new BooleanInputMenuItem(ItemStackUtil.make(Material.EMERALD_BLOCK, Component.text("Prêt", NamedTextColor.GREEN)),
				ItemStackUtil.make(Material.REDSTONE_BLOCK, Component.text("En attente", NamedTextColor.RED)),
				this::isReadyForNextPhase,
				this::setReadyForNextPhase);
		var wrapLayout = new WrapMenuLayout();
		wrapLayout.addMenuElement(readyItem);
		wrapLayout.addMenuElement(changeTeamItem);
		var border = new BorderMenuLayout(ItemStackUtil.make(Material.GREEN_STAINED_GLASS_PANE,Component.empty()), wrapLayout);
		menu = new MenuInventory(border,3);
	}
	
	public PlayerWrapper getParentWrapper() {
		return NekotineCore.MODULES.get(WrappingModule.class).getWrapper(wrapped, PlayerWrapper.class);
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
		this.readyForNextPhase = readyForNextPhase;
		if (readyForNextPhase) {
			Vi6Main.IOC.resolve(Vi6Game.class).getPhaseMachine().getPhase(Vi6PhaseLobby.class).checkForCompletion();
		}
	}
	
}
