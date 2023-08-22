package fr.nekotine.vi6clean.impl.game;

import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Stream;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.scoreboard.Team.Option;
import org.bukkit.scoreboard.Team.OptionStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import fr.nekotine.core.NekotineCore;
import fr.nekotine.core.game.phase.CollectionPhase;
import fr.nekotine.core.game.phase.eventargs.PhaseFailureEventArgs;
import fr.nekotine.core.map.MapModule;
import fr.nekotine.core.snapshot.PlayerStatusSnaphot;
import fr.nekotine.core.util.EntityUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.Vi6Main;
import fr.nekotine.vi6clean.impl.game.phase.Vi6PhaseLobby;
import fr.nekotine.vi6clean.impl.game.phase.Vi6PhasePreparation;
import fr.nekotine.vi6clean.impl.game.team.GuardTeam;
import fr.nekotine.vi6clean.impl.game.team.ThiefTeam;
import fr.nekotine.vi6clean.impl.map.Vi6Map;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class Vi6Game extends CollectionPhase<Player> implements ForwardingAudience{
	
	private World world;
	
	private Scoreboard scoreboard;
	
	private Team scoreboardGuard;
	
	private Team scoreboardThief;
	
	private final GuardTeam guards = new GuardTeam();
	
	private final ThiefTeam thiefs = new ThiefTeam();
	
	private final CollectionPhase<Player> lobbyPhase = new Vi6PhaseLobby(this::onLobbyPhaseComplete, this::cancel, this::all);
	
	private final CollectionPhase<Player> preparationPhase = new Vi6PhasePreparation(this::onPreparationPhaseComplete, this::cancel, this::all);
	
	private CollectionPhase<Player> currentPhase = lobbyPhase;
	
	private String mapName;
	
	private Vi6Map map;
	
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
		world = Bukkit.getWorlds().get(0);
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
		var maps = NekotineCore.MODULES.get(MapModule.class).getMapFinder().list();
		if (maps.size() < 1) {
			throw new IllegalStateException("Aucune map n'est disponible");
		}
		mapName = maps.get(0).getName();
		// TODO Setup majordom
		currentPhase.setup();
	}

	@Override
	public void globalTearDown() {
		try {
			scoreboardGuard.unregister();
			scoreboardThief.unregister();
		}catch(IllegalStateException e) {
			Vi6Main.LOGGER.log(Level.WARNING, "Impossible de supprimer les team, le scoreboard n'existe plus (plugin onDisable?)", e);
		}
		scoreboard = null;
	}

	@Override
	public void itemSetup(Player item) {
		var wrapper = new PlayerWrapper(item);
		NekotineCore.MODULES.get(WrappingModule.class).putWrapper(item, wrapper);
		wrapper.setPreGameSnapshot(new PlayerStatusSnaphot().snapshot(item));
		EntityUtil.clearPotionEffects(item);
		EntityUtil.defaultAllAttributes(item);
		item.getInventory().clear();
		currentPhase.itemSetup(item);
	}

	@Override
	public void itemTearDown(Player item) {
		currentPhase.itemTearDown(item);
		var wrappingModule = NekotineCore.MODULES.get(WrappingModule.class);
		var wrapper = wrappingModule.getWrapper(item, PlayerWrapper.class);
		wrappingModule.removeWrapper(item, PlayerWrapper.class);
		wrapper.getPreGameSnapshot().patch(item);
	}
	
	public Stream<Player> all(){
		return Stream.concat(guards.stream(), thiefs.stream());
	}
	
	@Override
	public @NotNull Iterable<? extends Audience> audiences() {
		return guards.audiences();
	}
	
	public void addPlayer(Player player) {
		if (isRunning) {
			fr.nekotine.core.game.team.Team team = guards;
			if (guards.size() > thiefs.size()) {
				team = thiefs;
			}
			team.add(player);
		}
	}
	
	public void removePlayer(Player player) {
		if (isRunning) {
			guards.remove(player);
			thiefs.remove(player);
		}
	}
	
	public @Nullable Scoreboard getScoreboard() {
		return scoreboard;
	}
	
	public @Nullable Vi6Map getMap() {
		return map;
	}
	
	public void setMap(@Nullable Vi6Map map) {
		this.map = map;
	}
	
	public String getMapName() {
		return mapName;
	}
	
	public void setMapName(String name) {
		mapName = name;
	}
	
	public GuardTeam getGuards() {
		return guards;
	}
	
	public ThiefTeam getThiefs() {
		return thiefs;
	}
	
	public World getWorld() {
		return world;
	}
	
	public CollectionPhase<Player> getCurrentPhase(){
		return currentPhase;
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
		currentPhase = preparationPhase;
		currentPhase.setup();
	}
	
	private void onPreparationPhaseComplete() {
		
	}
	
}
