package fr.nekotine.vi6clean.bot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.jetbrains.annotations.Nullable;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.logging.NekotineLogger;
import fr.nekotine.vi6clean.game.Vi6Game;
import fr.nekotine.vi6clean.game.phase.Vi6PhaseLobby;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.trait.GameModeTrait;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

/**
 * The bot roster. This class holds every reference to Citizens that new code
 * owns, and it is the only new class that calls into Citizens. The one
 * pre-existing exception is {@code debug/NpcProbe}, which the frozen intent
 * forbids touching and Epic 2 deletes.
 * <p>
 * Three invariants are load-bearing:
 * <ul>
 * <li>{@code GameModeTrait} is never active on a bot. The game mode belongs to
 * the game, and a trait that re-applies one every tick would override what the
 * game decided.</li>
 * <li>A bot is admitted, gamemoded and teleported through the unmodified code a
 * human goes through, so this class decides nothing the game does not already
 * decide for a player.</li>
 * <li>A bot never outlives the session. {@code SHOULD_SAVE} keeps it off disk
 * while it lives, and the store is flushed after the shutdown teardown so the
 * deletion is not swallowed by a snapshot Citizens took before
 * {@code onDisable} ran.</li>
 * </ul>
 * <p>
 * It is only ever instantiated once {@link CitizensAvailability#check()}
 * reports {@link CitizensAvailability#READY}, since a class holding Citizens
 * types cannot link without them.
 */
public class BotRegistry implements BotService {

	public static final String TEAM_GUARD = "guard";

	public static final String TEAM_THIEF = "thief";

	/**
	 * The "game mode sticks" acceptance criterion is about what happens well after
	 * admission, not at it: a {@code GameModeTrait} left attached only shows up
	 * once Citizens had a chance to re-apply it, and a Citizens respawn only shows
	 * up once it restored its own stored location. One sample taken 20 ticks in
	 * reads clean in both of those cases, so the invariant is re-read more than
	 * once.
	 */
	private static final int[] INVARIANT_RECHECK_DELAYS_TICKS = {20, 200};

	private static final double SPAWN_TOLERANCE_BLOCKS = 0.5;

	/**
	 * Scoreboard team membership goes through the entry name, which the server caps
	 * at 16 characters.
	 */
	private static final int NAME_MAX_LENGTH = 16;

	private static final String NAME_PREFIX = "Bot";

	private static final int NAME_INDEX_LIMIT = 1000;

	private static final String LOBBY_REQUIRED = "les bots ne peuvent être ajoutés que pendant la phase lobby, "
			+ "qui n'est pas en cours";

	private static final ComponentLogger logger = NekotineLogger.make(BotRegistry.class);

	private final Map<UUID, NPC> npcs = new LinkedHashMap<>();

	private int nameCounter = 0;

	/**
	 * One observation of one bot, read live off the Citizens object. Every field is
	 * nullable on purpose: a reading that could not be taken has to stay visibly
	 * different from a reading that came back clean, otherwise a check that failed
	 * to run would be reported as a check that passed.
	 *
	 * @param traitActive
	 *            null when the trait could not be read at all, which is not the
	 *            same answer as {@code false}
	 * @param saveSuppressed
	 *            whether the NPC still carries {@code SHOULD_SAVE=false}, read back
	 *            off the live object rather than assumed from the code that set it.
	 *            null when Citizens never held the key at all
	 */
	private record BotReading(String name, @Nullable UUID npcId, @Nullable UUID entityId, @Nullable GameMode gameMode,
			@Nullable Location location, @Nullable Boolean traitActive, @Nullable Boolean saveSuppressed) {

		static BotReading unreadable(String name) {
			return new BotReading(name, null, null, null, null, null, null);
		}

		boolean isReadable() {
			return gameMode != null;
		}

	}

