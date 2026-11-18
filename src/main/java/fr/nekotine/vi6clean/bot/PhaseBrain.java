package fr.nekotine.vi6clean.bot;

import java.util.random.RandomGenerator;

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
 */
public record PhaseBrain(BotBrain lobby, BotBrain preparation, BotBrain inMap) implements BotBrain {

	/** A brain that does nothing: the bot stays where it is. */
	public static final BotBrain IDLE = _ -> {
	};

	/**
	 * @return the standard brains: lobby team pick and ready, preparation entrance
	 *         and ready, idle in the map
	 */
	public static PhaseBrain standard(BotTeamIntent intent, RandomGenerator random) {
		return new PhaseBrain(new LobbyBrain(intent), new PreparationBrain(random), IDLE);
	}

	@Override
	public void tick(Bot bot) {
		lobby.tick(bot);
		var machine = Ioc.resolve(Vi6Game.class).getPhaseMachine();
		if (machine.getPhase(Vi6PhasePreparation.class) != null) {
			preparation.tick(bot);
		} else if (machine.getPhase(Vi6PhaseInMap.class) != null) {
			inMap.tick(bot);
		}
	}
}
