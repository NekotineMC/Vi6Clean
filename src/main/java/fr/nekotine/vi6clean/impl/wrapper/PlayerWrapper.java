package fr.nekotine.vi6clean.impl.wrapper;

import org.bukkit.entity.Player;

import fr.nekotine.core.snapshot.Snapshot;
import fr.nekotine.core.wrapper.WrapperBase;

public class PlayerWrapper extends WrapperBase<Player> {

	private Snapshot<Player> preGameSnapshot;
	
	public PlayerWrapper(Player wrapped) {
		super(wrapped);
	}

	public Snapshot<Player> getPreGameSnapshot() {
		return preGameSnapshot;
	}

	public void setPreGameSnapshot(Snapshot<Player> preGameSnapshot) {
		this.preGameSnapshot = preGameSnapshot;
	}

}