	/**
	 * Main thread only, see {@link BotService#addBots(int, String)}.
	 */
	@Override
	public AddReport addBots(int requested, @Nullable String teamName) {
		if (requested < MIN_BOTS_PER_COMMAND || requested > MAX_BOTS_PER_COMMAND) {
			return refused(requested, "le nombre de bots doit être compris entre " + MIN_BOTS_PER_COMMAND + " et "
					+ MAX_BOTS_PER_COMMAND);
		}
		final NPCRegistry npcRegistry;
		final Vi6Game game;
		try {
			npcRegistry = CitizensAPI.getNPCRegistry();
		} catch (Exception e) {
			logger.error("Le registre de NPCs de Citizens n'a pas pu être obtenu", e);
			return refused(requested, "le registre de NPCs de Citizens est indisponible");
		} catch (LinkageError err) {
			logger.error("Le registre de NPCs n'a pas pu être atteint, une classe Citizens est introuvable", err);
			return refused(requested, "une classe Citizens est introuvable");
		}
		try {
			game = Ioc.resolve(Vi6Game.class);
		} catch (Exception e) {
			logger.error("La game n'a pas pu être résolue", e);
			return refused(requested, "la game n'est pas disponible");
		}
		if (npcRegistry == null) {
			return refused(requested, "le registre de NPCs de Citizens est indisponible");
		}
		var admitted = new ArrayList<NPC>();
		var failures = new ArrayList<String>();
		for (var i = 0; i < requested; i++) {
			// The lobby gate is re-read for every bot rather than once at batch entry: the
			// phase can end midway, and admitting the rest of the batch after that is
			// exactly what the gate exists to prevent.
			if (!isLobbyRunning(game)) {
				failures.add(LOBBY_REQUIRED + " (" + (i + 1) + "/" + requested + " bot(s) non ajoute(s))");
				break;
			}
			String name = null;
			NPC npc = null;
			try {
				// The name lookup sits inside the per bot try on purpose: a registry that
				// cannot be walked must skip one bot, not abandon the batch with no report
				// at all.
				name = nextFreeName(npcRegistry);
				// admitOne rolls its own bot back when it fails; holding the handle out here
				// covers the window between "the game took it" and "the roster owns it", so a
				// throw in that window cannot orphan a live NPC the roster never learns about.
				npc = admitOne(npcRegistry, name, teamName, game);
				npcs.put(npc.getUniqueId(), npc);
				admitted.add(npc);
			} catch (Exception e) {
				rollback(game, npc, null);
				failures.add(describeFailure(name, e));
				logger.error("Le bot " + name + " n'a pas pu être ajouté au roster", e);
			} catch (LinkageError err) {
				rollback(game, npc, null);
				failures.add(describeFailure(name, err));
				logger.error("Le bot " + name + " n'a pas pu être ajouté au roster, une classe Citizens est "
						+ "introuvable", err);
			}
		}
		logRosterState(game, admitted, failures);
		scheduleInvariantRechecks(game, List.copyOf(admitted));
		return new AddReport(requested, admitted.size(), countTeam(game, admitted, true),
				countTeam(game, admitted, false), List.copyOf(failures), npcs.size());
	}

	/**
	 * A request that created nothing still has to report the cumulative roster, so
	 * the operator sees the total whether or not the batch worked.
	 */
	private AddReport refused(int requested, String reason) {
		return new AddReport(requested, 0, 0, 0, List.of(reason), npcs.size());
	}

	@Override
	public Collection<Bot> getBots() {
		return npcs.values().stream().map(npc -> new Bot(npc.getUniqueId(), npc.getName())).toList();
	}

	/**
	 * Main thread only, like {@link #addBots(int, String)}: {@code npcs} is a bare
	 * {@link LinkedHashMap} and the write happens on the server thread.
	 */
	@Override
	public int size() {
		return npcs.size();
	}

