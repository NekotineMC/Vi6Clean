package fr.nekotine.vi6clean.bot;

import fr.nekotine.core.bot.Bot;

/**
 * Decides what a bot does, once per tick, inside its {@code BotTickEvent}. A
 * brain plays by human rules: it reads game state through Vi6's public API and
 * acts only through the bot's controls and the commands a player can send.
 */
@FunctionalInterface
public interface BotBrain {

	void tick(Bot bot);

	/**
	 * Called, inside the bot's {@code BotTickEvent}, once when this brain stops
	 * being ticked (e.g. {@link PhaseBrain} leaves its phase): a brain that drives
	 * movement over several ticks releases it here. Does nothing by default.
	 */
	default void halt(Bot bot) {
	}
}
