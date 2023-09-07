package fr.nekotine.vi6clean.impl.wrapper;

import java.util.LinkedList;
import java.util.List;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import fr.nekotine.core.NekotineCore;
import fr.nekotine.core.block.BlockPatch;
import fr.nekotine.core.block.fakeblock.AppliedFakeBlockPatch;
import fr.nekotine.core.wrapper.WrapperBase;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.Vi6Main;
import fr.nekotine.vi6clean.constant.InMapState;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.game.phase.Vi6PhaseInMap;
import fr.nekotine.vi6clean.impl.game.phase.Vi6PhaseInfiltration;
import fr.nekotine.vi6clean.impl.map.Entrance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class InMapPhasePlayerWrapper extends WrapperBase<Player> {
	
	private static final BlockPatch canLeaveMapBlockingPatch = new BlockPatch(s -> s.setType(Material.BARRIER));
	
	private List<AppliedFakeBlockPatch> mapLeaveBlockers = new LinkedList<>();
	
	private boolean canLeaveMap;
	
	private boolean canCaptureArtefact;
	
	private InMapState state;
	
	public InMapPhasePlayerWrapper(Player wrapped) {
		super(wrapped);
	}

	public boolean canLeaveMap() {
		return canLeaveMap;
	}
	
	public void updateMapLeaveBlocker() {
		var game = Vi6Main.IOC.resolve(Vi6Game.class);
		var map = game.getPhaseMachine().getPhase(Vi6PhaseInMap.class).getMap();
		if (canLeaveMap) {
			for (var blocker : mapLeaveBlockers) {
				blocker.unpatch();
			}
			mapLeaveBlockers.clear();
		}else {
			for (var entrance : map.getEntrances().backingMap().values()) {
				mapLeaveBlockers.addAll(entrance.getBlockingBox().applyFakeBlockPatch(wrapped, canLeaveMapBlockingPatch));
			}
			for (var exit : map.getExits().backingMap().values()) {
				mapLeaveBlockers.addAll(exit.applyFakeBlockPatch(wrapped, canLeaveMapBlockingPatch));
			}
		}
	}

	public void setCanLeaveMap(boolean canLeaveMap) {
		if (this.canLeaveMap != canLeaveMap) {
			this.canLeaveMap = canLeaveMap;
			updateMapLeaveBlocker();
		}
	}

	public boolean isInside() {
		return state == InMapState.INSIDE;
	}
	
	public void thiefScheduleCanLeaveMap() {
		setCanLeaveMap(false);
		new BukkitRunnable() {
			
			@Override
			public void run() {
				setCanLeaveMap(true);
				wrapped.sendMessage(Component.text("Vous pouvez désormais vous enfuire", NamedTextColor.GOLD));
			}
			
		}.runTaskLater(NekotineCore.getAttachedPlugin(), 30*20);
	}
	
	public void thiefScheduleCanCaptureArtefact() {
		canCaptureArtefact = false;
		new BukkitRunnable() {
			
			@Override
			public void run() {
				canCaptureArtefact = true;
				wrapped.sendMessage(Component.text("Vous pouvez désormais voler des artefacts", NamedTextColor.GOLD));
			}
			
		}.runTaskLater(NekotineCore.getAttachedPlugin(), 30*20);
	}
	
	public void thiefEnterInside(Entrance entrance) {
		state = InMapState.INSIDE;
		var game = Vi6Main.IOC.resolve(Vi6Game.class);
		game.getThiefs().sendMessage(wrapped.displayName().color(NamedTextColor.AQUA).append(Component.text(" est entré dans la carte par l'entrée ", NamedTextColor.GOLD).append(Component.text(entrance.getName(), NamedTextColor.AQUA))));
		game.getGuards().sendMessage(wrapped.displayName().color(NamedTextColor.AQUA).append(Component.text(" est rentré dans la carte", NamedTextColor.GOLD)));
		for (var guard : game.getGuards()) {
			guard.showEntity(NekotineCore.getAttachedPlugin(), wrapped);
		}
		thiefScheduleCanLeaveMap();
		thiefScheduleCanCaptureArtefact();
	}
	
	public void thiefLeaveMap() {
		var game = Vi6Main.IOC.resolve(Vi6Game.class);
		state = InMapState.LEFT;
		wrapped.setGameMode(GameMode.SPECTATOR);
		
		// Send message
		var infiltrationWrapper = NekotineCore.MODULES.get(WrappingModule.class).getWrapperOptional(wrapped, InfiltrationPhasePlayerWrapper.class);
		if (infiltrationWrapper.isPresent()) {
			game.sendMessage(wrapped.displayName().color(NamedTextColor.AQUA)
					.append(Component.text(" s'est échappé avec ", NamedTextColor.GOLD))
					.append(Component.text(infiltrationWrapper.get().getStolenArtefacts().size(), NamedTextColor.AQUA))
					.append(Component.text(" artéfacts!", NamedTextColor.GOLD)));
		}else {
			game.sendMessage(wrapped.displayName().color(NamedTextColor.AQUA)
					.append(Component.text(" s'est échappé!", NamedTextColor.GOLD)));
		}
		
		// Check for game end
		var infiltrationPhase = game.getPhaseMachine().getPhase(Vi6PhaseInfiltration.class);
		if (infiltrationPhase != null) {
			infiltrationPhase.checkForCompletion();
		}
	}
	
	public PlayerWrapper getParentWrapper() {
		return NekotineCore.MODULES.get(WrappingModule.class).getWrapper(wrapped, PlayerWrapper.class);
	}

	public boolean canCaptureArtefact() {
		return canCaptureArtefact;
	}

	public void setCanCaptureArtefact(boolean canCaptureArtefact) {
		this.canCaptureArtefact = canCaptureArtefact;
	}

	public InMapState getState() {
		return state;
	}

	public void setState(InMapState state) {
		this.state = state;
	}
}