	/**
	 * Destroys every bot, in the teardown order the story fixes: derive the
	 * {@code Player}, drop it from the game roster, destroy the NPC, forget it,
	 * then flush the Citizens store once for the whole batch.
	 */
	@Override
	public void destroyAll() {
		if (npcs.isEmpty()) {
			return;
		}
		var count = npcs.size();
		var owning = owningRegistry();
		for (var npc : List.copyOf(npcs.values())) {
			// One uncooperative NPC must not cost the whole teardown: the remaining bots,
			// the roster clear and the store flush all still have to happen.
			try {
				retire(npc, null);
			} catch (Exception e) {
				logger.warn("La destruction d'un bot a echoue", e);
			} catch (LinkageError err) {
				logger.warn("La destruction d'un bot a echoue, une classe Citizens est introuvable", err);
			}
		}
		npcs.clear();
		flushStore(owning);
		logDestroyedRoster(count);
	}

	/**
	 * Creates one bot, spawns it at the world spawn and admits it through the
	 * unmodified game path. Anything that goes wrong rolls the whole bot back, so
	 * the roster never keeps a player the game failed to set up.
	 *
	 * @return the admitted NPC, only once it really landed
	 */
	private NPC admitOne(NPCRegistry npcRegistry, String name, @Nullable String teamName, Vi6Game game)
			throws Exception {
		NPC npc = null;
		Player player = null;
		try {
			npc = npcRegistry.createNPC(EntityType.PLAYER, name);
			if (npc == null) {
				throw new IllegalStateException("Citizens n'a créé aucun NPC pour le nom " + name);
			}
			// Citizens flushes saves.yml on its own shutdown, and it does so
			// before our onDisable runs. Refusing to persist the NPC at all is what stops
			// the next boot from resurrecting it, and every reading reads the flag back
			// off the NPC so the guarantee is observed rather than assumed.
			npc.data().set(NPC.Metadata.SHOULD_SAVE, false);
			// Defensive: 2.0.44 does not register GameModeTrait as a default trait, but
			// a future release that did would silently put every bot back in SURVIVAL.
			if (npc.hasTrait(GameModeTrait.class)) {
				npc.removeTrait(GameModeTrait.class);
			}
			var world = game.getWorld();
			var spawnLocation = world == null ? null : world.getSpawnLocation();
			if (spawnLocation == null) {
				throw new IllegalStateException("aucun monde n'est chargé, le bot " + name + " n'a pas où apparaître");
			}
			if (!npc.spawn(spawnLocation.clone())) {
				throw new IllegalStateException("Citizens n'a pas fait apparaître le NPC " + name);
			}
			var entity = npc.getEntity();
			if (!(entity instanceof Player spawned)) {
				throw new IllegalStateException("le NPC " + name + " n'a produit aucun Player");
			}
			player = spawned;
			admit(game, player, teamName);
			// ObservableCollection.add swallows and logs whatever a callback throws, so a
			// bot can sit in the roster half set up while addPlayer returns normally.
			// This is the read that notices.
			if (!landedInRoster(game, player)) {
				throw new IllegalStateException(
						"le bot " + name + " est dans le roster mais n'a pas été setup, une callback d'ajout a échoué");
			}
			return npc;
		} catch (Exception e) {
			rollback(game, npc, player);
			throw e;
		} catch (LinkageError err) {
			rollback(game, npc, player);
			throw err;
		}
	}

	private static void admit(Vi6Game game, Player player, @Nullable String teamName) {
		if (TEAM_GUARD.equalsIgnoreCase(teamName)) {
			game.addPlayerInGuards(player);
		} else if (TEAM_THIEF.equalsIgnoreCase(teamName)) {
			game.addPlayerInThiefs(player);
		} else {
			game.addPlayer(player);
		}
	}

