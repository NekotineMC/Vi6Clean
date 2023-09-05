package fr.nekotine.vi6clean.impl.game.phase;

import java.util.LinkedList;
import java.util.List;

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
import fr.nekotine.core.game.phase.IPhaseMachine;
import fr.nekotine.core.state.ItemState;
import fr.nekotine.core.state.ItemWrappingState;
import fr.nekotine.core.usable.Usable;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.core.util.collection.ObservableCollection;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.Vi6Main;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.wrapper.LobbyPhasePlayerWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class Vi6PhaseLobby extends CollectionPhase<Vi6PhaseGlobal, Player>{

	private Objective scoreboardPlayerListingObjective;
	
	private Usable openMenuUsable;
	
	public Vi6PhaseLobby(IPhaseMachine machine) {
		super(machine);
	}
	
	@Override
	public Class<Vi6PhaseGlobal> getParentType() {
		return Vi6PhaseGlobal.class;
	}

	@Override
	public ObservableCollection<Player> getItemCollection() {
		return Vi6Main.IOC.resolve(Vi6Game.class).getPlayerList();
	}
	
	@Override
	protected void globalSetup(Object inputData) {
		var scoreboard = Vi6Main.IOC.resolve(Vi6Game.class).getScoreboard();
		scoreboardPlayerListingObjective = scoreboard.getObjective("playerListing");
		if (scoreboardPlayerListingObjective == null) {
			scoreboardPlayerListingObjective = scoreboard.registerNewObjective("playerListing",
					Criteria.DUMMY,
					Component.text("Liste des joueurs", NamedTextColor.GOLD),
					RenderType.INTEGER);
		}
		scoreboardPlayerListingObjective.setDisplaySlot(DisplaySlot.SIDEBAR);
		openMenuUsable = new Usable(ItemStackUtil.make(Material.BEACON, Component.text("Menu Vi6", NamedTextColor.GOLD))) {
			@Override
			protected void OnInteract(PlayerInteractEvent e) {
				NekotineCore.MODULES.get(WrappingModule.class).getWrapper(e.getPlayer(), LobbyPhasePlayerWrapper.class).getMenu().displayTo(e.getPlayer());
				e.setCancelled(true);
			}
			
			@Override
			protected void OnDrop(PlayerDropItemEvent e) {
				NekotineCore.MODULES.get(WrappingModule.class).getWrapper(e.getPlayer(), LobbyPhasePlayerWrapper.class).getMenu().displayTo(e.getPlayer());
				e.setCancelled(true);
			}
		}.register();
	}

	@Override
	protected void globalTearDown() {
		scoreboardPlayerListingObjective.unregister();
		scoreboardPlayerListingObjective = null;
		openMenuUsable.unregister();
	}

	@Override
	public void itemSetup(Player item) {
		var wrappingModule = NekotineCore.MODULES.get(WrappingModule.class);
		wrappingModule.getWrapper(item, LobbyPhasePlayerWrapper.class).setReadyForNextPhase(false);
		scoreboardPlayerListingObjective.getScore(item).setScore(0);
		item.getInventory().addItem(openMenuUsable.getItemStack());
	}

	@Override
	public void itemTearDown(Player item) {
		scoreboardPlayerListingObjective.getScore(item).resetScore();
		item.getInventory().clear();
	}
	
	@Override
	protected List<ItemState<Player>> makeAppliedItemStates() {
		var list = new LinkedList<ItemState<Player>>();
		list.add(new ItemWrappingState<>(LobbyPhasePlayerWrapper::new));
		return list;
	}

	public void checkForCompletion() {
		var game = Vi6Main.IOC.resolve(Vi6Game.class);
		var wrappingModule = NekotineCore.MODULES.get(WrappingModule.class);
		if (game.getPlayerList().stream().allMatch(p -> wrappingModule.getWrapper(p, LobbyPhasePlayerWrapper.class).isReadyForNextPhase())) {
			complete();
		}
	}

}
