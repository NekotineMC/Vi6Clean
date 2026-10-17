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
	 * What one removal request actually did.
	 * <p>
	 * {@code removed} is read back off the game after the teardown rather than
	 * counted on the way in: {@code ObservableCollection.remove} swallows whatever
	 * its own suppression callbacks throw, so a teardown that half failed still
	 * returns normally and only a read of {@code getPlayerList()} /
	 * {@code getGuards()} / {@code getThiefs()} can tell that apart from a clean
	 * one. {@code guards} and {@code thieves} are the other way round, read before
	 * the teardown, because nothing about a departed bot's team is knowable
	 * afterwards.
	 *
	 * @param requested
	 *            how many bots the operator asked to take out, the whole roster
	 *            when the command carried no count
	 * @param removed
	 *            how many of them left the bot roster and Citizens, and whose
	 *            departure the game roster agrees with -- a bot whose entity was
	 *            already gone cannot be handed to {@code removePlayer}, and that
	 *            one is logged rather than counted here
	 * @param guards
	 *            how many of the removed bots were on the guard team
	 * @param thieves
	 *            how many of the removed bots were on the thief team
	 * @param failures
	 *            one reason per bot that did not leave
	 * @param rosterSize
	 *            the roster after the request, which is the figure the operator
	 *            needs in order to know what is left
	 */
	record RemoveReport(int requested, int removed, int guards, int thieves, List<String> failures, int rosterSize) {

		/**
		 * Same bound and same reason as {@link AddReport#MAX_REPORTED_FAILURES}: a
		 * request of 64 uncooperative NPCs must not become 64 stack traces in the
		 * sender's chat.
		 */
		private static final int MAX_REPORTED_FAILURES = 5;

		/**
		 * @return the one line an operator can read. The team split is there because a
		 *         removal that was narrowed to a team has to prove it took that team,
		 *         and the remaining roster is there because the removal is only half
		 *         the question an operator asks it.
		 */
		public String describe() {
			var head = removed + "/" + requested + " bot(s) retire(s) du roster, dont " + guards + " garde(s) et "
					+ thieves + " voleur(s), roster restant " + rosterSize + " bot(s)";
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
	 * One bot as the operator sees it.
	 * <p>
	 * Deliberately not {@link Bot}: that record is the identity of a bot and stays
	 * the bare {@code (npcId, name)} pair, while everything here is a reading taken
	 * live and is allowed to answer "I could not read this".
	 *
	 * @param team
	 *            the team the bot really is on, or an explicit none/unreadable
	 *            rather than a null the caller has to interpret
	 * @param liveness
	 *            whether Citizens currently has the bot spawned, which is a
	 *            different question from whether it is still in the roster: a
	 *            despawn is not a removal
	 */
	record BotStatus(String name, String team, String liveness) {

		public String describe() {
			return name + " | equipe " + team + " | " + liveness;
		}

	}

	/**
	 * The whole roster, as one report.
	 *
	 * @param botCount
	 *            how many bots the roster holds, unreadable ones included
	 * @param humanCount
	 *            how many humans are connected, read from the server: the game
	 *            roster holds the bots too, so it cannot be the source of this
	 *            figure
	 */
	record RosterReport(int botCount, int humanCount, List<BotStatus> bots) {

		public String header() {
			return botCount + " bot(s) au roster, " + humanCount + " humain(s) connecte(s)";
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
	 * Removes the {@code requested} newest bots, optionally narrowed to one team.
	 * Every bot leaves through the same {@code game.removePlayer} a human leaves
	 * through, and the Citizens store is flushed once so a restart does not bring
	 * any of them back.
	 * <p>
	 * "Newest" is the roster insertion order walked backwards: it is what makes
	 * {@code add 8} then {@code remove 3} take back the last three, with no
	 * timestamp to invent. A request wider than the roster removes what there is
	 * and reports that figure, never the requested one.
	 * <p>
	 * <b>Main thread only.</b>, like {@link #addBots(int, String)}.
	 *
	 * @return what the request did, never null
	 */
	RemoveReport removeBots(int requested, @Nullable String teamName);

	/**
	 * Reads the whole roster for the operator: one {@link BotStatus} per bot, plus
	 * the bot and human counts. A bot that cannot be read is reported as unreadable
	 * rather than dropped, because an operator who cannot account for a bot cannot
	 * correct the roster either.
	 * <p>
	 * <b>Main thread only.</b>, like {@link #addBots(int, String)}.
	 *
	 * @return what the roster holds right now, never null
	 */
	RosterReport listBots();

	/**
	 * @return a copy of the roster's identities, keyed on the Citizens NPC id. It
	 *         is the surface for a caller that needs the ids and names themselves;
	 *         the operator facing report is {@link #listBots()}, which reads the
	 *         roster directly and adds the readings this record deliberately does
	 *         not carry. It is a snapshot, not a live view. Main thread only, like
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