	/**
	 * Whether the admission really completed.
	 * <p>
	 * Being in {@code players} is not enough: the phase machine observes that
	 * collection through an addition callback that logs and swallows its own
	 * failures, so a player can be listed and still be half set up.
	 * <p>
	 * The game mode is the decisive read, because it is written after everything
	 * else in {@code Vi6Game.setupGuard} and {@code setupThief}: the scoreboard
	 * entry first, then the wrapper, and only then
	 * {@code player.setGameMode(GameMode.ADVENTURE)}. A throw anywhere in between —
	 * and the likeliest is the null {@code PlayerWrapper} a failed item setup
	 * leaves behind — is swallowed by {@code ObservableCollection.add} and lands
	 * here as "not ADVENTURE" rather than as a silent success.
	 * <p>
	 * The scoreboard entry is still checked when there is a scoreboard: it is what
	 * the 16 character name rule actually guards.
	 */
	private boolean landedInRoster(Vi6Game game, Player player) {
		if (!game.getPlayerList().contains(player)) {
			return false;
		}
		var guard = game.getGuards().contains(player);
		var thief = game.getThiefs().contains(player);
		if (guard == thief) {
			return false;
		}
		if (player.getGameMode() != GameMode.ADVENTURE) {
			return false;
		}
		var scoreboard = game.getScoreboard();
		if (scoreboard == null) {
			return true;
		}
		var entry = scoreboard.getEntryTeam(player.getName());
		if (entry == null) {
			return false;
		}
		return TEAM_GUARD.equalsIgnoreCase(entry.getName()) == guard;
	}

	private void rollback(Vi6Game game, @Nullable NPC npc, @Nullable Player player) {
		if (npc == null) {
			return;
		}
		retire(npc, player);
	}

	/**
	 * The single teardown path, shared by the shutdown and by the admission
	 * rollback. Nothing here is allowed to throw: this runs from {@code onDisable},
	 * where a throw would skip the rest of the shutdown.
	 */
	private void retire(NPC npc, @Nullable Player knownPlayer) {
		Player player = knownPlayer;
		var entity = (Entity) null;
		if (player == null) {
			entity = readEntity(npc);
			if (entity instanceof Player spawned) {
				player = spawned;
			}
		}
		if (player != null) {
			removeFromGame(safeName(npc), player);
		}
		destroy(npc, entity);
		npcs.remove(npc.getUniqueId());
	}

	/**
	 * {@code retire} runs from {@code onDisable}, where a throw skips
	 * {@code game.close()} and {@code super.onDisable()} entirely, so no Citizens
	 * call on this path may be unguarded — including the one that only labels a log
	 * line.
	 */
	private static String safeName(NPC npc) {
		try {
			return npc.getName();
		} catch (Exception e) {
			logger.warn("Le nom d'un bot n'a pas pu etre lu", e);
			return "un bot sans nom lisible";
		} catch (LinkageError err) {
			logger.warn("Le nom d'un bot n'a pas pu etre lu, une classe Citizens est introuvable", err);
			return "un bot sans nom lisible";
		}
	}

	private void removeFromGame(String name, Player player) {
		try {
			Ioc.resolve(Vi6Game.class).removePlayer(player);
		} catch (Exception e) {
			logger.warn("Le bot " + name + " n'a pas pu être retiré de la game", e);
		} catch (LinkageError err) {
			logger.warn("Le bot " + name + " n'a pas pu être retiré de la game, une classe Citizens est introuvable",
					err);
		}
	}

	private @Nullable NPCRegistry owningRegistry() {
		try {
			return npcs.values().stream().findFirst().map(NPC::getOwningRegistry).orElse(null);
		} catch (Exception e) {
			logger.warn("Le registre propriétaire des bots n'a pas pu être lu", e);
			return null;
		} catch (LinkageError err) {
			logger.warn("Le registre propriétaire des bots n'a pas pu être lu, une classe Citizens est introuvable",
					err);
			return null;
		}
	}

