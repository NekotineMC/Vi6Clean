package fr.nekotine.vi6clean.impl.wrapper;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import fr.nekotine.core.NekotineCore;
import fr.nekotine.core.wrapper.WrapperBase;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.Vi6Main;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.game.phase.Vi6PhaseInfiltration;
import fr.nekotine.vi6clean.impl.map.Entrance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class InMapPhasePlayerWrapper extends WrapperBase<Player> {
	
	private boolean canLeaveMap;
	
	private boolean isInside;
	
	private boolean canCaptureArtefact;
	
	public InMapPhasePlayerWrapper(Player wrapped) {
		super(wrapped);
	}

	public boolean canLeaveMap() {
		return canLeaveMap;
	}

	public void setCanLeaveMap(boolean canLeaveMap) {
		this.canLeaveMap = canLeaveMap;
	}

	public boolean isInside() {
		return isInside;
	}
	
	public void thiefScheduleCanLeaveMap() {
		canLeaveMap = false;
		new BukkitRunnable() {
			
			@Override
			public void run() {
				canLeaveMap = true;
				wrapped.sendMessage(Component.text("Vous pouvez désormais vous enfuire", NamedTextColor.GOLD));
			}
			
		}.runTaskLater(NekotineCore.getAttachedPlugin(), 60*20);
	}
	
	public void thiefScheduleCanCaptureArtefact() {
		canCaptureArtefact = false;
		new BukkitRunnable() {
			
			@Override
			public void run() {
				canCaptureArtefact = true;
				wrapped.sendMessage(Component.text("Vous pouvez désormais vous voler des artefacts", NamedTextColor.GOLD));
			}
			
		}.runTaskLater(NekotineCore.getAttachedPlugin(), 60*20);
	}
	
	public void thiefEnterInside(Entrance entrance) {
		isInside = true;
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
		isInside = false;
		var infiltrationPhase = Vi6Main.IOC.resolve(Vi6Game.class).getPhaseMachine().getPhase(Vi6PhaseInfiltration.class);
		if (infiltrationPhase != null) {
			infiltrationPhase.checkForCompletion();
		}
	}
	
	public PlayerWrapper getParentWrapper() {
		return NekotineCore.MODULES.get(WrappingModule.class).getWrapper(wrapped, PlayerWrapper.class);
	}

	public void setInside(boolean isInside) {
		this.isInside = isInside;
	}

	public boolean canCaptureArtefact() {
		return canCaptureArtefact;
	}

	public void setCanCaptureArtefact(boolean canCaptureArtefact) {
		this.canCaptureArtefact = canCaptureArtefact;
	}
}
