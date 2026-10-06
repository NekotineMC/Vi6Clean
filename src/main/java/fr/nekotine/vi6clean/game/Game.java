package fr.nekotine.vi6clean.game;

import java.util.stream.Stream;

import org.bukkit.Bukkit;
import org.bukkit.scoreboard.Scoreboard;

import fr.nekotine.core.logging.NekotineLogger;
import fr.nekotine.vi6clean.game.phase.Phase;
import fr.nekotine.vi6clean.game.phase.PlayerWrapper;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

public class Game {

	private final ComponentLogger logger = NekotineLogger.make(); //TODO HERE
	
	private final Scoreboard _scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
	
	private Phase<? extends PlayerWrapper> _currentPhase;
	
	
	public void goToPreparationPhase() {
		if (_currentPhase != null) {
			try {
				_currentPhase.onEnd();
			}catch(Exception e) {
				logger.error("Une erreur est survenue lors du déchargement de la phase", e);
			}
		}
	}
	
	public Scoreboard getScoreboard() {
		return _scoreboard;
	}
	
	public Stream<PlayerWrapper> guards(){
		return Bukkit.getOnlinePlayers().stream().map(p -> (PlayerWrapper)_currentPhase.wrapperFor(p)).filter(w -> w.team() == Vi6Team.Guard);
	}
	
	public Stream<PlayerWrapper> thieves(){
		return Bukkit.getOnlinePlayers().stream().map(p -> (PlayerWrapper)_currentPhase.wrapperFor(p)).filter(w -> w.team() == Vi6Team.Thief);
	}
	
}