	/**
	 * Citizens took its shutdown snapshot of {@code saves.yml} before
	 * {@code onDisable} ran, so the in memory deletion performed by
	 * {@code npc.destroy()} would never reach disk on its own. Flushing once, after
	 * the loop, is what makes the teardown durable.
	 */
	private void flushStore(@Nullable NPCRegistry registry) {
		if (registry == null) {
			logger.warn("Le registre proprietaire des bots est inconnu, l'ecriture apres destruction est sautee");
			return;
		}
		try {
			registry.saveToStore();
			logger.info("Le registre de NPCs a ete ecrit sur le disque apres la destruction des bots");
		} catch (Exception e) {
			logger.warn("Le registre de NPCs n'a pas pu être écrit sur le disque après la destruction des bots", e);
		} catch (LinkageError err) {
			logger.warn("Le registre de NPCs n'a pas pu être écrit sur le disque, une classe Citizens est introuvable",
					err);
		}
	}

	/**
	 * The teardown runs in {@code onDisable}, where the roster cannot be inspected
	 * afterwards, so the evidence that the bots are gone from the game has to be in
	 * the log.
	 */
	private void logDestroyedRoster(int destroyed) {
		try {
			var game = Ioc.resolve(Vi6Game.class);
			var scoreboard = game.getScoreboard();
			logger.info(destroyed + " bot(s) ont été détruits | Etat de la game apres destruction : "
					+ game.getPlayerList().size() + " joueur(s), " + game.getGuards().size() + " garde(s), "
					+ game.getThiefs().size() + " voleur(s), equipe scoreboard " + TEAM_GUARD + "="
					+ teamEntries(scoreboard, TEAM_GUARD) + " et " + TEAM_THIEF + "="
					+ teamEntries(scoreboard, TEAM_THIEF));
		} catch (Exception e) {
			logger.warn("L'etat de la game apres destruction des bots n'a pas pu être lu", e);
		} catch (LinkageError err) {
			logger.warn("L'etat de la game apres destruction des bots n'a pas pu être lu, une classe Citizens est "
					+ "introuvable", err);
		}
	}

	private static String teamEntries(@Nullable Scoreboard scoreboard, String teamName) {
		if (scoreboard == null) {
			return "absent";
		}
		var team = scoreboard.getTeam(teamName);
		return team == null ? "absente" : String.valueOf(team.getEntries().size());
	}

	private static boolean isLobbyRunning(Vi6Game game) {
		return game.getPhaseMachine().getPhase(Vi6PhaseLobby.class) != null;
	}

	private int countTeam(Vi6Game game, List<NPC> admitted, boolean guards) {
		var count = 0;
		for (var npc : admitted) {
			var entity = readEntity(npc);
			if (!(entity instanceof Player player)) {
				continue;
			}
			var team = guards ? game.getGuards() : game.getThiefs();
			if (team.contains(player)) {
				count++;
			}
		}
		return count;
	}

	/**
	 * The diagnostics ship always on. {@code src/test} is empty by design, so this
	 * log is the only automated check a run of this plugin can produce, which is
	 * exactly why every figure here has to be one that can go red.
	 */
	private void logRosterState(Vi6Game game, List<NPC> admitted, List<String> failures) {
		logger.info("Bots ajoutes : " + admitted.size() + ", roster du bot package : " + npcs.size());
		logger.info("Noms des bots ajoutes : " + admitted.stream().map(NPC::getName).toList());
		logger.info("Etat de la game : " + game.getPlayerList().size() + " joueur(s), " + game.getGuards().size()
				+ " garde(s), " + game.getThiefs().size() + " voleur(s)");
		for (var npc : admitted) {
			logger.info(describe(game, read(npc), "a l'admission"));
		}
		if (!failures.isEmpty()) {
			logger.warn("Echecs d'ajout (" + failures.size() + ") : " + failures);
		}
	}

