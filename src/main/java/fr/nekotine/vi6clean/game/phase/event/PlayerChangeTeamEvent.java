package fr.nekotine.vi6clean.game.phase.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import fr.nekotine.vi6clean.game.Vi6Team;

public class PlayerChangeTeamEvent extends Event implements Cancellable {

	private static final HandlerList HANDLER_LIST = new HandlerList();

	private final Player player;

	private final Vi6Team newTeam;

	private final Vi6Team oldTeam;

	private boolean cancelled;

	public PlayerChangeTeamEvent(final Player player, final Vi6Team oldTeam, final Vi6Team newTeam) {
		this.player = player;
		this.oldTeam = oldTeam;
		this.newTeam = newTeam;
	}

	public Player player() {
		return player;
	}

	public Vi6Team oldTeam() {
		return oldTeam;
	}

	public Vi6Team newTeam() {
		return newTeam;
	}

	@Override
	public boolean isCancelled() {
		return cancelled;
	}

	@Override
	public void setCancelled(final boolean cancel) {
		cancelled = cancel;
	}

	@NotNull
	@Override
	public HandlerList getHandlers() {
		return HANDLER_LIST;
	}

	@NotNull
	public static HandlerList getHandlerList() {
		return HANDLER_LIST;
	}

}
