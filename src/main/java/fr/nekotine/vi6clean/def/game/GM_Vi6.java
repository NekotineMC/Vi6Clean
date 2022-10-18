package fr.nekotine.vi6clean.def.game;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;

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
		
		map = (MAP_Vi6) ModuleManager.GetModule(MapModule.class).loadMap(mapId);
		
		for (var player : getPlayerList()) {
			//SNAPSHOT
			playersStatusSnapshot.put(player, new PlayerStatusSnaphot().deepSnapshot(player));
			/**
			 * La majorité des modifications sont faites dans la phase
			 */
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
}