	/**
	 * Rereads every admitted bot once the delay has elapsed, on the server thread,
	 * more than once, so the invariant is checked where it actually bites.
	 */
	private void scheduleInvariantRechecks(Vi6Game game, List<NPC> admitted) {
		if (admitted.isEmpty()) {
			return;
		}
		for (var delay : INVARIANT_RECHECK_DELAYS_TICKS) {
			try {
				Bukkit.getScheduler().runTaskLater(Ioc.resolve(JavaPlugin.class), () -> {
					try {
						logInvariant(game, admitted, delay);
					} catch (Exception e) {
						logger.warn("L'etat des bots n'a pas pu être relu " + delay + " ticks après leur ajout", e);
					} catch (LinkageError err) {
						logger.warn("L'etat des bots n'a pas pu être relu, une classe Citizens est introuvable", err);
					}
				}, delay);
			} catch (Exception e) {
				logger.warn("La relecture de l'etat des bots n'a pas pu être programmée à " + delay + " ticks", e);
			} catch (LinkageError err) {
				logger.warn("La relecture de l'etat des bots n'a pas pu être programmée, une classe Citizens est "
						+ "introuvable", err);
			}
		}
	}

	private void logInvariant(Vi6Game game, List<NPC> admitted, int delay) {
		var readings = admitted.stream().map(BotRegistry::read).toList();
		for (var reading : readings) {
			logger.info(describe(game, reading, delay + " ticks apres l'admission"));
		}
		var unreadable = (int) readings.stream().filter(r -> !r.isReadable()).count();
		var traitUnreadable = (int) readings.stream().filter(r -> r.traitActive() == null).count();
		var adventure = (int) readings.stream().filter(r -> r.gameMode() == GameMode.ADVENTURE).count();
		var onSpawn = (int) readings.stream().filter(r -> isAtWorldSpawn(spawnOf(game), r.location())).count();
		var trailed = (int) readings.stream().filter(r -> Boolean.TRUE.equals(r.traitActive())).count();
		// An aggregate over the roster Map would be a tautology: keys of a Map are
		// distinct by construction. Comparing the two ids read live off the NPC is the
		// only check that can tell a correct key from the forbidden one.
		var idCollisions = (int) readings.stream().filter(r -> r.npcId() != null && r.npcId().equals(r.entityId()))
				.count();
		var saveSuppressed = (int) readings.stream().filter(r -> Boolean.TRUE.equals(r.saveSuppressed())).count();
		var expectedReadable = admitted.size() - unreadable;
		logger.info("Bilan a " + delay + " ticks : " + expectedReadable + "/" + admitted.size() + " bot(s) lisibles, "
				+ unreadable + " illisible(s), " + traitUnreadable + " GameModeTrait illisible(s), " + adventure
				+ " en ADVENTURE, " + onSpawn + " toujours au spawn du monde, " + trailed + " avec un GameModeTrait, "
				+ idCollisions + " ou un id NPC vaut l'id d'entite, " + saveSuppressed + " non persiste(s)");
		// A report nobody compares is a report that cannot fail, which is exactly what
		// round 1 shipped. The figures above are the evidence; this is the assertion,
		// because there is no test framework here to carry it.
		var readable = expectedReadable;
		if (adventure != readable) {
			logger.warn(
					"INVARIANT ROMPU a " + delay + " ticks : " + (readable - adventure) + " bot(s) lisibles ne sont "
							+ "plus en ADVENTURE, donc un GameModeTrait ou une autre cause les a repris");
		}
		if (onSpawn != readable) {
			logger.warn("INVARIANT ROMPU a " + delay + " ticks : " + (readable - onSpawn) + " bot(s) lisibles ne sont "
					+ "plus au spawn du monde, donc Citizens a deplace ou respawn un bot");
		}
		if (trailed != 0) {
			logger.warn("INVARIANT ROMPU a " + delay + " ticks : " + trailed + " bot(s) portent un GameModeTrait");
		}
		if (idCollisions != 0) {
			logger.warn("INVARIANT ROMPU a " + delay + " ticks : " + idCollisions + " bot(s) ont un id NPC egal a leur "
					+ "id d'entite, donc le roster est keye sur la mauvaise valeur");
		}
		if (saveSuppressed != readable) {
			logger.warn("INVARIANT ROMPU a " + delay + " ticks : " + (readable - saveSuppressed) + " bot(s) lisibles "
					+ "ne refusent pas la persistance, donc ils peuvent survivre au redemarrage");
		}
	}

