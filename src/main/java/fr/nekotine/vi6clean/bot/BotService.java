package fr.nekotine.vi6clean.bot;

import java.util.Collection;
import java.util.List;

import org.jetbrains.annotations.Nullable;

/**
 * What the rest of the plugin is allowed to ask of the bots, with no Citizens
 * type anywhere in sight.
 * <p>
 * This interface is one half of the classloader barrier. A command executor
 * lives in a class that is linked at {@code onLoad}, and the bytecode verifier
 * resolves the class of every {@code invokevirtual} it sees, so naming
 * {@link BotRegistry} from there would drag Citizens in on a server that does
 * not have it. Going through this interface keeps the only Citizens-bearing
 * class, {@code BotRegistry}, one reflective hop away, behind the
 * {@link CitizensAvailability} gate.
 */
public interface BotService {

	/**
	 * Upper bound of one add request. Also the range of the {@code count} command
	 * argument, so a bad count is refused by the parser and never reaches a spawn
	 * loop.
	 */
	int MIN_BOTS_PER_COMMAND = 1;

	int MAX_BOTS_PER_COMMAND = 64;

	/**
	 * What one add request actually did.
	 *
	 * @param requested
	 *            how many bots the operator asked for
	 * @param admitted
	 *            how many of them reached the roster
	 * @param guards
	 *            how many of the admitted bots the game put on the guard team
	 * @param thieves
	 *            how many of the admitted bots the game put on the thief team
	 * @param failures
	 *            one reason per bot that did not
	 * @param rosterSize
	 *            the cumulative roster after the request, which is the figure the
	 *            repeat-add row of the story's matrix asks the operator to be told
	 */
	record AddReport(int requested, int admitted, int guards, int thieves, List<String> failures, int rosterSize) {

		/**
		 * How many reasons {@link #describe()} is willing to paste into one line, so a
		 * request of 64 failures does not become 64 stack traces in the sender's chat.
		 */
		private static final int MAX_REPORTED_FAILURES = 5;

		public boolean isFullyAdmitted() {
			return admitted == requested;
		}

		/**
		 * @return the one line an operator can read. It always carries the team split,
		 *         which is the whole point of the balancing rule, it always carries the
		 *         cumulative roster, and it always carries the failures, even when
		 *         nothing was requested.
		 */
		public String describe() {
			var head = admitted + "/" + requested + " bot(s) ajouté(s) au roster, dont " + guards + " garde(s) et "
					+ thieves + " voleur(s), roster total " + rosterSize + " bot(s)";
			if (failures.isEmpty()) {
				return head + ".";
			}
			var shown = failures.stream().limit(MAX_REPORTED_FAILURES).toList();
			var tail = failures.size() > shown.size()
					? " | ... et " + (failures.size() - shown.size()) + " autre(s) echec(s)"
					: "";
			return head + ". Echecs : " + String.join(" | ", shown) + tail;
		}

	}

	/**
	 * Spawns and admits {@code requested} bots. A pinned {@code teamName} puts
	 * every one of them in that team, otherwise the balancing rule of the game
	 * decides. A bot that fails to spawn, to be admitted or to be fully set up is
	 * rolled back and reported, and the rest of the request is still attempted.
	 * <p>
	 * <b>Main thread only.</b> This touches the Citizens registry, teleports
	 * entities, mutates the {@code ObservableCollection}s the game phase machine
	 * observes, and reads the scoreboard. Call it from the server thread, which is
	 * what the command executor does.
	 *
	 * @return what the request did, never null
	 */
	AddReport addBots(int requested, @Nullable String teamName);

	/**
	 * @return the live roster, keyed on the Citizens NPC id. Main thread only, like
	 *         {@link #addBots(int, String)}
	 */
	Collection<Bot> getBots();

	/**
	 * @return how many bots the roster holds, main thread only
	 */
	int size();

	/**
	 * Destroys every bot, removing each one from the game roster as well as from
	 * Citizens. Bots are transient: none is re-adopted on boot, and the destroyed
	 * NPCs are flushed to disk, so a restart can never leak a ghost into the
	 * roster.
	 */
	void destroyAll();

}
