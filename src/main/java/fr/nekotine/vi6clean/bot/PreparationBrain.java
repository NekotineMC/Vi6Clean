package fr.nekotine.vi6clean.bot;

import java.util.List;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

import org.jetbrains.annotations.Nullable;

import fr.nekotine.core.bot.Bot;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.Vi6Commands;
import fr.nekotine.vi6clean.game.Vi6Game;
import fr.nekotine.vi6clean.wrapper.PreparationPhasePlayerWrapper;

/**
 * In each preparation, a thief picks a random entrance with
 * {@code /game entrance}, then every bot readies up with
 * {@code /game prepready}, once.
 */
public class PreparationBrain implements BotBrain {

	private final RandomGenerator random;

	private final Supplier<List<String>> entrances;

	// Commands land after the tick and /game prepready toggles: act once per
	// preparation, identified by the bot's preparation wrapper
	private @Nullable PreparationPhasePlayerWrapper actedIn;

	private @Nullable String pickedEntrance;

	/**
	 * @param random
	 *            the source of the entrance pick, shared with the other bots so a
	 *            seed gives the same picks
	 */
	public PreparationBrain(RandomGenerator random) {
		this(random, Vi6Commands::entranceNames);
	}

	/**
	 * @param random
	 *            the source of the entrance pick
	 * @param entrances
	 *            the entrance names to pick from, read once per preparation
	 */
	public PreparationBrain(RandomGenerator random, Supplier<List<String>> entrances) {
		this.random = random;
		this.entrances = entrances;
	}

	@Override
	public void tick(Bot bot) {
		var wrapper = Ioc.resolve(WrappingModule.class)
				.getWrapperOptional(bot.player(), PreparationPhasePlayerWrapper.class).orElse(null);
		if (wrapper == null) {
			actedIn = null;
			return;
		}
		if (wrapper == actedIn) {
			return;
		}
		actedIn = wrapper;
		pickedEntrance = null;
		if (Ioc.resolve(Vi6Game.class).getThiefs().contains(bot.player())) {
			// Without an entrance, preparation falls back to a random one
			var names = entrances.get();
			if (!names.isEmpty()) {
				pickedEntrance = names.get(random.nextInt(names.size()));
				bot.sendCommand("game entrance " + pickedEntrance);
			}
		}
		bot.sendCommand("game prepready");
	}

	/**
	 * @return the entrance this brain asked for in its last preparation, or
	 *         {@code null} if it picked none; its command may not have landed
	 */
	public @Nullable String pickedEntrance() {
		return pickedEntrance;
	}
}