	private static BotReading read(NPC npc) {
		var name = npc.getName();
		try {
			var npcId = npc.getUniqueId();
			var entity = npc.getEntity();
			GameMode mode = null;
			Location location = null;
			UUID entityId = null;
			if (entity instanceof Player player) {
				mode = player.getGameMode();
				location = player.getLocation();
				entityId = player.getUniqueId();
			}
			return new BotReading(name, npcId, entityId, mode, location, npc.hasTrait(GameModeTrait.class),
					readSaveSuppression(npc));
		} catch (Exception e) {
			logger.warn("L'etat du bot " + name + " n'a pas pu être lu", e);
			return BotReading.unreadable(name);
		} catch (LinkageError err) {
			logger.warn("L'etat du bot " + name + " n'a pas pu été lu, une classe Citizens est introuvable", err);
			return BotReading.unreadable(name);
		}
	}

	/**
	 * Reads {@code SHOULD_SAVE} back off the NPC. The transience guarantee is the
	 * one thing in this package whose failure is invisible until the <em>next</em>
	 * boot, so the value that enforces it is observed on every reading instead of
	 * being taken on trust from the line that writes it.
	 *
	 * @return true when the NPC refuses to be persisted, false when it accepts it,
	 *         and null when Citizens holds no such key at all — a third answer, not
	 *         a pass
	 */
	private static Boolean readSaveSuppression(NPC npc) {
		try {
			var raw = npc.data().get(NPC.Metadata.SHOULD_SAVE);
			return raw instanceof Boolean saved ? !saved : null;
		} catch (Exception e) {
			logger.warn("La persistance d'un bot n'a pas pu être lue", e);
			return null;
		} catch (LinkageError err) {
			logger.warn("La persistance d'un bot n'a pas pu être lue, une classe Citizens est introuvable", err);
			return null;
		}
	}

	private static @Nullable Location spawnOf(Vi6Game game) {
		var world = game.getWorld();
		return world == null ? null : world.getSpawnLocation();
	}

	private static String describe(Vi6Game game, BotReading reading, String moment) {
		var ids = "id NPC " + reading.npcId() + " | id entite " + reading.entityId();
		if (reading.gameMode() == null) {
			return reading.name() + " | " + moment + " | " + ids
					+ " | plus de Player, gamemode illisible | GameModeTrait " + describeTrait(reading)
					+ " | SHOULD_SAVE " + describeSave(reading);
		}
		var spawn = spawnOf(game);
		var spot = isAtWorldSpawn(spawn, reading.location())
				? "au spawn du monde"
				: describeSpot(spawn, reading.location());
		return reading.name() + " | " + moment + " | " + ids + " | gamemode " + reading.gameMode() + " | " + spot
				+ " | GameModeTrait " + describeTrait(reading) + " | SHOULD_SAVE " + describeSave(reading);
	}

	private static String describeSave(BotReading reading) {
		var suppressed = reading.saveSuppressed();
		if (suppressed == null) {
			return "illisible, ce qui n'est pas un succes";
		}
		return suppressed ? "desactive, donc jamais persiste" : "actif, donc ce bot peut survivre au redemarrage";
	}

	private static String describeTrait(BotReading reading) {
		var trait = reading.traitActive();
		if (trait == null) {
			return "illisible, ce qui n'est pas un succes";
		}
		return trait ? "ACTIF, ce qui est un bug" : "absent";
	}

	private static String describeSpot(Location spawn, @Nullable Location location) {
		if (location == null) {
			return "position illisible";
		}
		var spawnWorld = spawn == null ? null : spawn.getWorld();
		var actualWorld = location.getWorld();
		if (spawnWorld == null || actualWorld == null || !spawnWorld.equals(actualWorld)) {
			return "dans le monde " + worldName(actualWorld) + " au lieu de " + worldName(spawnWorld);
		}
		return "HORS DU SPAWN, " + round(location.distance(spawn)) + " blocs";
	}

