package fr.nekotine.vi6clean.impl.game;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.scoreboard.Team.Option;
import org.bukkit.scoreboard.Team.OptionStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import fr.nekotine.core.game.phase.CollectionPhase;
import fr.nekotine.core.game.phase.eventargs.PhaseFailureEventArgs;
import fr.nekotine.core.snapshot.PlayerStatusSnaphot;
import fr.nekotine.core.snapshot.Snapshot;
import fr.nekotine.core.util.EntityUtil;
import fr.nekotine.vi6clean.impl.game.phase.Vi6PhaseLobby;
import fr.nekotine.vi6clean.impl.game.team.GuardTeam;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class Vi6Game extends CollectionPhase<Player> implements ForwardingAudience{

	protected Scoreboard scoreboard;
	
	private Team scoreboardGuard;
	
	private Team scoreboardThief;
	
	private final Map<Player, Snapshot<Player>> playerSnapshots = new HashMap<>();
	
	private final GuardTeam guards = new GuardTeam();
	
	private final GuardTeam thiefs = new GuardTeam();
	
	private final CollectionPhase<Player> lobbyPhase = new Vi6PhaseLobby(this::onLobbyPhaseComplete, this::cancel, this::all);
	
	private CollectionPhase<Player> currentPhase = lobbyPhase;
	
	public Vi6Game(Consumer<PhaseFailureEventArgs> onFailure) {
		super(onFailure);
		setItemStreamSupplier(this::all);
		guards.setPlayerAddCallback(this::onJoinGuard);
		guards.setPlayerRemoveCallback(this::onLeaveGuard);
		thiefs.setPlayerAddCallback(this::onJoinThief);
		thiefs.setPlayerRemoveCallback(this::onLeaveThief);
	}

	@Override
	public void globalSetup() {
		scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
		scoreboardGuard = scoreboard.registerNewTeam("guard");
		scoreboardThief = scoreboard.registerNewTeam("thief");
		scoreboardGuard.color(NamedTextColor.BLUE);
		scoreboardThief.color(NamedTextColor.RED);
		scoreboardGuard.displayName(Component.text("Garde", NamedTextColor.BLUE));
		scoreboardThief.displayName(Component.text("Voleur", NamedTextColor.RED));
		scoreboardGuard.prefix(Component.text("[Garde] ", NamedTextColor.BLUE));
		scoreboardThief.prefix(Component.text("[Voleur] ", NamedTextColor.RED));
		Set.of(scoreboardGuard, scoreboardThief).forEach(team ->{
			team.setAllowFriendlyFire(false);
			team.setCanSeeFriendlyInvisibles(true);
			team.setOption(Option.NAME_TAG_VISIBILITY, OptionStatus.FOR_OWN_TEAM);
			team.setOption(Option.COLLISION_RULE, OptionStatus.NEVER);
		});
		// TODO Setup majordom
		currentPhase.setup();
	}

	@Override
	public void globalTearDown() {
		scoreboardGuard.unregister();
		scoreboardThief.unregister();
		scoreboard = null;
	}

	@Override
	public void itemSetup(Player item) {
		playerSnapshots.put(item, new PlayerStatusSnaphot().snapshot(item));
		EntityUtil.clearPotionEffects(item);
		EntityUtil.defaultAllAttributes(item);
		item.getInventory().clear();
		currentPhase.itemSetup(item);
	}

	@Override
	public void itemTearDown(Player item) {
		currentPhase.itemTearDown(item);
		playerSnapshots.get(item).patch(item);
	}
	
	public Stream<Player> all(){
		return Stream.concat(guards.stream(), thiefs.stream());
	}
	
	@Override
	public @NotNull Iterable<? extends Audience> audiences() {
		return guards.audiences();
	}
	
	public void addPlayer(Player player) {
		var team = guards;
		if (guards.size() > thiefs.size()) {
			team = thiefs;
		}
		team.add(player);
	}
	
	public void removePlayer(Player player) {
		guards.remove(player);
		thiefs.remove(player);
	}
	
	public @Nullable Scoreboard getScoreboard() {
		return scoreboard;
	}
	
	// Event handlers
	
	private void onJoinGuard(Player player) {
		itemSetup(player);
		player.setScoreboard(scoreboard);
		scoreboardGuard.addPlayer(player);
	}
	
	private void onLeaveGuard(Player player) {
		itemTearDown(player);
		scoreboardGuard.removePlayer(player);
		player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
	}
	
	private void onJoinThief(Player player) {
		itemSetup(player);
		player.setScoreboard(scoreboard);
		scoreboardThief.addPlayer(player);
	}
	
	private void onLeaveThief(Player player) {
		itemTearDown(player);
		scoreboardThief.removePlayer(player);
		player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
	}
	
	private void onLobbyPhaseComplete() {
		
	}
	
}
