package fr.nekotine.vi6clean.def.game;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.NameTagVisibility;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.scoreboard.Team.Option;
import org.bukkit.scoreboard.Team.OptionStatus;

import fr.nekotine.core.game.Game;
import fr.nekotine.core.game.GamePhase;
import fr.nekotine.core.game.GameTeam;
import fr.nekotine.core.lobby.GameModeIdentifier;
import fr.nekotine.core.map.MapIdentifier;
import fr.nekotine.core.map.MapModule;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.snapshot.PlayerStatusSnaphot;
import fr.nekotine.core.snapshot.Snapshot;
import fr.nekotine.vi6clean.def.game.phase.PHASE_Vi6_Preparation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class GM_Vi6 extends Game{

	public static final String PREPARATION_PHASE_KEY = "PreparationPhase";
	
	public static final GameModeIdentifier IDENTIFIER =
			new GameModeIdentifier(
					"vi6",
					Component.text("Voleur Industriel 6").color(NamedTextColor.BLUE)) {
		@Override
		public Game generateTypedGame() {
			return new GM_Vi6();
		}
	};
	
	//---------------------
	
	private MapIdentifier mapId;
	
	private GameTeam _guardTeam;
	
	private GameTeam _thiefTeam;
	
	private Scoreboard _scoreboard;
	
	private Objective _scoreboardSidebarObjective;
	
	private Team _scoreboardGuardTeam;
	
	private Team _scoreboardThiefTeam;
	
	//---------------------RUNTIME
	
	private Map<Player, Snapshot<Player>> playersStatusSnapshot = new HashMap<>();
	
	@SuppressWarnings("unused")
	private MAP_Vi6 map;
	
	@Override
	public void registerTeams(List<GameTeam> teamList) {
		_guardTeam = new GameTeam(Component.translatable("color.minecraft.blue").color(NamedTextColor.BLUE));
		_thiefTeam = new GameTeam(Component.translatable("color.minecraft.red").color(NamedTextColor.RED));
		teamList.add(_guardTeam);
		teamList.add(_thiefTeam);
	}

	@Override
	public void registerGamePhases(Map<String, GamePhase<? extends Game>> _gamePhasesMap) {
		_gamePhasesMap.put(PREPARATION_PHASE_KEY, new PHASE_Vi6_Preparation(this));
	}
	
	@Override
	public void GotoFirstPhase() {
		GotoGamePhase(PREPARATION_PHASE_KEY);
	}
	
	@Override
	protected void setup() {
		
		// Scoreboard
		_scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
		_scoreboardSidebarObjective = _scoreboard.registerNewObjective("ScoreboardSidebar", "dummy", IDENTIFIER.getName());
		// guard team
		_scoreboardGuardTeam = _scoreboard.registerNewTeam("guard");
		_scoreboardGuardTeam.setAllowFriendlyFire(false);
		_scoreboardGuardTeam.displayName(Component.text("Garde").color(NamedTextColor.BLUE));
		_scoreboardGuardTeam.color(NamedTextColor.BLUE);
		_scoreboardGuardTeam.setOption(Option.NAME_TAG_VISIBILITY, OptionStatus.NEVER);
		_scoreboardGuardTeam.setCanSeeFriendlyInvisibles(true);
		// thief team
		_scoreboardThiefTeam = _scoreboard.registerNewTeam("thief");
		_scoreboardThiefTeam.setAllowFriendlyFire(false);
		_scoreboardThiefTeam.displayName(Component.text("Voleur").color(NamedTextColor.RED));
		_scoreboardThiefTeam.color(NamedTextColor.RED);
		_scoreboardThiefTeam.setOption(Option.NAME_TAG_VISIBILITY, OptionStatus.NEVER);
		_scoreboardThiefTeam.setCanSeeFriendlyInvisibles(true);
		
		// Map
		map = (MAP_Vi6) ModuleManager.GetModule(MapModule.class).loadMap(mapId);
		
		// Per player actions
		for (var player : getPlayerList()) {
			//SNAPSHOT
			playersStatusSnapshot.put(player, new PlayerStatusSnaphot().deepSnapshot(player));
			/**
			 * La majorité des modifications sont faites dans la phase
			 */
			player.setScoreboard(_scoreboard);
			// TODO Wrap Player (WrapperBase + le revoir est pas opti)
		}
		playersStatusSnapshot = new HashMap<>(playersStatusSnapshot); // Trim HashMap
		
		// TODO Enable Majordom
		
	}

	@Override
	protected void end() {
		for (var player : getPlayerList()) {
			var snapshot = playersStatusSnapshot.get(player);
			snapshot.patch(player);
		}
		_scoreboard = null;
	}

	@Override
	protected void collectGameData() {
	}

	@Override
	protected void asyncManageGameData() {
	}
	
	@Override
	public void onPlayerPreLeave(Player player) {
		super.onPlayerPreLeave(player);
		Abort();
	}
	
	// Events Handling
	
	// Getter Setter
	
	public void setMapId(MapIdentifier mapId) {
		this.mapId = mapId;
	}
	
	public MapIdentifier getMapId()
	{
		return mapId;
	}
	
	public GameTeam getGuardTeam() {
		return _guardTeam;
	}
	
	public GameTeam getThiefTeam() {
		return _thiefTeam;
	}
	
	public Scoreboard getScoreboard() {
		return _scoreboard;
	}
	
	public Objective getScoreboardSidebarObjective() {
		return _scoreboardSidebarObjective;
	}
	
	public Team getScoreboardGuardTeam() {
		return _scoreboardGuardTeam;
	}
	
	public Team getScoreboardThiefTeam() {
		return _scoreboardThiefTeam;
	}
}
