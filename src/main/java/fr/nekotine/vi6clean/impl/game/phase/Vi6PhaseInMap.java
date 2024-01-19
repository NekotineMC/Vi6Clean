package fr.nekotine.vi6clean.impl.game.phase;

import java.util.LinkedList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.RenderType;
import org.bukkit.scoreboard.Team;

import com.destroystokyo.paper.event.entity.EntityTeleportEndGatewayEvent;

import fr.nekotine.core.constant.DayTime;
import fr.nekotine.core.game.phase.CollectionPhase;
import fr.nekotine.core.game.phase.IPhaseMachine;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.map.MapModule;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.state.ItemState;
import fr.nekotine.core.state.ItemWrappingState;
import fr.nekotine.core.state.PotionEffectState;
import fr.nekotine.core.state.RegisteredEventListenerState;
import fr.nekotine.core.state.State;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.AssertUtil;
import fr.nekotine.core.util.DebugUtil;
import fr.nekotine.core.util.collection.ObservableCollection;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.InMapState;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.majordom.Majordom;
import fr.nekotine.vi6clean.impl.map.Vi6Map;
import fr.nekotine.vi6clean.impl.map.artefact.Artefact;
import fr.nekotine.vi6clean.impl.map.koth.Koth;
import fr.nekotine.vi6clean.impl.tool.ToolHandlerContainer;
import fr.nekotine.vi6clean.impl.wrapper.InMapPhasePlayerWrapper;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class Vi6PhaseInMap extends CollectionPhase<Vi6PhaseGlobal,Player> implements Listener{
	
	private Vi6Map map;
	
	private Objective scoreboardArtefactListObjective;
	private final Team stolenTeam = Ioc.resolve(Vi6Game.class).getScoreboard().registerNewTeam("stolen");
	private final Team unknownTeam = Ioc.resolve(Vi6Game.class).getScoreboard().registerNewTeam("unknown");
	private final Team safeTeam = Ioc.resolve(Vi6Game.class).getScoreboard().registerNewTeam("safe");
	private int unfoundStolenArtefacts = 0;
	
	public Vi6PhaseInMap(IPhaseMachine machine) {
		super(machine);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
		
		var scoreboard = Ioc.resolve(Vi6Game.class).getScoreboard();
		scoreboardArtefactListObjective = scoreboard.getObjective("artefactListing");
		if (scoreboardArtefactListObjective == null) {
			scoreboardArtefactListObjective = scoreboard.registerNewObjective("artefactListing",
					Criteria.DUMMY,
					Component.text("Check-list", NamedTextColor.GOLD).decorate(TextDecoration.UNDERLINED),
					RenderType.INTEGER);
		}
		scoreboardArtefactListObjective.setDisplaySlot(DisplaySlot.SIDEBAR_TEAM_BLUE);
		stolenTeam.color(NamedTextColor.RED);
		unknownTeam.color(NamedTextColor.YELLOW);
		safeTeam.color(NamedTextColor.GREEN);
	}

	@Override
	public Class<Vi6PhaseGlobal> getParentType() {
		return Vi6PhaseGlobal.class;
	}

	@Override
	public ObservableCollection<Player> getItemCollection() {
		return Ioc.resolve(Vi6Game.class).getPlayerList();
	}
	
	@Override
	public void globalSetup(Object inputData) {
		var game = Ioc.resolve(Vi6Game.class);
		var world = game.getWorld();
		world.setTime(DayTime.MIDNIGHT);
		var mapName = game.getMapName();
		if (mapName == null) {
			var maps = Ioc.resolve(MapModule.class).getMapFinder().list();
			if (maps.size() <= 0) {
				throw new IllegalStateException("Aucune map n'est disponible");
			}
			mapName = maps.get(0).getName();
		}
		map = Ioc.resolve(MapModule.class).getMapFinder().findByName(Vi6Map.class, mapName).loadConfig();
		AssertUtil.nonNull(map, "La map n'a pas pus etre chargee");
		var artefacts = map.getArtefacts();
		for (var artefact : artefacts.values()) {
			artefact.setup(world);
			objectiveSafe(artefact);
		}
		var entrances = map.getEntrances();
		for (var entranceName : entrances.keySet()) {
			var entrance = entrances.get(entranceName);
			entrance.setName(entranceName);
			if (game.isDebug()) {
				DebugUtil.debugBoundingBox(world, entrance.getBlockingBox(), Bukkit.createBlockData(Material.ORANGE_STAINED_GLASS));
				DebugUtil.debugBoundingBox(world, entrance.getEntranceTriggerBox(), Bukkit.createBlockData(Material.GREEN_STAINED_GLASS));
			}
		}
		if (game.isDebug()) {
			for (var exit : map.getExits().values()) {
				DebugUtil.debugBoundingBox(world, exit, Bukkit.createBlockData(Material.RED_STAINED_GLASS));
			}
			for (var roomCaptor : map.getRoomCaptors().values()) {
				DebugUtil.debugBoundingBox(world, roomCaptor.getTriggerBox(), Bukkit.createBlockData(Material.YELLOW_STAINED_GLASS));
			}
		}
		for (var tool : Ioc.resolve(ToolHandlerContainer.class).getHandlers()) {
			tool.startHandling();
		}
		Ioc.resolve(Majordom.class).enable();
	}

	@Override
	public void globalTearDown() {
		Ioc.resolve(Majordom.class).revertThenDisable();
		var game = Ioc.resolve(Vi6Game.class);
		game.getWorld().setTime(DayTime.NOON);
		DebugUtil.clearDebugEntities();
		for (var artefact : map.getArtefacts().values()) {
			artefact.clean();
		}
		for (var tool : Ioc.resolve(ToolHandlerContainer.class).getHandlers()) {
			tool.stopHandling();
			tool.removeAll();
		}
		for(var koth : map.getKoths().values()) {
			koth.clean();
		}
		map = null;
		
		stolenTeam.unregister();
		unknownTeam.unregister();
		safeTeam.unregister();
		scoreboardArtefactListObjective.unregister();
		scoreboardArtefactListObjective = null;
	}
	
	@Override
	public void itemSetup(Player item) {
		var wrappingModule = Ioc.resolve(WrappingModule.class);
		var wrap = wrappingModule.getWrapper(item, InMapPhasePlayerWrapper.class);
		item.setGameMode(GameMode.ADVENTURE);
		if (!wrap.getParentWrapper().isThief()) {
			wrap.setCanLeaveMap(false);
			wrap.updateMapLeaveBlocker();
		}
	}

	@Override
	public void itemTearDown(Player item) {
		var wrappingModule = Ioc.resolve(WrappingModule.class);
		var wrap = wrappingModule.getWrapper(item, InMapPhasePlayerWrapper.class);
		if (!wrap.getParentWrapper().isThief()) {
			wrap.setCanLeaveMap(true);
			wrap.updateMapLeaveBlocker();
		}
	}
	
	@Override
	protected List<State> makeAppliedStates() {
		var list = new LinkedList<State>();
		list.add(new RegisteredEventListenerState(this));
		return list;
	}
	
	@Override
	protected List<ItemState<Player>> makeAppliedItemStates() {
		var list = new LinkedList<ItemState<Player>>();
		list.add(new ItemWrappingState<>(InMapPhasePlayerWrapper::new));
		list.add(new PotionEffectState<Player>(new PotionEffect(PotionEffectType.SATURATION, -1, 0, false, false, false)));
		return list;
	}
	
	public Vi6Map getMap() {
		return map;
	}
	
	// Event handlers
	
	@EventHandler
	private void onPlayerMove(PlayerMoveEvent evt) {
		var player = evt.getPlayer();
		var optWrapper = Ioc.resolve(WrappingModule.class).getWrapperOptional(player, InMapPhasePlayerWrapper.class);
		if (optWrapper.isEmpty()) {
			return;
		}
		var wrapper = optWrapper.get();
		var destVect = evt.getTo().toVector();
		if (wrapper.isInside()) {
			if (!wrapper.canLeaveMap()) {
				if (map.getEntrances().values().stream().map(e -> e.getBlockingBox()).anyMatch(bb -> bb.contains(destVect))){
					evt.setCancelled(true);
					return;
				}
				if (map.getExits().values().stream().map(e -> e).anyMatch(bb -> bb.contains(destVect))){
					evt.setCancelled(true);
					return;
				}
			}else {
				var exit = map.getExits().values().stream().filter(e -> e.contains(destVect)).findFirst();
				if (exit.isPresent()) {
					wrapper.thiefLeaveMap();
					return;
				}
			}
			map.getArtefacts().values().forEach(artefact -> {
				var zone = artefact.getInsideCaptureZone();
				if (artefact.getBoundingBox().contains(destVect)) {
					zone.add(player);
					wrapper.showArtefactBar();
				}else if(artefact.getInsideCaptureZone().contains(player)){
					zone.remove(player);
					wrapper.showDefaultBar();
				}
			});
			map.getKoths().values().stream().forEach(koth -> {
				var zone = koth.getInsideCaptureZone();
				if (koth.getBoundingBox().contains(destVect)) {
					zone.add(player);
				}else {
					zone.remove(player);
				}
			});
			map.getRoomCaptors().values().stream().forEach(roomcaptor -> {
				if (roomcaptor.getTriggerBox().contains(destVect)) {
					var r = roomcaptor.getRoom();
					if (!r.contentEquals(wrapper.getRoom())) {
						wrapper.setRoom(r);
						player.sendActionBar(Component.text("Salle: "+r,NamedTextColor.WHITE));
					}
					
				}
			});
		}else if (wrapper.getState() == InMapState.ENTERING){
			var entrance = map.getEntrances().values().stream()
					.filter(e -> e.getEntranceTriggerBox().contains(destVect))
					.findFirst();
			if (entrance.isPresent()) {
				wrapper.thiefEnterInside(entrance.get());
			}
		}
	}
	
	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		map.getArtefacts().values().stream().forEach(Artefact::tick);
		map.getKoths().values().stream().forEach(Koth::tick);
	}

	@EventHandler
	private void onPlayerDeath(PlayerDeathEvent evt) {
		var game = Ioc.resolve(Vi6Game.class);
		var player = evt.getPlayer();
		if (game.getThiefs().contains(evt.getPlayer())){
			var optWrap = Ioc.resolve(WrappingModule.class).getWrapperOptional(player, InMapPhasePlayerWrapper.class);
			if (optWrap.isPresent()) {
				optWrap.get().thiefLeaveMap(true);
			}
		}
	}
	
	//Map protection
	@EventHandler
	public void itemFrameBreak(HangingBreakEvent e) {
		e.setCancelled(true);
	}
	@EventHandler
	public void itemFrameChange(PlayerItemFrameChangeEvent e) {
		e.setCancelled(true);
	}
	@EventHandler
	public void entityDamageEvent(EntityDamageEvent e) {
		if(e.getEntity() instanceof ArmorStand || e.getEntity() instanceof Minecart) 
			e.setCancelled(true);
	}
	
	@EventHandler
	public void interactEvent(PlayerInteractEvent e) {
		if(e.getAction()==Action.RIGHT_CLICK_BLOCK && e.getClickedBlock().getType()==Material.RESPAWN_ANCHOR) 
			e.setCancelled(true);
		
	}
	@EventHandler
	public void endGateway(EntityTeleportEndGatewayEvent e) {
		if(!(e.getEntity() instanceof Player)) 
			e.setCancelled(true);	
	}
	
	@EventHandler
	public void vehicleDestroyEvent(VehicleDestroyEvent e) {
		e.setCancelled(true);
	}
	
	//
	
	public Objective getSidebarObjective() {
		return scoreboardArtefactListObjective;
	}
	public void objectiveStolen(Artefact artefact) {
		var name = artefact.getName();
		if(stolenTeam.hasEntry(name)) return;
		stolenTeam.addEntry(name);
		scoreboardArtefactListObjective.getScore(name).setScore(2);
		if(--unfoundStolenArtefacts == 0) {
			unknownToSafe();
		}
	}
	public void objectiveSafe(Artefact artefact) {
		var name = artefact.getName();
		if(safeTeam.hasEntry(name)) return;
		safeTeam.addEntry(name);
		scoreboardArtefactListObjective.getScore(name).setScore(0);
	}
	public void objectiveUnknown(Artefact artefact) {
		var name = artefact.getName();
		if(unknownTeam.hasEntry(name)) return;
		unknownTeam.addEntry(name);
		scoreboardArtefactListObjective.getScore(name).setScore(1);
	}
	public void safeToUnknown() {
		unfoundStolenArtefacts++;
		for (var artefact : map.getArtefacts().values()) {
			if(safeTeam.hasEntry(artefact.getName())) {
				objectiveUnknown(artefact);
			}
		}
	}
	public void unknownToSafe() {
		for (var artefact : map.getArtefacts().values()) {
			if(unknownTeam.hasEntry(artefact.getName())) {
				objectiveSafe(artefact);
			}
		}
	}
}
