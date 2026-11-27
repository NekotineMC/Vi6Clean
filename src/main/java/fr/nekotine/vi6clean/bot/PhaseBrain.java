package fr.nekotine.vi6clean.bot;

import java.util.Objects;
import java.util.random.RandomGenerator;

import org.jetbrains.annotations.Nullable;

import fr.nekotine.core.bot.Bot;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.vi6clean.game.Vi6Game;
import fr.nekotine.vi6clean.game.phase.Vi6PhaseInMap;
import fr.nekotine.vi6clean.game.phase.Vi6PhasePreparation;

/**
 * Hands each tick to the brain of the running phase: the lobby brain always (it
 * idles outside the lobby), then the preparation brain while preparation runs,
 * else the in-map brain while the map runs (infiltration included). Swap one
 * phase's brain by building a new {@code PhaseBrain}.
 *
 * <p>
 * When the preparation or in-map slot stops being ticked (its phase ended, or
 * another slot runs), its brain is {@link BotBrain#halt halted} once, in the
 * first tick without it. It remembers that slot per bot: one instance per bot,
 * never shared.
 */
public final class PhaseBrain implements BotBrain {

	/** A brain that does nothing: the bot stays where it is. */
	public static final BotBrain IDLE = _ -> {
	};

	private final BotBrain lobby;

	private final BotBrain preparation;

	private final BotBrain inMap;

	/** The slot ticked last tick, or {@code null} for none. */
	private @Nullable BotBrain lastSlot;

	public PhaseBrain(BotBrain lobby, BotBrain preparation, BotBrain inMap) {
		this.lobby = Objects.requireNonNull(lobby, "lobby");
		this.preparation = Objects.requireNonNull(preparation, "preparation");
		this.inMap = Objects.requireNonNull(inMap, "inMap");
	}

	/**
	 * @return the standard brains: lobby team pick and ready, preparation entrance
	 *         and ready, idle in the map
	 */
	public static PhaseBrain standard(BotTeamIntent intent, RandomGenerator random) {
		return new PhaseBrain(new LobbyBrain(intent), new PreparationBrain(random), IDLE);
	}

	public BotBrain lobby() {
		return lobby;
	}

	public BotBrain preparation() {
		return preparation;
	}

	public BotBrain inMap() {
		return inMap;
	}

	@Override
	public void tick(Bot bot) {
		lobby.tick(bot);
		var machine = Ioc.resolve(Vi6Game.class).getPhaseMachine();
		BotBrain slot = null;
		if (machine.getPhase(Vi6PhasePreparation.class) != null) {
			slot = preparation;
		} else if (machine.getPhase(Vi6PhaseInMap.class) != null) {
			slot = inMap;
		}
		if (lastSlot != null && lastSlot != slot) {
			var left = lastSlot;
			lastSlot = null;
			left.halt(bot);
		}
		lastSlot = slot;
		if (slot != null) {
			slot.tick(bot);
		}
	}

	/** Halts the slot ticked last, if any. */
	@Override
	public void halt(Bot bot) {
		var left = lastSlot;
		lastSlot = null;
		if (left != null) {
			left.halt(bot);
		}
	}

	@Override
	public String toString() {
		return "PhaseBrain[lobby=" + lobby + ", preparation=" + preparation + ", inMap=" + inMap + "]";
	}
}
