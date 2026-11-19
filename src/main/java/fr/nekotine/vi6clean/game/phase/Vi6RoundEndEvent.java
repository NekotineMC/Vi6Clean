package fr.nekotine.vi6clean.game.phase;

import java.util.Map;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired once per round, when infiltration ends with a result and just before
 * the game goes back to the lobby. Not fired for a round cut short (reset,
 * lobby forced mid-round).
 */
public class Vi6RoundEndEvent extends Event {

	/** Why the round ended. */
	public enum Reason {
		/** The infiltration lost timer ran out. */
		LOST_TIMER("lost-timer"),
		/** Every thief left the map. */
		ALL_THIEVES_LEFT("all-thieves-left");

		private final String code;

		Reason(String code) {
			this.code = code;
		}

		public String code() {
			return code;
		}
	}

	private final Reason reason;

	private final Map<String, Integer> artefactScores;

	/**
	 * @param artefactScores
	 *            each artefact's thief score, by artefact name: 3 safe, 2 stolen, 1
	 *            escaped, 0 lost
	 */
	public Vi6RoundEndEvent(Reason reason, Map<String, Integer> artefactScores) {
		this.reason = reason;
		this.artefactScores = Map.copyOf(artefactScores);
	}

	public Reason getReason() {
		return reason;
	}

	/**
	 * @return each artefact's thief score by name: 3 safe, 2 stolen, 1 escaped, 0
	 *         lost
	 */
	public Map<String, Integer> getArtefactScores() {
		return artefactScores;
	}

	private static final HandlerList handlers = new HandlerList();

	@Override
	public @NotNull HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
}