	private static String worldName(@Nullable World world) {
		return world == null ? "inconnu" : world.getName();
	}

	private static boolean isAtWorldSpawn(@Nullable Location spawn, @Nullable Location location) {
		if (location == null || spawn == null) {
			return false;
		}
		var spawnWorld = spawn.getWorld();
		if (spawnWorld == null || !spawnWorld.equals(location.getWorld())) {
			return false;
		}
		return location.distance(spawn) <= SPAWN_TOLERANCE_BLOCKS;
	}

	private static double round(double value) {
		return Math.round(value * 100) / 100.0;
	}

	private String nextFreeName(NPCRegistry npcRegistry) {
		for (var attempt = 0; attempt < NAME_INDEX_LIMIT; attempt++) {
			nameCounter++;
			if (nameCounter >= NAME_INDEX_LIMIT) {
				nameCounter = 1;
			}
			var candidate = fitName(NAME_PREFIX + nameCounter);
			if (!isNameTaken(npcRegistry, candidate)) {
				return candidate;
			}
		}
		// Sequential names exhausted, which only happens on a registry that already
		// holds NAME_INDEX_LIMIT bots. The fallback goes through the same availability
		// test, it does not skip it.
		for (var attempt = 0; attempt < NAME_INDEX_LIMIT; attempt++) {
			var candidate = fitName(NAME_PREFIX + UUID.randomUUID().toString().substring(0, 12));
			if (!isNameTaken(npcRegistry, candidate)) {
				return candidate;
			}
		}
		throw new IllegalStateException("aucun nom de bot libre n'a pu être généré");
	}

	private boolean isNameTaken(NPCRegistry npcRegistry, String name) {
		for (var existing : npcRegistry) {
			if (name.equalsIgnoreCase(existing.getName())) {
				return true;
			}
		}
		for (var online : Bukkit.getOnlinePlayers()) {
			if (name.equalsIgnoreCase(online.getName())) {
				return true;
			}
		}
		return false;
	}

	private static String fitName(String rawName) {
		return rawName.length() <= NAME_MAX_LENGTH ? rawName : rawName.substring(0, NAME_MAX_LENGTH);
	}

	private static String describeFailure(@Nullable String name, Throwable error) {
		return (name == null ? "un bot sans nom" : name) + " : " + error;
	}

	private static @Nullable Entity readEntity(NPC npc) {
		try {
			return npc.getEntity();
		} catch (Exception e) {
			logger.warn("L'entité d'un bot n'a pas pu être lue", e);
			return null;
		} catch (LinkageError err) {
			logger.warn("L'entité d'un bot n'a pas pu être lue, une classe Citizens est introuvable", err);
			return null;
		}
	}

	private void destroy(NPC npc, @Nullable Entity knownEntity) {
		try {
			npc.destroy();
		} catch (Exception e) {
			logger.warn("Un bot n'a pas pu être détruit, repli sur Entity#remove()", e);
			removeEntity(npc, knownEntity);
		} catch (LinkageError err) {
			logger.warn("Un bot n'a pas pu être détruit, une classe Citizens est introuvable, repli sur "
					+ "Entity#remove()", err);
			removeEntity(npc, knownEntity);
		}
	}

	private void removeEntity(NPC npc, @Nullable Entity knownEntity) {
		try {
			var toRemove = knownEntity != null ? knownEntity : npc.getEntity();
			if (toRemove == null) {
				return;
			}
			toRemove.remove();
		} catch (Exception e) {
			logger.warn("L'entité d'un bot n'a pas pu être retirée", e);
		} catch (LinkageError err) {
			logger.warn("L'entité d'un bot n'a pas pu être retirée, une classe Citizens est introuvable", err);
		}
	}

}
