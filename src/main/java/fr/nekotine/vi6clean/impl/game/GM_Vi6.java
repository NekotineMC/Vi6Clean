package fr.nekotine.vi6clean.impl.game;

import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Team.Option;
import org.bukkit.scoreboard.Team.OptionStatus;
import org.jetbrains.annotations.Nullable;

import fr.nekotine.core.game.Game;
import fr.nekotine.core.game.GameMode;
import fr.nekotine.core.game.GamePhase;
import fr.nekotine.core.game.GameTeam;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.snapshot.PlayerStatusSnaphot;
import fr.nekotine.core.util.CollectionUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.impl.game.phase.PHASE_Vi6_Preparation;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class GM_Vi6 extends GameMode<GD_Vi6>{

	public static final String PREPARATION_PHASE_KEY = "PreparationPhase";
	
	//---------------------RUNTIME
	
	public GM_Vi6(JavaPlugin plugin) {
		super(plugin);
	}
	
	@Override
	public void registerTeams(Game<GD_Vi6> game) {
		var guardTeam = new GameTeam(Component.translatable("color.minecraft.blue").color(NamedTextColor.BLUE));
		var thiefTeam = new GameTeam(Component.translatable("color.minecraft.red").color(NamedTextColor.RED));
		game.getTeams().add(guardTeam);
		game.getTeams().add(thiefTeam);
		var gd = game.getGameData();
		gd.setGuardTeam(guardTeam);
		gd.setThiefTeam(thiefTeam);
	}
	
	@Override
	public void GotoFirstPhase(Game<GD_Vi6> game) {
		GotoGamePhase(game, PREPARATION_PHASE_KEY);
	}
	
	@Override
	protected void globalSetup(Game<GD_Vi6> game) {
		
		var gd = game.getGameData();
		
		// Scoreboard
		var scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
		gd.setScoreboard(scoreboard);
		gd.setScoreboardSidebarObjective(scoreboard.registerNewObjective("ScoreboardSidebar", "dummy", Component.text("vi6")));
		// guard team
		var scoreboardGuardTeam = scoreboard.registerNewTeam("guard");
		scoreboardGuardTeam.setAllowFriendlyFire(false);
		scoreboardGuardTeam.displayName(Component.text("Garde").color(NamedTextColor.BLUE));
		scoreboardGuardTeam.color(NamedTextColor.BLUE);
		scoreboardGuardTeam.setOption(Option.NAME_TAG_VISIBILITY, OptionStatus.NEVER);
		scoreboardGuardTeam.setCanSeeFriendlyInvisibles(true);
		gd.setScoreboardGuardTeam(scoreboardGuardTeam);
		// thief team
		var scoreboardThiefTeam = scoreboard.registerNewTeam("thief");
		scoreboardThiefTeam.setAllowFriendlyFire(false);
		scoreboardThiefTeam.displayName(Component.text("Voleur").color(NamedTextColor.RED));
		scoreboardThiefTeam.color(NamedTextColor.RED);
		scoreboardThiefTeam.setOption(Option.NAME_TAG_VISIBILITY, OptionStatus.NEVER);
		scoreboardThiefTeam.setCanSeeFriendlyInvisibles(true);
		gd.setScoreboardThiefTeam(scoreboardThiefTeam);
		
		// Map
		
		// TODO Enable Majordom
		
	}
	
	@Override
	protected void playerSetup(Game<GD_Vi6> game, Player player, GameTeam team) {
		
		var gd = game.getGameData();
		
		// SNAPSHOT
		gd.getPreGamePlayersStatus().put(player, new PlayerStatusSnaphot().deepSnapshot(player));
		// Trim HashMap (pas opti de faire ca plein de fois mais bon...)
		gd.setPreGamePlayersStatus(CollectionUtil.trimHashMap(gd.getPreGamePlayersStatus()));
	
		// Scoreboard
		player.setScoreboard(gd.getScoreboard());
		//
		player.setViewDistance(15); // Set back to normal
		// Team
		if (team == gd.getGuardTeam()) {
			gd.getScoreboardGuardTeam().addPlayer(player);
		}else {
			gd.getScoreboardThiefTeam().addPlayer(player);
		}
		var wrapper = new PlayerWrapper(player);
		ModuleManager.GetModule(WrappingModule.class).putWrapper(player, wrapper);
	}

	@Override
	protected void globalEnd(Game<GD_Vi6> game) {
		var gd = game.getGameData();
		gd.setScoreboard(null); // Un scoreboard sans reference est supprimé.
	}
	
	@Override
	protected void playerEnd(Game<GD_Vi6> game, Player player, GameTeam team) {
		var snapshot = game.getGameData().getPreGamePlayersStatus().get(player);
		snapshot.patch(player);
		player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
		ModuleManager.GetModule(WrappingModule.class).removeWrapper(player, PlayerWrapper.class);
	}

	@Override
	protected void collectGameData(Game<GD_Vi6> game) {
	}

	@Override
	protected void asyncManageGameData(Game<GD_Vi6> game) {
	}
	
	@Override
	public void onPlayerPostLeave(Game<GD_Vi6> game, Player player) {
		Abort(game);
	}

	@Override
	public void registerGamePhases(Map<String, GamePhase<GD_Vi6, ? extends GameMode<GD_Vi6>>> _gamePhasesMap) {
		_gamePhasesMap.put(PREPARATION_PHASE_KEY, new PHASE_Vi6_Preparation(null));
	}

	@Override
	public Game<GD_Vi6> createGame(@Nullable Map<String, Object> flattenedGameData) {
		return new Game<GD_Vi6>(this, new GD_Vi6(flattenedGameData));
	}
}
