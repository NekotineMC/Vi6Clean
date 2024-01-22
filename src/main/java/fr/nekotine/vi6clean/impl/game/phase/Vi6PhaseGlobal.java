package fr.nekotine.vi6clean.impl.game.phase;

import java.util.LinkedList;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import fr.nekotine.core.game.phase.CollectionPhase;
import fr.nekotine.core.game.phase.IPhaseMachine;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.state.ItemState;
import fr.nekotine.core.state.ItemWrappingState;
import fr.nekotine.core.state.PlayerScoreboardState;
import fr.nekotine.core.state.PlayerSnapshotState;
import fr.nekotine.core.util.EntityUtil;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.util.collection.ObservableCollection;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;

public class Vi6PhaseGlobal extends CollectionPhase<Void, Player> implements Listener{

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
		EventUtil.register(this);
	}

	@Override
	protected void globalTearDown() {
		EventUtil.unregister(this);
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
	protected List<ItemState<Player>> makeAppliedItemStates() {
		var game = Ioc.resolve(Vi6Game.class);
		var list = new LinkedList<ItemState<Player>>();
		list.add(new ItemWrappingState<>(PlayerWrapper::new));
		list.add(new PlayerSnapshotState());
		list.add(new PlayerScoreboardState(game.getScoreboard()));
		return list;
	}
	
	@EventHandler
	public void onPlayerLeft(PlayerQuitEvent evt) {
		Ioc.resolve(Vi6Game.class).removePlayer(evt.getPlayer());
	}
	
	@EventHandler
	public void onPlayerToggleSneak(PlayerToggleSneakEvent evt) {
		evt.getPlayer().setSprinting(false);
	}

}
