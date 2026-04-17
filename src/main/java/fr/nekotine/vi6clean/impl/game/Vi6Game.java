package fr.nekotine.vi6clean.impl.game;

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.scoreboard.Team.Option;
import org.bukkit.scoreboard.Team.OptionStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import fr.nekotine.core.game.phase.IPhaseMachine;
import fr.nekotine.core.game.phase.PhaseMachine;
import fr.nekotine.core.glow.EntityGlowModule;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.logging.NekotineLogger;
import fr.nekotine.core.map.IMapModule;
import fr.nekotine.core.map.MapModule;
import fr.nekotine.core.util.AssertUtil;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.util.collection.ObservableCollection;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6Keys;
import fr.nekotine.vi6clean.constant.Vi6Team;
import fr.nekotine.vi6clean.impl.game.phase.Vi6PhaseInfiltration;
import fr.nekotine.vi6clean.impl.game.phase.Vi6PhaseLobby;
import fr.nekotine.vi6clean.impl.game.phase.Vi6PhasePreparation;
import fr.nekotine.vi6clean.impl.game.team.GuardTeam;
import fr.nekotine.vi6clean.impl.game.team.ThiefTeam;
import fr.nekotine.vi6clean.impl.map.Vi6Map;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.kyori.adventure.text.minimessage.MiniMessage;

public class Vi6Game implements ForwardingAudience, AutoCloseable, Listener {

	private final ComponentLogger logger = NekotineLogger.make();

	private String mapName;

	private World world = Bukkit.getWorlds().getFirst();

	private final Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();

	private final Team scoreboardGuard = scoreboard.registerNewTeam("guard");

	private final Team scoreboardThief = scoreboard.registerNewTeam("thief");

	private final ObservableCollection<Player> players = ObservableCollection.wrap(new LinkedList<>());

	private final GuardTeam guards = new GuardTeam();

	private final ThiefTeam thiefs = new ThiefTeam();

	private IPhaseMachine phaseMachine = new PhaseMachine();

