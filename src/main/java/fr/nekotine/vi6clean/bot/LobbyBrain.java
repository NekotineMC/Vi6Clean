package fr.nekotine.vi6clean.bot;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import fr.nekotine.core.bot.Bot;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.configuration.ConfigManager;
import fr.nekotine.vi6clean.game.Vi6Game;
import fr.nekotine.vi6clean.wrapper.LobbyPhasePlayerWrapper;
import io.papermc.paper.util.Tick;

/**
 * In each lobby, joins the team of its intent with {@code /game team} at once,
 * then readies up with {@code /game ready} once
 * {@code bots.lobby-ready-delay-seconds} have passed, so an operator can still
 * type between two games of a run.
 */
public class LobbyBrain implements BotBrain {

	private final BotTeamIntent intent;

	// Commands land after the tick and /game ready toggles: act once per lobby,
	// identified by the bot's lobby wrapper
	private @Nullable LobbyPhasePlayerWrapper actedIn;

	private @Nullable BotTeamIntent picked;

	// Ticks since this brain's first tick in the current lobby
	private int ticksInLobby;

	// The ready delay in ticks, read when the lobby is first seen
	private int readyAfterTicks;

	private boolean readied;

	public LobbyBrain(BotTeamIntent intent) {
		this.intent = intent;
	}

	@Override
	public void tick(Bot bot) {
		var wrapper = Ioc.resolve(WrappingModule.class).getWrapperOptional(bot.player(), LobbyPhasePlayerWrapper.class)
				.orElse(null);
		if (wrapper == null) {
			actedIn = null;
			picked = null;
			readied = false;
			return;
		}
		if (wrapper != actedIn) {
			actedIn = wrapper;
			ticksInLobby = 0;
			readied = false;
			readyAfterTicks = Tick.tick()
					.fromDuration(Ioc.resolve(ConfigManager.class).getConfig().bots().lobbyReadyDelay());
			picked = pickTeam(bot);
			bot.sendCommand("game team " + picked.code());
		} else {
			ticksInLobby++;
		}
		if (!readied && ticksInLobby >= readyAfterTicks) {
			readied = true;
			bot.sendCommand("game ready");
		}
	}

	/**
	 * @return the team this brain asked for in the current lobby, or {@code null}
	 *         if it has not acted yet; its command may not have landed
	 */
	@Nullable BotTeamIntent pickedTeam() {
		return picked;
	}

	private BotTeamIntent pickTeam(Bot bot) {
		if (intent != BotTeamIntent.ANY) {
			return intent;
		}
		// The join path already put the bot in a team: compare the others, with
		// the picks of brains whose command has not landed yet
		var self = bot.player();
		var game = Ioc.resolve(Vi6Game.class);
		var bots = Ioc.resolve(ModuleManager.class).get(Vi6Bots.class);
		var guards = 0;
		var thiefs = 0;
		for (var player : game.getPlayerList()) {
			if (player.equals(self)) {
				continue;
			}
			var team = bots.pickedTeam(player);
			if (team == null) {
				team = currentTeam(game, player);
			}
			if (team == BotTeamIntent.GUARD) {
				guards++;
			} else if (team == BotTeamIntent.THIEF) {
				thiefs++;
			}
		}
		return thiefs < guards ? BotTeamIntent.THIEF : BotTeamIntent.GUARD;
	}

	private static @Nullable BotTeamIntent currentTeam(Vi6Game game, Player player) {
		if (game.getGuards().contains(player)) {
			return BotTeamIntent.GUARD;
		}
		return game.getThiefs().contains(player) ? BotTeamIntent.THIEF : null;
	}
}
