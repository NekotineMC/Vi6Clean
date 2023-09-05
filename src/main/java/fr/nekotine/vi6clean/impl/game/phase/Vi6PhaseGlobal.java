package fr.nekotine.vi6clean.impl.game.phase;

import java.util.LinkedList;
import java.util.List;

import org.bukkit.entity.Player;

import fr.nekotine.core.game.phase.CollectionPhase;
import fr.nekotine.core.game.phase.IPhaseMachine;
import fr.nekotine.core.state.ItemState;
import fr.nekotine.core.state.ItemWrappingState;
import fr.nekotine.core.state.PlayerSnapshotState;
import fr.nekotine.core.util.EntityUtil;
import fr.nekotine.core.util.collection.ObservableCollection;
import fr.nekotine.vi6clean.Vi6Main;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;

public class Vi6PhaseGlobal extends CollectionPhase<Void, Player>{

	public Vi6PhaseGlobal(IPhaseMachine machine) {
		super(machine);
	}

	@Override
	public Class<Void> getParentType() {
		return Void.class;
	}

	@Override
	public ObservableCollection<Player> getItemCollection() {
		return Vi6Main.IOC.resolve(Vi6Game.class).getPlayerList();
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
	}

	@Override
	public void itemTearDown(Player item) {
	}
	
	@Override
	protected List<ItemState<Player>> makeAppliedItemStates() {
		var list = new LinkedList<ItemState<Player>>();
		list.add(new ItemWrappingState<>(PlayerWrapper::new));
		list.add(new PlayerSnapshotState());
		return list;
	}

}
