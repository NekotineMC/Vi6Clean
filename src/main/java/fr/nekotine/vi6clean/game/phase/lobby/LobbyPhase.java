package fr.nekotine.vi6clean.game.phase.lobby;

import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.RenderType;

import fr.nekotine.core.logging.NekotineLogger;
import fr.nekotine.core.usable.Usable;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.vi6clean.game.Game;
import fr.nekotine.vi6clean.game.Vi6Game;
import fr.nekotine.vi6clean.game.phase.AbstractPhase;
import fr.nekotine.vi6clean.game.phase.lobby.event.LobbyPlayerReadyEvent;
import fr.nekotine.vi6clean.menu.LobbyMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

public class LobbyPhase extends AbstractPhase<LobbyPlayerWrapper> implements Listener{

	private final ComponentLogger logger = NekotineLogger.make();
	
	private final Game _game;
	
	private Objective _playerListScoreboardObjective;
	
	private Usable openMenuUsable;
	
	public LobbyPhase(Game game) {
		_game = game;
	}
	
	@Override
	public void onStart() {
		logger.info("Début de la phase Lobby");
		// Inversion des équipes existantes
		var lastGuards = _game.getGuards().stream().collect(Collectors.toList());
		var lastThieves = _game.getThiefs().stream().collect(Collectors.toList());
		lastThieves.forEach(_game::addPlayerInGuards);
		lastGuards.forEach(_game::addPlayerInThiefs);
		
		// Ajout les joueurs qui n'ont pas été ajoutés
		for (var p : Bukkit.getServer().getOnlinePlayers()) {
			_game.addPlayer(p);
		}
		
		// Création du scoreboard pour la liste des joueurs
		var scoreboard = _game.getScoreboard();
		_playerListScoreboardObjective = scoreboard.getObjective("playerListing");
		if (_playerListScoreboardObjective == null) {
			_playerListScoreboardObjective = scoreboard.registerNewObjective("playerListing", Criteria.DUMMY,
					Component.text("Liste des joueurs", NamedTextColor.GOLD), RenderType.INTEGER);
		}
		_playerListScoreboardObjective.setDisplaySlot(DisplaySlot.SIDEBAR);
		
		//Creation de l'Usable pour ouvir le menu
		openMenuUsable = LobbyMenu.openMenuItem(this);
		openMenuUsable.register();
		
		// définir le wrapper pour tous les joueurs
		for (var p : Bukkit.getOnlinePlayers()) {
			setupPlayer(p);
		}
		
		// Abonnement aux évenements
		EventUtil.register(this);
	}

	@Override
	public void onEnd() {
		logger.info("Fin de la phase Lobby");
		EventUtil.unregister(this);
		
		// Teardown player
		for (var p : Bukkit.getOnlinePlayers()) {
			setupPlayer(p);
		}
		
		if (_playerListScoreboardObjective != null){
			_playerListScoreboardObjective.unregister();
			_playerListScoreboardObjective = null;
		}
		if (openMenuUsable != null) {
			openMenuUsable.unregister();
			openMenuUsable = null;
		}
	}
	
	private void setupPlayer(Player player) {
		// TODO trouver un moyen propre de créer cette classe depuis le wrapper de la phase précédente
		setWrapperFor(player, new LobbyPlayerWrapper(player));
		_playerListScoreboardObjective.getScore(player).setScore(0);
		player.getInventory().clear();
		player.getInventory().addItem(openMenuUsable.getItemStack());
		player.teleport(_game.getWorld().getSpawnLocation());
	}
	
	private void teardownPlayer(Player player) {
		player.getInventory().clear();
		_playerListScoreboardObjective.getScore(player).resetScore();
	}
	
	@EventHandler
	public void onPlayerJoined(PlayerJoinEvent evt) {
		setupPlayer(evt.getPlayer());
	}
	
	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent evt) {
		teardownPlayer(evt.getPlayer());
	}
	
	@EventHandler
	public void onPlayerReady(LobbyPlayerReadyEvent evt) {
		for (var p : Bukkit.getOnlinePlayers()) {
			var wrap = wrapperFor(p);
			if (!wrap.isReady()) {
				return;
			}
		}
		_game.goToPreparationPhase();
	}

}
