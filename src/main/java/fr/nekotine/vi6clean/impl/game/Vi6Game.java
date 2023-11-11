package fr.nekotine.vi6clean.impl.game;

import java.util.LinkedList;
import java.util.Set;

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
import fr.nekotine.core.game.phase.IPhaseMachine;
import fr.nekotine.core.game.phase.PhaseMachine;
import fr.nekotine.core.glow.EntityGlowModule;
import fr.nekotine.core.util.collection.ObservableCollection;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6Team;
import fr.nekotine.vi6clean.impl.game.phase.Vi6PhaseInfiltration;
import fr.nekotine.vi6clean.impl.game.phase.Vi6PhaseLobby;
import fr.nekotine.vi6clean.impl.game.phase.Vi6PhasePreparation;
import fr.nekotine.vi6clean.impl.game.team.GuardTeam;
import fr.nekotine.vi6clean.impl.game.team.ThiefTeam;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class Vi6Game implements ForwardingAudience, AutoCloseable{
	
	private final World world = Bukkit.getWorlds().get(0);
	
	private final Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
	
	private final Team scoreboardGuard = scoreboard.registerNewTeam("guard");
	
	private final Team scoreboardThief = scoreboard.registerNewTeam("thief");
	
	private final ObservableCollection<Player> players = ObservableCollection.wrap(new LinkedList<>());
	
	private final GuardTeam guards = new GuardTeam();
	
	private final ThiefTeam thiefs = new ThiefTeam();
	
	private IPhaseMachine phaseMachine = new PhaseMachine();
	
	private String mapName;
	
	private boolean debug = true;
	
	public Vi6Game() {
		
		// add/remove players from scoreboard team
		
		guards.addAdditionCallback(this::setupGuard);
		guards.addSuppressionCallback(this::tearDownPotentialGuard);
		thiefs.addAdditionCallback(this::setupThief);
		thiefs.addSuppressionCallback(this::tearDownPotentialThief);
		
		// Setup scoreboard teams
		
		scoreboardGuard.color(NamedTextColor.BLUE);
		scoreboardThief.color(NamedTextColor.RED);
		scoreboardGuard.displayName(Component.text("Garde", NamedTextColor.BLUE));
		scoreboardThief.displayName(Component.text("Voleur", NamedTextColor.RED));
		scoreboardGuard.prefix(Component.text("[Garde] ", NamedTextColor.BLUE));
		scoreboardThief.prefix(Component.text("[Voleur] ", NamedTextColor.RED));
		Set.of(scoreboardGuard, scoreboardThief).forEach(team ->{
			team.setAllowFriendlyFire(false);
			team.setCanSeeFriendlyInvisibles(true);
			team.setOption(Option.NAME_TAG_VISIBILITY, OptionStatus.FOR_OTHER_TEAMS);
			team.setOption(Option.COLLISION_RULE, OptionStatus.NEVER);
		});
		
		//
		
		phaseMachine.registerPhase(Vi6PhaseLobby.class, Vi6PhaseLobby::new);
		phaseMachine.registerPhase(Vi6PhasePreparation.class, Vi6PhasePreparation::new);
		phaseMachine.registerPhase(Vi6PhaseInfiltration.class, Vi6PhaseInfiltration::new);
		phaseMachine.setLooping(true);
		
		// TODO Setup majordom to Vi6Main.IOC
	}

	@Override
	public void close() throws Exception {
		phaseMachine.end();
		scoreboardGuard.unregister();
		scoreboardThief.unregister();
	}
	
	public ObservableCollection<Player> getPlayerList(){
		return players;
	}
	
	@Override
	public @NotNull Iterable<? extends Audience> audiences() {
		return players;
	}
	
	public void addPlayer(Player player) {
		if (guards.size() > thiefs.size()) {
			addPlayerInThiefs(player);
		}else {
			addPlayerInGuards(player);
		}
	}
	
	public void addPlayerInGuards(Player player) {
		if (!players.contains(player)) {
			players.add(player);
		}
		if(thiefs.contains(player)) {
			thiefs.remove(player);
		}
		if (!guards.contains(player)) {
			guards.add(player);
		}
	}
	
	public void addPlayerInThiefs(Player player) {
		if (!players.contains(player)) {
			players.add(player);
		}
		if(guards.contains(player)) {
			guards.remove(player);
		}
		if (!thiefs.contains(player)) {
			thiefs.add(player);
		}
	}
	
	public void removePlayer(Player player) {
		guards.remove(player);
		thiefs.remove(player);
		players.remove(player);
	}
	
	public @Nullable Scoreboard getScoreboard() {
		return scoreboard;
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
	
	public IPhaseMachine getPhaseMachine() {
		return phaseMachine;
	}
	
	private void setupGuard(Player player) {
		scoreboardGuard.addPlayer(player);
		var wrap = NekotineCore.MODULES.get(WrappingModule.class).getWrapper(player, PlayerWrapper.class);
		wrap.setTeam(Vi6Team.GUARD);
		var glowModule = NekotineCore.MODULES.get(EntityGlowModule.class);
		for (var guard : guards) {
			glowModule.glowEntityFor(guard, player);
			glowModule.glowEntityFor(player, guard);
		}
	}
	
	private void tearDownPotentialGuard(Object o) {
		if (o instanceof Player g) {
			tearDownGuard(g);
		}
	}
	
	private void tearDownGuard(Player player) {
		scoreboardGuard.removePlayer(player);
		var wrap = NekotineCore.MODULES.get(WrappingModule.class).getWrapperOptional(player, PlayerWrapper.class);
		if (wrap.isPresent()) {
			wrap.get().setTeam(Vi6Team.SPECTATOR);
		}
		var glowModule = NekotineCore.MODULES.get(EntityGlowModule.class);
		for (var guard : guards) {
			glowModule.unglowEntityFor(guard, player);
			glowModule.unglowEntityFor(player, guard);
		}
	}
	
	private void setupThief(Player player) {
		scoreboardThief.addPlayer(player);
		var wrap = NekotineCore.MODULES.get(WrappingModule.class).getWrapper(player, PlayerWrapper.class);
		wrap.setTeam(Vi6Team.THIEF);
		var glowModule = NekotineCore.MODULES.get(EntityGlowModule.class);
		for (var thief : thiefs) {
			glowModule.glowEntityFor(thief, player);
			glowModule.glowEntityFor(player, thief);
		}
	}
	
	private void tearDownThief(Player player) {
		scoreboardThief.removePlayer(player);
		var wrap = NekotineCore.MODULES.get(WrappingModule.class).getWrapperOptional(player, PlayerWrapper.class);
		if (wrap.isPresent()) {
			wrap.get().setTeam(Vi6Team.SPECTATOR);
		}
		var glowModule = NekotineCore.MODULES.get(EntityGlowModule.class);
		for (var thief : thiefs) {
			glowModule.unglowEntityFor(thief, player);
			glowModule.unglowEntityFor(player, thief);
		}
	}
	
	private void tearDownPotentialThief(Object o) {
		if (o instanceof Player t) {
			tearDownThief(t);
		}
	}

	public boolean isDebug() {
		return debug;
	}

	public void setDebug(boolean debug) {
		this.debug = debug;
	}
	
}