	private boolean debug = false;

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
		Set.of(scoreboardGuard, scoreboardThief).forEach(team -> {
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
		var mm = Ioc.resolve(MapModule.class);
		if (mapName == null) {
			var maps = mm.listMaps();
			if (maps.size() <= 0) {
				throw new IllegalStateException("Aucune map n'est disponible");
			}
			setMapName(maps.stream().sorted((mapA, mapB) -> {
				System.out.println(mapA.getName());
				System.out.println(mapB.getName());
				return mapA.getName().equals("SolarIndustries") ? -1 : 1;
			}).findFirst().get().getName());
		}
	}

	public final void start() {
		// Launch game
		try {
			phaseMachine.goTo(Vi6PhaseLobby.class, null);
		} catch (Exception e) {
			logger.error("Une erreur est survenue lors du chargement de la game", e);
		}
		EventUtil.register(this);
	}

	@Override
	public void close() {
		EventUtil.unregister(this);
		try {
			phaseMachine.end();
			scoreboardGuard.unregister();
			scoreboardThief.unregister();
		} catch (Exception e) {
			logger.error("Un erreur est survenur lors du déchargement de la game", e);
		}
	}

	public ObservableCollection<Player> getPlayerList() {
		return players;
	}

	@Override
	public @NotNull Iterable<? extends Audience> audiences() {
		return players;
	}

	public void addPlayer(Player player) {
		if (guards.size() > thiefs.size()) {
			addPlayerInThiefs(player);
		} else {
			addPlayerInGuards(player);
		}
		var loc = world.getSpawnLocation();
		player.teleport(loc);
	}

	public void addPlayerInGuards(Player player) {
		if (!players.contains(player)) {
			players.add(player);
		}
		if (thiefs.contains(player)) {
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
		if (guards.contains(player)) {
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
		var mapModule = Ioc.resolve(IMapModule.class);
		var metadata = mapModule.getMapMetadata(mapName);
		var map = mapModule.getContent(metadata, Vi6Map.class);
		AssertUtil.nonNull(map, String.format("La map %s n'a pas pus etre chargee", name));
		var w = Bukkit.getWorlds().stream().filter(wo -> wo.getName().equals(map.getWorldName())).findFirst();
		if (w.isEmpty()) {
			throw new RuntimeException(
					"Le monde " + map.getWorldName() + " correspondant à la carte " + mapName + " n'existe pas");
		}
		world = w.get();
		var loc = world.getSpawnLocation();
		for (var p : players) {
			p.teleport(loc);
		}
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

	public void setWorld(World world) {
		this.world = world;
	}

	public IPhaseMachine getPhaseMachine() {
		return phaseMachine;
	}

	private void setupGuard(Player player) {
		scoreboardGuard.addPlayer(player);
		var wrap = Ioc.resolve(WrappingModule.class).getWrapper(player, PlayerWrapper.class);
		wrap.setTeam(Vi6Team.GUARD);
		var glowModule = Ioc.resolve(EntityGlowModule.class);
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
		var wrap = Ioc.resolve(WrappingModule.class).getWrapperOptional(player, PlayerWrapper.class);
		if (wrap.isPresent()) {
			wrap.get().setTeam(Vi6Team.SPECTATOR);
		}
		var glowModule = Ioc.resolve(EntityGlowModule.class);
		for (var guard : guards) {
			glowModule.unglowEntityFor(guard, player);
			glowModule.unglowEntityFor(player, guard);
		}
	}

	private void setupThief(Player player) {
		scoreboardThief.addPlayer(player);
		var wrap = Ioc.resolve(WrappingModule.class).getWrapper(player, PlayerWrapper.class);
		wrap.setTeam(Vi6Team.THIEF);
		var glowModule = Ioc.resolve(EntityGlowModule.class);
		for (var thief : thiefs) {
			glowModule.glowEntityFor(thief, player);
			glowModule.glowEntityFor(player, thief);
		}
	}

	private void tearDownThief(Player player) {
		scoreboardThief.removePlayer(player);
		var wrap = Ioc.resolve(WrappingModule.class).getWrapperOptional(player, PlayerWrapper.class);
		if (wrap.isPresent()) {
			wrap.get().setTeam(Vi6Team.SPECTATOR);
		}
		var glowModule = Ioc.resolve(EntityGlowModule.class);
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

	@EventHandler
	private void onPlayerChangeItem(PlayerItemHeldEvent evt) {
		var previous = evt.getPlayer().getInventory().getItem(evt.getPreviousSlot());
		var next = evt.getPlayer().getInventory().getItem(evt.getNewSlot());
		if (previous != null && previous.getType() == Material.LEATHER_HORSE_ARMOR) {
			Ioc.resolve(Vi6Game.class).getGuards()
					.playSound(Sound.sound(Key.key("block.note_block.bell"), Sound.Source.VOICE, 1.0f, 1.5f));
		}
		if (next != null && next.getType() == Material.LEATHER_HORSE_ARMOR) {
			Ioc.resolve(Vi6Game.class).getGuards()
					.playSound(Sound.sound(Key.key("block.note_block.bell"), Sound.Source.VOICE, 1.0f, 2f));
		}
	}

	@EventHandler
	private void onDialogCustomClick(PlayerCustomClickEvent evt) {
		if (!(evt.getCommonConnection() instanceof PlayerGameConnection conn)) {
			return;
		}
		var player = conn.getPlayer();
		switch (evt.getIdentifier().asString()) {
			case Vi6Keys.DIALOG_GAME_SETTINGS :
				// open game settings dialog
				displayGameSettingsDialog(player);
				break;
			default :
				break;
		}
	}

	private void displayGameSettingsDialog(Player player) {
		var mapModule = Ioc.resolve(MapModule.class);
		var maps = mapModule.listMaps();
		var dialog = Dialog.create(builder -> builder.empty().base(DialogBase
				.builder(MiniMessage.miniMessage()
						.deserialize("<red>V<dark_aqua>oleur <red>I<dark_aqua>ndustriel <red>6"))
				.canCloseWithEscape(true).pause(false).inputs(List.of(
						DialogInput
								.singleOption("map", Component.text("Carte"),
										maps.stream()
												.map(mapMetadata -> SingleOptionDialogInput.OptionEntry.create(
														mapMetadata.getName(), mapMetadata.getDisplayName(),
														mapMetadata.getName().equals(this.getMapName())))
												.toList())
								.build(),
						DialogInput
								.bool("debug", MiniMessage.miniMessage().deserialize("Activer le mode debug <yellow>⚠"))
								.initial(this.isDebug()).build()))
				.build())
				.type(DialogType.confirmation(ActionButton.builder(Component.text("Enregistrer", NamedTextColor.GREEN))
						.action(DialogAction.customClick((response, audiance) -> {
							this.setDebug(response.getBoolean("debug"));
							this.setMapName(response.getText("map"));
							var mapMetadata = Ioc.resolve(MapModule.class).getMapMetadata(response.getText("map"));
							audiance.sendMessage(
									Component.text("Carte selectionnée: ").append(mapMetadata.getDisplayName()));
							audiance.sendMessage(Component.text("Mode débug: ")
									.append(Component.text(response.getBoolean("debug") ? "Activé" : "Désactivé")));
						}, ClickCallback.Options.builder().lifetime(Duration.ofMinutes(5)).build())).build(),
						ActionButton.builder(Component.text("Annuler", NamedTextColor.RED)).build())));
		player.showDialog(dialog);
	}
}
