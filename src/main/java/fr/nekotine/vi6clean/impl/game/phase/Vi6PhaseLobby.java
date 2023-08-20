package fr.nekotine.vi6clean.impl.game.phase;

import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.RenderType;

import fr.nekotine.core.NekotineCore;
import fr.nekotine.core.game.phase.CollectionPhase;
import fr.nekotine.core.game.phase.eventargs.PhaseFailureEventArgs;
import fr.nekotine.core.inventory.menu.MenuInventory;
import fr.nekotine.core.inventory.menu.item.ActionMenuItem;
import fr.nekotine.core.inventory.menu.layout.BorderMenuLayout;
import fr.nekotine.core.inventory.menu.layout.WrapMenuLayout;
import fr.nekotine.core.usable.Usable;
import fr.nekotine.core.usable.UsableModule;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.vi6clean.Vi6Main;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class Vi6PhaseLobby extends CollectionPhase<Player>{

	private Objective scoreboardPlayerListingObjective;
	
	private MenuInventory lobbyMenu;
	
	private Usable openMenuUsable;
	
	public Vi6PhaseLobby(Runnable onSuccess, Consumer<PhaseFailureEventArgs> onFailure, Supplier<Stream<Player>> source) {
		super(onSuccess, onFailure, source);
	}
	
	public Vi6PhaseLobby(Runnable onSuccess, Consumer<PhaseFailureEventArgs> onFailure) {
		super(onSuccess, onFailure);
	}
	
	public Vi6PhaseLobby(Runnable onSuccess) {
		super(onSuccess);
	}
	
	public Vi6PhaseLobby(Consumer<PhaseFailureEventArgs> onFailure) {
		super(onFailure);
	}
	
	@Override
	protected void globalSetup() {
		var scoreboard = Vi6Main.IOC.resolve(Vi6Game.class).getScoreboard();
		scoreboardPlayerListingObjective = scoreboard.getObjective("playerListing");
		if (scoreboardPlayerListingObjective == null) {
			scoreboardPlayerListingObjective = scoreboard.registerNewObjective("playerListing",
					Criteria.DUMMY,
					Component.text("Liste des joueurs", NamedTextColor.GOLD),
					RenderType.INTEGER);
		}
		scoreboardPlayerListingObjective.setDisplaySlot(DisplaySlot.SIDEBAR);
		setupMenu();
		openMenuUsable = new Usable(ItemStackUtil.make(Material.BEACON, Component.text("Menu Vi6", NamedTextColor.GOLD))) {
			@Override
			protected void OnInteract(PlayerInteractEvent e) {
				lobbyMenu.displayTo(e.getPlayer());
				e.setCancelled(true);
			}
			
			@Override
			protected void OnDrop(PlayerDropItemEvent e) {
				lobbyMenu.displayTo(e.getPlayer());
				e.setCancelled(true);
			}
		};
		NekotineCore.MODULES.get(UsableModule.class).register(openMenuUsable);
	}

	@Override
	protected void globalTearDown() {
		scoreboardPlayerListingObjective.unregister();
		scoreboardPlayerListingObjective = null;
	}

	@Override
	public void itemSetup(Player item) {
		scoreboardPlayerListingObjective.getScore(item).setScore(0);
		item.getInventory().addItem(openMenuUsable.getItemStack());
	}

	@Override
	public void itemTearDown(Player item) {
		item.getInventory().clear();
	}
	
	private void setupMenu() {
		var launchGameItem = new ActionMenuItem(ItemStackUtil.make(Material.SUNFLOWER, Component.text("Lancer la partie", NamedTextColor.GOLD)), this::complete);
		var wrapLayout = new WrapMenuLayout();
		wrapLayout.addMenuElement(launchGameItem);
		var border = new BorderMenuLayout(ItemStackUtil.make(Material.GREEN_STAINED_GLASS_PANE,Component.empty()), wrapLayout);
		lobbyMenu = new MenuInventory(border,3);
	}

}
