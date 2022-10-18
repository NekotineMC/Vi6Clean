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

	private static final String PREPARATION_PHASE_KEY = "PreparationPhase";
	
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
	
	//---------------------RUNTIME
	
	private Map<Player, Snapshot<Player>> playersStatusSnapshot = new HashMap<>();
	
	@SuppressWarnings("unused")
	private MAP_Vi6 map;
	
	@Override
	public void registerTeams(List<GameTeam> teamList) {
		teamList.add(new GameTeam(Component.translatable("color.minecraft.blue").color(NamedTextColor.BLUE)));
		teamList.add(new GameTeam(Component.translatable("color.minecraft.red").color(NamedTextColor.RED)));
	}

	@Override
	public void registerGamePhases(Map<String, GamePhase> _gamePhasesMap) {
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
			playersStatusSnapshot.put(player, new PlayerStatusSnaphot().deepSnapshot(player));
		}
		playersStatusSnapshot = new HashMap<>(playersStatusSnapshot); // Trim HashMap
		
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
}
