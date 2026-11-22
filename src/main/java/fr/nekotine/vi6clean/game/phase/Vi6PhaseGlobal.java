package fr.nekotine.vi6clean.game.phase;

import java.util.LinkedList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import fr.nekotine.core.game.phase.CollectionPhase;
import fr.nekotine.core.game.phase.IPhaseMachine;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.state.ItemState;
import fr.nekotine.core.state.ItemWrappingState;
import fr.nekotine.core.state.PlayerScoreboardState;
import fr.nekotine.core.state.PlayerSnapshotState;
import fr.nekotine.core.state.RegisteredEventListenerState;
import fr.nekotine.core.state.State;
import fr.nekotine.core.util.EntityUtil;
import fr.nekotine.core.util.collection.ObservableCollection;
import fr.nekotine.vi6clean.game.Vi6Game;
import fr.nekotine.vi6clean.wrapper.PlayerWrapper;

public class Vi6PhaseGlobal extends CollectionPhase<Void, Player> implements Listener {

	private final PotionEffect saturation = new PotionEffect(PotionEffectType.SATURATION, -1, 0, false, false, false);

	public Vi6PhaseGlobal(IPhaseMachine machine) {
		super(machine);
	}

	@Override
	public Class<Void> getParentType() {
		return Void.class;
	}

	@Override
	public ObservableCollection<Player> getItemCollection() {
		return Ioc.resolve(Vi6Game.class).getPlayerList();
	}

	@Override
	protected void globalSetup(Object inputData) {
	}

	@Override
	protected void globalTearDown() {
	}

	@Override
	public void itemSetup(Player item) {
		EntityUtil.clearPotionEffects(item);
		EntityUtil.defaultAllAttributes(item);
		item.getInventory().clear();
		item.addPotionEffect(saturation);
	}

	@Override
	public void itemTearDown(Player item) {
	}

	@Override
	protected List<State> makeAppliedStates() {
		var list = new LinkedList<State>();
		list.add(new RegisteredEventListenerState(this));
		return list;
	}

	@Override
	protected List<ItemState<Player>> makeAppliedItemStates() {
		var game = Ioc.resolve(Vi6Game.class);
		var list = new LinkedList<ItemState<Player>>();
		list.add(new ItemWrappingState<>(PlayerWrapper::new));
		list.add(new PlayerSnapshotState());
		list.add(new PlayerScoreboardState(game.getScoreboard()));
		return list;
	}

	/**
	 * Drops the player from the game, then, one tick later, re-runs the completion
	 * check of the lobby or preparation it left: the players still there may all be
	 * ready already. Skipped if that phase has ended or restarted since, or if the
	 * game has no player left.
	 */
	@EventHandler
	public void onPlayerLeft(PlayerQuitEvent evt) {
		var game = Ioc.resolve(Vi6Game.class);
		game.removePlayer(evt.getPlayer());
		var machine = game.getPhaseMachine();
		var lobby = machine.getPhase(Vi6PhaseLobby.class);
		var preparation = machine.getPhase(Vi6PhasePreparation.class);
		if (lobby == null && preparation == null) {
			return;
		}
		Bukkit.getScheduler().runTask(Ioc.resolve(JavaPlugin.class), () -> {
			if (game.getPlayerList().isEmpty()) {
				return;
			}
			// Phases are new instances each time they start
			if (lobby != null && machine.getPhase(Vi6PhaseLobby.class) == lobby) {
				lobby.checkForCompletion();
			} else if (preparation != null && machine.getPhase(Vi6PhasePreparation.class) == preparation) {
				preparation.checkForCompletion();
			}
		});
	}

	@EventHandler
	public void onPlayerToggleSneak(PlayerToggleSneakEvent evt) {
		evt.getPlayer().setSprinting(false);
	}
}
