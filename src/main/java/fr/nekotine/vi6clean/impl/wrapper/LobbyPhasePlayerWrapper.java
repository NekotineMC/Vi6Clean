package fr.nekotine.vi6clean.impl.wrapper;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;

import fr.nekotine.core.inventory.ItemStackBuilder;
import fr.nekotine.core.inventory.menu.MenuInventory;
import fr.nekotine.core.inventory.menu.element.BooleanInputMenuItem;
import fr.nekotine.core.inventory.menu.layout.BorderMenuLayout;
import fr.nekotine.core.inventory.menu.layout.WrapMenuLayout;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.core.wrapper.WrapperBase;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.game.phase.Vi6PhaseLobby;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class LobbyPhasePlayerWrapper extends WrapperBase<Player> {
	
	private MenuInventory menu;
	
	private boolean readyForNextPhase;
	
	public LobbyPhasePlayerWrapper(Player wrapped) {
		super(wrapped);
		var game = Ioc.resolve(Vi6Game.class);
		var playerWrapper = getParentWrapper();
		var changeTeamItem = new BooleanInputMenuItem(
				ItemStackUtil.make(Material.RED_BANNER, Component.text("Voleur", NamedTextColor.RED)),
				ItemStackUtil.make(Material.BLUE_BANNER, Component.text("Garde", NamedTextColor.BLUE)),
				playerWrapper::isThief,
				(isThief) -> {
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
		var debugItem = new BooleanInputMenuItem(
				new ItemStackBuilder(Material.GOLDEN_PICKAXE).flags(ItemFlag.values()).enchant().name(Component.text("Débug activé", NamedTextColor.YELLOW)).build(),
				ItemStackUtil.make(Material.GOLDEN_PICKAXE, Component.text("Débug désactivé", NamedTextColor.GRAY)),
				game::isDebug,
				game::setDebug);
		var wrapLayout = new WrapMenuLayout();
		wrapLayout.addElement(readyItem);
		wrapLayout.addElement(changeTeamItem);
		wrapLayout.addElement(debugItem);
		var border = new BorderMenuLayout(ItemStackUtil.make(Material.GREEN_STAINED_GLASS_PANE,Component.empty()), wrapLayout);
		menu = new MenuInventory(border,3);
	}
	
	public PlayerWrapper getParentWrapper() {
		return Ioc.resolve(WrappingModule.class).getWrapper(wrapped, PlayerWrapper.class);
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
		var lobby = Ioc.resolve(Vi6Game.class).getPhaseMachine().getPhase(Vi6PhaseLobby.class);
		lobby.getSidebarObjective().getScore(wrapped).setScore(readyForNextPhase?1:0);
		if (readyForNextPhase) {
			lobby.checkForCompletion();
		}
	}
	
}
