package fr.nekotine.vi6clean.impl.game;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;

import fr.nekotine.core.game.GameData;
import fr.nekotine.core.game.GameTeam;
import fr.nekotine.core.snapshot.Snapshot;
import fr.nekotine.vi6clean.impl.tool.ToolHandlerContainer;

public class GD_Vi6 extends GameData{

	public GD_Vi6(Map<String, Object> objectMap) {
		super(objectMap);
	}

	@Override
	public @NotNull Map<String, Object> serialize() {
		return new HashMap<>();
	}
	
	// ---- Values
	
	private GameTeam guardTeam;
	
	private GameTeam thiefTeam;
	
	private Scoreboard scoreboard;
	
	private Objective scoreboardSidebarObjective;
	
	private Team scoreboardGuardTeam;
	
	private Team scoreboardThiefTeam;
	
	private Map<Player, Snapshot<Player>> preGamePlayersStatus = new HashMap<>();
	
	private ToolHandlerContainer toolHandlerContainer;
	
	// ---- Getter/Setter
	
	public void setGuardTeam(GameTeam team) {
		guardTeam = team;
	}
	
	public GameTeam getGuardTeam() {
		return guardTeam;
	}
	
	public void setThiefTeam(GameTeam team) {
		thiefTeam = team;
	}
	
	public GameTeam getThiefTeam() {
		return thiefTeam;
	}

	public Scoreboard getScoreboard() {
		return scoreboard;
	}

	public void setScoreboard(Scoreboard scoreboard) {
		this.scoreboard = scoreboard;
	}

	public Objective getScoreboardSidebarObjective() {
		return scoreboardSidebarObjective;
	}

	public void setScoreboardSidebarObjective(Objective scoreboardSidebarObjective) {
		this.scoreboardSidebarObjective = scoreboardSidebarObjective;
	}

	public Team getScoreboardGuardTeam() {
		return scoreboardGuardTeam;
	}

	public void setScoreboardGuardTeam(Team scoreboardGuardTeam) {
		this.scoreboardGuardTeam = scoreboardGuardTeam;
	}

	public Team getScoreboardThiefTeam() {
		return scoreboardThiefTeam;
	}

	public void setScoreboardThiefTeam(Team scoreboardThiefTeam) {
		this.scoreboardThiefTeam = scoreboardThiefTeam;
	}

	public Map<Player, Snapshot<Player>> getPreGamePlayersStatus() {
		return preGamePlayersStatus;
	}

	public void setPreGamePlayersStatus(Map<Player, Snapshot<Player>> preGamePlayersStatus) {
		this.preGamePlayersStatus = preGamePlayersStatus;
	}
	
	public ToolHandlerContainer getToolHandlerContainer() {
		return toolHandlerContainer;
	}
	
	public void setToolHandlerContainer(ToolHandlerContainer toolHandlerContainer) {
		this.toolHandlerContainer = toolHandlerContainer;
	}
}
