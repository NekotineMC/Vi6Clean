package fr.nekotine.vi6clean.it;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import fr.nekotine.core.bot.Bot;
import fr.nekotine.core.bot.BotRoster;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.map.IMapModule;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.vi6clean.bot.Vi6Bots;
import fr.nekotine.vi6clean.bot.Vi6GameReset;
import fr.nekotine.vi6clean.configuration.ConfigManager;
import fr.nekotine.vi6clean.game.Vi6Game;
import fr.nekotine.vi6clean.game.Vi6Game.ForcedKoth;
import fr.nekotine.vi6clean.game.phase.Vi6PhaseInfiltration;
import fr.nekotine.vi6clean.game.phase.Vi6PhasePreparation;
import fr.nekotine.vi6clean.map.ThiefSpawn;
import fr.nekotine.vi6clean.map.Vi6Map;
import fr.nekotine.vi6clean.tool.Tool;
import fr.nekotine.vi6clean.tool.ToolHandler;
import fr.nekotine.vi6clean.tool.ToolHandlerContainer;
import fr.xxgoldenbluexx.paperjunitserver.PaperServer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Integration-test support, in two groups. The set-up hooks set a Vi6 game up
 * for a test and reset it between tests: call them from the test thread; each
 * runs its game work on the main thread, and those that then wait
 * {@link #ACK_TICKS} ticks, long enough for a bot to ack, wait off it. The run
 * and log helpers ({@link #shortTimersWithoutKoths}, {@link #run},
 * {@link #dispatch}, {@link #awaitGameEnded}, {@link #gameEndedLines},
 * {@link #savedFiles}) drive and check {@code /bot run} games, which tests of
 * an unattended run use instead of the set-up hooks.
 */
public final class Vi6TestHooks {

	/** Ticks for a teleport to reach a bot and its ack to come back. */
	private static final int ACK_TICKS = 3;

	/** Ticks a game of a {@code /bot run} may take, map loads included. */
	public static final int GAME_TIMEOUT_TICKS = 600;

	/** The lobby ready delay {@link #shortLobbyReadyDelay} sets, in ticks. */
	public static final int SHORT_LOBBY_READY_DELAY_TICKS = 20;

	/** The summary line {@code Vi6Run} logs for each game of a run. */
	public static final Pattern GAME_ENDED = Pattern.compile(
			"Game ended: game=(\\d+), reason=(lost-timer|all-thieves-left), stolen=(\\d+), escaped=(\\d+), duration=(\\d+)s, seed=(-?\\d+)");

	private Vi6TestHooks() {
	}

	/**
	 * Joins {@code count} idle bots ({@code Hook_1}, {@code Hook_2}...) at the game
	 * world's spawn; the lobby puts each on a team as it joins. Then waits
	 * {@link #ACK_TICKS} ticks, long enough for a bot to ack its join.
	 */
	public static List<Bot> spawnBots(int count) throws Exception {
		var bots = PaperServer.onMain(() -> {
			var roster = Ioc.resolve(BotRoster.class);
			var vi6Bots = Ioc.resolve(ModuleManager.class).get(Vi6Bots.class);
			var location = game().getWorld().getSpawnLocation().add(0.5, 0, 0.5);
			var spawned = new ArrayList<Bot>();
			for (int i = 1; i <= count; i++) {
				var bot = roster.spawn("Hook_" + i, location);
				vi6Bots.setBrain(bot, null);
				spawned.add(bot);
			}
			return spawned;
		});
		PaperServer.awaitTicks(ACK_TICKS);
		return bots;
	}

	/** Puts each player on its team, joining the game if needed. */
	public static void putOnTeams(List<Player> guards, List<Player> thieves) throws Exception {
		PaperServer.onMain(() -> {
			var game = game();
			guards.forEach(game::addPlayerInGuards);
			thieves.forEach(game::addPlayerInThiefs);
			return null;
		});
	}

	/**
	 * Goes to preparation, as a ready lobby does: the map loads, guards spawn in it
	 * and thieves in the minimap.
	 */
	public static void goToPreparation() throws Exception {
		PaperServer.onMain(() -> {
			var machine = game().getPhaseMachine();
			machine.goTo(Vi6PhasePreparation.class, null);
			if (machine.getPhase(Vi6PhasePreparation.class) == null) {
				throw new IllegalStateException("preparation did not start, see the server log");
			}
			return null;
		});
		PaperServer.awaitTicks(ACK_TICKS);
	}

	/**
	 * Goes straight to infiltration, skipping preparation (no rune, no shop, no
	 * guard weapons): the map loads, every thief enters at {@code thiefSpawn} and
	 * guards spawn inside.
	 *
	 * @param thiefSpawn
	 *            the thief spawn's name, or {@code null} for the first by name
	 */
	public static void goToInfiltration(@Nullable String thiefSpawn) throws Exception {
		PaperServer.onMain(() -> {
			var game = game();
			var spawn = thiefSpawn(thiefSpawn);
			var spawns = new HashMap<Player, ThiefSpawn>();
			game.getThiefs().forEach(thief -> spawns.put(thief, spawn));
			var machine = game.getPhaseMachine();
			machine.goTo(Vi6PhaseInfiltration.class, spawns);
			if (machine.getPhase(Vi6PhaseInfiltration.class) == null) {
				throw new IllegalStateException("infiltration did not start, see the server log");
			}
			// Preparation spawns the guards: going straight to infiltration skips it
			game.getGuards().spawnInMap();
			return null;
		});
		PaperServer.awaitTicks(ACK_TICKS);
	}

	/**
	 * @return the current map's thief spawn named {@code name}, or the first by
	 *         name for {@code null}; main thread only
	 */
	public static ThiefSpawn thiefSpawn(@Nullable String name) {
		var spawns = currentMap().getThiefSpawns();
		if (name == null) {
			return spawns.entrySet().stream().min(Map.Entry.comparingByKey())
					.orElseThrow(() -> new IllegalStateException("the map has no thief spawn")).getValue();
		}
		var spawn = spawns.get(name);
		if (spawn == null) {
			throw new IllegalArgumentException(
					"unknown thief spawn " + name + ", the map has " + spawns.keySet().stream().sorted().toList());
		}
		return spawn;
	}

	/**
	 * @return the current map's content, as the next map phase loads it; main
	 *         thread only
	 */
	public static Vi6Map currentMap() {
		var mapModule = Ioc.resolve(IMapModule.class);
		return mapModule.getContent(mapModule.getMapMetadata(game().getMapName()), Vi6Map.class);
	}

	/**
	 * Gives {@code player} a new tool as its registered stack, as a purchase does
	 * without the shop.
	 *
	 * @param toolCode
	 *            the tool's {@code @ToolCode}
	 * @return the new tool
	 */
	public static Tool giveTool(Player player, String toolCode) throws Exception {
		return PaperServer.onMain(() -> {
			var handler = Ioc.resolve(ToolHandlerContainer.class).getHandlers().stream()
					.filter(h -> h.getToolCode().equals(toolCode)).findFirst()
					.orElseThrow(() -> new IllegalArgumentException("unknown tool " + toolCode));
			return give(handler, player);
		});
	}

	private static <T extends Tool> T give(ToolHandler<T> handler, Player player) {
		var tool = handler.makeNewTool();
		handler.attachToPlayer(tool, player);
		return tool;
	}

	/**
	 * Teleports {@code player}, then waits {@link #ACK_TICKS} ticks, long enough
	 * for a bot to ack the teleport.
	 */
	public static void place(Player player, Location location) throws Exception {
		PaperServer.onMain(() -> {
			if (!player.teleport(location)) {
				throw new IllegalStateException("teleport of " + player.getName() + " to " + location + " refused");
			}
			return null;
		});
		PaperServer.awaitTicks(ACK_TICKS);
	}

	/**
	 * Spawns exactly these KoTHs, with these effects, in the next map phases (none
	 * for no argument), until {@link #reset()}.
	 */
	public static void forceKoths(ForcedKoth... koths) throws Exception {
		PaperServer.onMain(() -> {
			game().setForcedKoths(List.of(koths));
			return null;
		});
	}

	/** Resets the game with {@link Vi6GameReset#reset()}, then lets it settle. */
	public static void reset() throws Exception {
		PaperServer.onMain(() -> {
			Vi6GameReset.reset();
			return null;
		});
		PaperServer.awaitTicks(2);
	}

	/** @return the guard spawns of the current map; main thread only */
	public static List<Location> guardSpawns() {
		var world = game().getWorld();
		return currentMap().getGuardSpawns().stream().map(l -> l.toLocation(world))
				.sorted(Comparator.comparingDouble(Location::getX).thenComparingDouble(Location::getZ)).toList();
	}

	/**
	 * Waits until the game is in {@code phase}, failing with an
	 * {@link AssertionError} after {@code timeoutTicks} ticks.
	 *
	 * @return the server tick at which the phase was first seen
	 */
	public static int awaitPhase(Class<?> phase, int timeoutTicks) throws Exception {
		var reachedAt = new int[]{-1};
		PaperServer.awaitUntil(() -> {
			if (game().getPhaseMachine().getPhase(phase) != null) {
				reachedAt[0] = Bukkit.getCurrentTick();
				return true;
			}
			return false;
		}, timeoutTicks);
		return reachedAt[0];
	}

	/** @return the Vi6 game */
	public static Vi6Game game() {
		return Ioc.resolve(Vi6Game.class);
	}

	/**
	 * Short phases, a {@link #shortLobbyReadyDelay short lobby ready delay} and no
	 * KoTH, until {@link #reset()}: a game of an idle-bot run then ends on the lost
	 * timer within {@link #GAME_TIMEOUT_TICKS}.
	 */
	public static void shortTimersWithoutKoths() throws Exception {
		PaperServer.onMain(() -> {
			var config = Ioc.resolve(ConfigManager.class).getConfig().game();
			config.preparation().duration(Duration.ofSeconds(2));
			config.infiltration().lostDuration(Duration.ofSeconds(3));
			config.infiltration().endSpectatorTime(Duration.ofMillis(500));
			return null;
		});
		shortLobbyReadyDelay();
		forceKoths();
	}

	/**
	 * Sets {@code bots.lobby-ready-delay-seconds} to
	 * {@link #SHORT_LOBBY_READY_DELAY_TICKS} ticks until {@link #reset()} (which
	 * reloads {@code config.yml}): lobby brains ready up that long after their team
	 * pick instead of 10 s. Call it in the set-up of every test whose bots go
	 * through a lobby by their brains, after any reset.
	 */
	public static void shortLobbyReadyDelay() throws Exception {
		PaperServer.onMain(() -> {
			Ioc.resolve(ConfigManager.class).getConfig().bots()
					.lobbyReadyDelay(Duration.ofMillis(SHORT_LOBBY_READY_DELAY_TICKS * 50L));
			return null;
		});
	}

	/**
	 * Waits for a {@code Game ended: game=<game>} line, then asserts it is the only
	 * one.
	 */
	public static void awaitGameEnded(LogCapture capture, int game) throws Exception {
		PaperServer.awaitUntil(() -> !gameEndedLines(capture, game).isEmpty(), GAME_TIMEOUT_TICKS);
		var lines = gameEndedLines(capture, game);
		if (lines.size() != 1) {
			throw new AssertionError("game " + game + " lines: " + lines);
		}
	}

	/**
	 * @return the captured {@code Game ended: game=<game>} lines, each asserted
	 *         {@link #GAME_ENDED well-formed}
	 */
	public static List<String> gameEndedLines(LogCapture capture, int game) {
		var lines = capture.messages().stream().filter(m -> m.startsWith("Game ended: game=" + game + ",")).toList();
		for (var line : lines) {
			if (!GAME_ENDED.matcher(line).matches()) {
				throw new AssertionError("malformed line: " + line);
			}
		}
		return lines;
	}

	/** @return the player's data, stats and advancements files; main thread only */
	public static List<Path> savedFiles(Player player) {
		var server = MinecraftServer.getServer();
		var uuid = player.getUniqueId().toString();
		return List.of(server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(uuid + ".dat"),
				server.getWorldPath(LevelResource.PLAYER_STATS_DIR).resolve(uuid + ".json"),
				server.getWorldPath(LevelResource.PLAYER_ADVANCEMENTS_DIR).resolve(uuid + ".json"));
	}

	/** Dispatches {@code command} on the main thread; see {@link #dispatch}. */
	public static List<String> run(String command) throws Exception {
		return PaperServer.onMain(() -> dispatch(command));
	}

	/**
	 * Dispatches {@code command} as {@link Bukkit#dispatchCommand} does, from a
	 * sender that records what it is sent. Main thread only.
	 *
	 * @return the plain text of the messages sent back
	 */
	public static List<String> dispatch(String command) {
		var out = new ArrayList<String>();
		var sender = Bukkit.createCommandSender(c -> out.add(PlainTextComponentSerializer.plainText().serialize(c)));
		if (!Bukkit.dispatchCommand(sender, command)) {
			throw new AssertionError("command not dispatched: " + command);
		}
		return out;
	}
}
