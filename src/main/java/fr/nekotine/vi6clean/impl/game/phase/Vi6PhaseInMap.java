package fr.nekotine.vi6clean.impl.game.phase;

import java.util.LinkedList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import fr.nekotine.core.NekotineCore;
import fr.nekotine.core.game.phase.CollectionPhase;
import fr.nekotine.core.game.phase.IPhaseMachine;
import fr.nekotine.core.map.MapModule;
import fr.nekotine.core.state.ItemState;
import fr.nekotine.core.state.ItemWrappingState;
import fr.nekotine.core.state.RegisteredEventListenerState;
import fr.nekotine.core.state.State;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.AssertUtil;
import fr.nekotine.core.util.DebugUtil;
import fr.nekotine.core.util.collection.ObservableCollection;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.Vi6Main;
import fr.nekotine.vi6clean.constant.InMapState;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.map.Vi6Map;
import fr.nekotine.vi6clean.impl.map.artefact.Artefact;
import fr.nekotine.vi6clean.impl.tool.ToolType;
import fr.nekotine.vi6clean.impl.wrapper.InMapPhasePlayerWrapper;

public class Vi6PhaseInMap extends CollectionPhase<Vi6PhaseGlobal,Player> implements Listener{
	
	private Vi6Map map;
	
	private List<BlockDisplay> debugDisplays = new LinkedList<>();
	
	public Vi6PhaseInMap(IPhaseMachine machine) {
		super(machine);
	}

	@Override
	public Class<Vi6PhaseGlobal> getParentType() {
		return Vi6PhaseGlobal.class;
	}

	@Override
	public ObservableCollection<Player> getItemCollection() {
		return Vi6Main.IOC.resolve(Vi6Game.class).getPlayerList();
	}
	
	@Override
	public void globalSetup(Object inputData) {
		var game = Vi6Main.IOC.resolve(Vi6Game.class);
		var mapName = game.getMapName();
		if (mapName == null) {
			var maps = NekotineCore.MODULES.get(MapModule.class).getMapFinder().list();
			if (maps.size() <= 0) {
				throw new IllegalStateException("Aucune map n'est disponible");
			}
			mapName = maps.get(0).getName();
		}
		map = NekotineCore.MODULES.get(MapModule.class).getMapFinder().findByName(Vi6Map.class, mapName).loadConfig();
		AssertUtil.nonNull(map, "La map n'a pas pus etre chargee");
		var artefacts = map.getArtefacts().backingMap();
		var world = game.getWorld();
		for (var artefact : artefacts.values()) {
			if (game.isDebug()) {
				debugDisplays.add(DebugUtil.debugBoundingBox(world, artefact.getBoundingBox(), Bukkit.createBlockData(Material.GLASS)));
			}
		}
		var entrances = map.getEntrances().backingMap();
		for (var entranceName : entrances.keySet()) {
			var entrance = entrances.get(entranceName);
			entrance.setName(entranceName);
			if (game.isDebug()) {
				debugDisplays.add(DebugUtil.debugBoundingBox(world, entrance.getBlockingBox().get(), Bukkit.createBlockData(Material.ORANGE_STAINED_GLASS)));
				debugDisplays.add(DebugUtil.debugBoundingBox(world, entrance.getEntranceTriggerBox().get(), Bukkit.createBlockData(Material.GREEN_STAINED_GLASS)));
			}
		}
		if (game.isDebug()) {
			for (var exit : map.getExits().backingMap().values()) {
				debugDisplays.add(DebugUtil.debugBoundingBox(world, exit.get(), Bukkit.createBlockData(Material.RED_STAINED_GLASS)));
			}
		}
		for (var tool : ToolType.values()) {
			tool.getHandler().startHandling();
		}
	}

	@Override
	public void globalTearDown() {
		for (var display : debugDisplays) {
			display.remove();
		}
		debugDisplays.clear();
		for (var artefact : map.getArtefacts().backingMap().values()) {
			artefact.clean();
		}
		for (var tool : ToolType.values()) {
			tool.getHandler().stopHandling();
			tool.getHandler().removeAll();
		}
		map = null;
	}
	
	@Override
	public void itemSetup(Player item) {
		var wrappingModule = NekotineCore.MODULES.get(WrappingModule.class);
		var wrap = wrappingModule.getWrapper(item, InMapPhasePlayerWrapper.class);
		item.setGameMode(GameMode.ADVENTURE);
		if (!wrap.getParentWrapper().isThief()) {
			wrap.setCanLeaveMap(false);
			wrap.updateMapLeaveBlocker();
		}
	}

	@Override
	public void itemTearDown(Player item) {
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
		return list;
	}
	
	
	public Vi6Map getMap() {
		return map;
	}
	
	// Event handlers
	
	@EventHandler
	private void onPlayerMove(PlayerMoveEvent evt) {
		var player = evt.getPlayer();
		var optWrapper = NekotineCore.MODULES.get(WrappingModule.class).getWrapperOptional(player, InMapPhasePlayerWrapper.class);
		if (optWrapper.isEmpty()) {
			return;
		}
		var wrapper = optWrapper.get();
		var destVect = evt.getTo().toVector();
		if (wrapper.isInside()) {
			if (!wrapper.canLeaveMap()) {
				if (map.getEntrances().backingMap().values().stream().map(e -> e.getBlockingBox().get()).anyMatch(bb -> bb.contains(destVect))){
					evt.setCancelled(true);
					return;
				}
				if (map.getExits().backingMap().values().stream().map(e -> e.get()).anyMatch(bb -> bb.contains(destVect))){
					evt.setCancelled(true);
					return;
				}
			}else {
				var exit = map.getExits().backingMap().values().stream().filter(e -> e.get().contains(destVect)).findFirst();
				if (exit.isPresent()) {
					wrapper.thiefLeaveMap();
					return;
				}
			}
			map.getArtefacts().backingMap().values().stream().filter(a -> !a.isCaptured()).forEach(artefact -> {
				var zone = artefact.getInsideCaptureZone();
				if (artefact.getBoundingBox().contains(destVect)) {
					zone.add(player);
				}else {
					zone.remove(player);
				}
			});
		}else if (wrapper.getState() == InMapState.ENTERING){
			var entrance = map.getEntrances().backingMap().values().stream()
					.filter(e -> e.getEntranceTriggerBox().get().contains(destVect))
					.findFirst();
			if (entrance.isPresent()) {
				wrapper.thiefEnterInside(entrance.get());
			}
		}
	}
	
	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		map.getArtefacts().backingMap().values().stream().forEach(Artefact::tick);
	}

	@EventHandler
	private void onPlayerDeath(PlayerDeathEvent evt) {
		var game = Vi6Main.IOC.resolve(Vi6Game.class);
		var player = evt.getPlayer();
		if (game.getThiefs().contains(evt.getPlayer())){
			var optWrap = NekotineCore.MODULES.get(WrappingModule.class).getWrapperOptional(player, InMapPhasePlayerWrapper.class);
			if (optWrap.isPresent()) {
				optWrap.get().thiefLeaveMap();
			}
		}
	}
	
}
