package fr.nekotine.vi6clean.impl.game.phase;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
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
import org.bukkit.plugin.java.JavaPlugin;
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
import fr.nekotine.core.logging.NekotineLogger;
import fr.nekotine.core.map.IMapModule;
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
import fr.nekotine.vi6clean.constant.Vi6Team;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.majordom.Majordom;
import fr.nekotine.vi6clean.impl.map.Vi6Map;
import fr.nekotine.vi6clean.impl.map.artefact.Artefact;
import fr.nekotine.vi6clean.impl.map.koth.AbstractKothEffect;
import fr.nekotine.vi6clean.impl.map.koth.Koth;
import fr.nekotine.vi6clean.impl.map.koth.effect.EmpKothEffect;
import fr.nekotine.vi6clean.impl.map.koth.effect.LightKothEffect;
import fr.nekotine.vi6clean.impl.tool.ToolHandlerContainer;
import fr.nekotine.vi6clean.impl.wrapper.InMapPhasePlayerWrapper;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class Vi6PhaseInMap extends CollectionPhase<Vi6PhaseGlobal,Player> implements Listener{
	
	private Logger logger = new NekotineLogger(getClass());
	
	private Vi6Map map;
	
	private Objective guardScoreboard;
	//private final Team guardCountTeam = Ioc.resolve(Vi6Game.class).getScoreboard().registerNewTeam("guardCount");
	private final Team guardEscapedTeam = Ioc.resolve(Vi6Game.class).getScoreboard().registerNewTeam("guardEscaped");
	private final Team guardStolenTeam = Ioc.resolve(Vi6Game.class).getScoreboard().registerNewTeam("guardStolen");
	private final Team guardUnknownTeam = Ioc.resolve(Vi6Game.class).getScoreboard().registerNewTeam("guardUnknown");
	private final Team guardSafeTeam = Ioc.resolve(Vi6Game.class).getScoreboard().registerNewTeam("guardSafe");
	//private final String countString = "A trouver: ";
	private final String guardObjectiveName = "guardArtefactListing";
	private int unfoundStolenArtefacts = 0;
	
	private Objective thiefScoreboard;
	private final String thiefObjectiveName = "thiefArtefactListing";
	
	public Vi6PhaseInMap(IPhaseMachine machine) {
		super(machine);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
		
		var scoreboard = Ioc.resolve(Vi6Game.class).getScoreboard();
		guardScoreboard = scoreboard.getObjective(guardObjectiveName);
		if (guardScoreboard == null) {
			guardScoreboard = scoreboard.registerNewObjective(guardObjectiveName,
					Criteria.DUMMY,
					Component.text("Check-list", NamedTextColor.GOLD).decorate(TextDecoration.UNDERLINED),
					RenderType.INTEGER);
		}
		guardScoreboard.setDisplaySlot(DisplaySlot.SIDEBAR_TEAM_BLUE);
		//guardCountTeam.color(NamedTextColor.AQUA);
		guardEscapedTeam.color(NamedTextColor.DARK_RED);
		guardStolenTeam.color(NamedTextColor.RED);
		guardUnknownTeam.color(NamedTextColor.YELLOW);
		guardSafeTeam.color(NamedTextColor.GREEN);
		//guardCountTeam.addEntry(countString);
		//guardUpdateCount();
		
		thiefScoreboard = scoreboard.getObjective(thiefObjectiveName);
		if (thiefScoreboard == null) {
			thiefScoreboard = scoreboard.registerNewObjective(thiefObjectiveName,
					Criteria.DUMMY,
					Component.text("Check-list", NamedTextColor.GOLD).decorate(TextDecoration.UNDERLINED),
					RenderType.INTEGER);
		}
		thiefScoreboard.setDisplaySlot(DisplaySlot.SIDEBAR_TEAM_RED);
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
		// LOAD MAP
		var mapModule = Ioc.resolve(IMapModule.class);
		var mapName = game.getMapName();
		if (mapName == null) {
			var maps = mapModule.listMaps();
			if (maps.size() <= 0) {
				throw new IllegalStateException("Aucune map n'est disponible");
			}
			mapName = maps.stream().findAny().get().getName();
		}
		var metadata = mapModule.getMapMetadata(mapName);
		map = mapModule.getContent(metadata, Vi6Map.class);
		AssertUtil.nonNull(map, "La map n'a pas pus etre chargee");
		// LOAD MAP END
		var artefacts = map.getArtefacts();
		for (var artefact : artefacts.values()) {
			artefact.setup(world);
			guardObjectiveSafe(artefact);
			thiefObjectiveSafe(artefact);
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
		// KOTH
		List<AbstractKothEffect> kothEffects = new LinkedList<>(Arrays.asList(new EmpKothEffect(), new LightKothEffect()));
		List<Koth> koths = new LinkedList<>(map.getKoths().values());
		var limit = Ioc.resolve(JavaPlugin.class).getConfig().getInt("koth.limit", 0);
		Collections.shuffle(kothEffects);
		Collections.shuffle(koths);
		var nbKothSpawned = 0;
		var random = new Random();
		var count = 0;
		while(count < limit && kothEffects.size() > 0 && koths.size() > 0) {
			count++;
			var effect = kothEffects.remove(0);
			var probability = effect.getProbability();
			if(random.nextDouble() > probability) {
				continue;
			}
			nbKothSpawned++;
			var koth = koths.remove(0);
			koth.setup(effect, world);
			logger.log(Level.INFO, 
					String.format("Spawning koth at %s (%s) typed %s",
							koth.getName(),
							koth.getDisplayLocation(),
							effect.getCode()
							));
		}
		game.getGuards().sendMessage(
				Component.text("Koth", NamedTextColor.DARK_PURPLE)
				.append(Component.text(" >> ", NamedTextColor.WHITE))
				.append(Component.text(nbKothSpawned,NamedTextColor.AQUA))
				.append(Component.text(" zones à capturer sont apparues !", NamedTextColor.GOLD)));
		// WEATHER
		var weatherRand = random.nextFloat();
		if (weatherRand < 0.2) {
			world.setStorm(true);
			world.setWeatherDuration(Integer.MAX_VALUE);
			if (weatherRand < 0.1) {
				world.setThundering(true);
				world.setThunderDuration(Integer.MAX_VALUE);
			}else {
				
			}
		}
		Ioc.resolve(Majordom.class).enable();
	}

	@Override
	public void globalTearDown() {
		Ioc.resolve(Majordom.class).revertThenDisable();
		var game = Ioc.resolve(Vi6Game.class);
		var world = game.getWorld();
		world.setTime(DayTime.NOON);
		world.setStorm(false);
		world.setWeatherDuration(Integer.MAX_VALUE);
		world.setThundering(false);
		world.setThunderDuration(Integer.MAX_VALUE);
		
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
		
		//guardCountTeam.unregister();
		guardEscapedTeam.unregister();
		guardStolenTeam.unregister();
		guardUnknownTeam.unregister();
		guardSafeTeam.unregister();
		guardScoreboard.unregister();

		thiefScoreboard.unregister();
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
		wrap.tearDown();
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
	
	@EventHandler
	public void handleInBushDamage(PlayerInteractEvent evt) {
		if(evt.getAction() != Action.LEFT_CLICK_BLOCK) return;
		var player = evt.getPlayer();
		var eyeLoc = player.getEyeLocation();
		var result = player.getWorld().rayTrace(
				eyeLoc, 
				eyeLoc.getDirection(), 
				3, 
				FluidCollisionMode.NEVER, 
				true, 
				1, 
				e -> player.canSee(e));
		var hitE = result.getHitEntity();
		if(hitE != null) {
			player.attack(hitE);
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
	public void onLivingEntityDamage(EntityDamageEvent e) {
		if(e.getEntity() instanceof Player player) {
			var wrapper = Ioc.resolve(WrappingModule.class).getWrapper(player, InMapPhasePlayerWrapper.class);
			if(wrapper==null) return;
			if(wrapper.getParentWrapper().getTeam()==Vi6Team.GUARD) {
				//epsilon value
				e.setDamage(0.01);
			}
		}
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
	
	public void guardObjectiveSafe(Artefact artefact) {
		var name = artefact.getName();
		if(guardSafeTeam.hasEntry(name)) return;
		guardSafeTeam.addEntry(name);
		guardScoreboard.getScore(name).setScore(3);
	}
	public void guardObjectiveStolen(Artefact artefact) {
		var name = artefact.getName();
		if(guardStolenTeam.hasEntry(name)) return;
		guardStolenTeam.addEntry(name);
		guardScoreboard.getScore(name).setScore(1);
		if(--unfoundStolenArtefacts == 0) {
			guardUnknownToSafe();
		}
		//guardUpdateCount();
	}
	public void guardObjectiveEscaped(Artefact artefact) {
		var name = artefact.getName();
		if(guardEscapedTeam.hasEntry(name)) return;
		guardEscapedTeam.addEntry(name);
		guardScoreboard.getScore(name).setScore(0);
	}
	public void guardSafeToUnknown() {
		unfoundStolenArtefacts++;
		//guardUpdateCount();
		for (var artefact : map.getArtefacts().values()) {
			if(guardSafeTeam.hasEntry(artefact.getName())) {
				guardObjectiveUnknown(artefact);
			}
		}
	}
	private void guardUnknownToSafe() {
		for (var artefact : map.getArtefacts().values()) {
			if(guardUnknownTeam.hasEntry(artefact.getName())) {
				guardObjectiveSafe(artefact);
			}
		}
	}
	private void guardObjectiveUnknown(Artefact artefact) {
		var name = artefact.getName();
		if(guardUnknownTeam.hasEntry(name)) return;
		guardUnknownTeam.addEntry(name);
		guardScoreboard.getScore(name).setScore(2);
	}
	/*private void guardUpdateCount() {
		guardScoreboard.getScore(countString).setScore(unfoundStolenArtefacts);
	}*/
	
	//
	
	private void thiefObjectiveSafe(Artefact artefact) {
		var name = artefact.getName();
		thiefScoreboard.getScore(name).setScore(3);
		thiefScoreboard.getScore(name).customName(Component.text(name, NamedTextColor.GREEN));
	}
	public void thiefObjectiveStolen(Artefact artefact) {
		var name = artefact.getName();
		thiefScoreboard.getScore(name).setScore(2);
		thiefScoreboard.getScore(name).customName(Component.text(name, NamedTextColor.YELLOW));
	}
	
	public void thiefObjectiveEscaped(Artefact artefact) {
		var name = artefact.getName();
		thiefScoreboard.getScore(name).setScore(1);
		thiefScoreboard.getScore(name).customName(Component.text(name, NamedTextColor.AQUA));
	}
	public void thiefObjectiveLost(Artefact artefact) {
		var name = artefact.getName();
		thiefScoreboard.getScore(name).setScore(0);
		thiefScoreboard.getScore(name).customName(Component.text(name, NamedTextColor.RED));
	}
}
