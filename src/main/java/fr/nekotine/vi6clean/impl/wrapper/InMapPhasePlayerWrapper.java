package fr.nekotine.vi6clean.impl.wrapper;

import java.util.LinkedList;
import java.util.List;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import fr.nekotine.core.bar.actionbar.ActionBarComponent;
import fr.nekotine.core.bar.actionbar.SharedActionBar;
import fr.nekotine.core.block.BlockPatch;
import fr.nekotine.core.block.fakeblock.AppliedFakeBlockPatch;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.status.effect.StatusEffect;
import fr.nekotine.core.status.effect.StatusEffectModule;
import fr.nekotine.core.wrapper.WrapperBase;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.InMapState;
import fr.nekotine.vi6clean.constant.Vi6Team;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.game.phase.Vi6PhaseInMap;
import fr.nekotine.vi6clean.impl.game.phase.Vi6PhaseInfiltration;
import fr.nekotine.vi6clean.impl.map.Entrance;
import fr.nekotine.vi6clean.impl.status.effect.invisibility.TrueInvisibilityStatusEffectType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class InMapPhasePlayerWrapper extends WrapperBase<Player> {
	
	private final StatusEffect invisibleEffect = new StatusEffect(TrueInvisibilityStatusEffectType.get(), -1);
	
	private static final BlockPatch canLeaveMapBlockingPatch = new BlockPatch(s -> s.setType(Material.BARRIER));
	
	private List<AppliedFakeBlockPatch> mapLeaveBlockers = new LinkedList<>();
	
	private boolean canLeaveMap;
	
	private boolean canCaptureArtefact;
	
	private InMapState state;
	
	private String room = "";
	
	private SharedActionBar artefactActionBar = new SharedActionBar();
	private ActionBarComponent artefactComponent = new ActionBarComponent();
	private SharedActionBar defaultActionBar = new SharedActionBar();
	private ActionBarComponent captureComponent = new ActionBarComponent();
	private ActionBarComponent leaveComponent = new ActionBarComponent();
	private ActionBarComponent roomComponent = new ActionBarComponent();
	{artefactActionBar.addComponent(artefactComponent);
	defaultActionBar.addComponent(roomComponent);}
	
	public InMapPhasePlayerWrapper(Player wrapped) {
		super(wrapped);
		var pw = Ioc.resolve(WrappingModule.class).getWrapperOptional(wrapped, PlayerWrapper.class);
		if(!pw.isPresent()) return;
		if(pw.get().getTeam()==Vi6Team.THIEF) {
			var effectModule = Ioc.resolve(StatusEffectModule.class);
			effectModule.addEffect(wrapped, invisibleEffect);
		}else {
			defaultActionBar.addViewers(wrapped);
		}
	}

	public boolean canLeaveMap() {
		return canLeaveMap;
	}
	
	public void updateMapLeaveBlocker() {
		var game = Ioc.resolve(Vi6Game.class);
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
			var cannot = Component.text("Fuyable ✘", NamedTextColor.RED);
			var can = Component.text("Fuyable ✓", NamedTextColor.GREEN);
			var text = canLeaveMap ? can : cannot;
			leaveComponent.setText(text);
			updateMapLeaveBlocker();
		}
	}

	public boolean isInside() {
		return state == InMapState.INSIDE;
	}
	
	public void thiefScheduleCanLeaveMap() {
		setCanLeaveMap(false);
		wrapped.sendMessage(Component.text("Vous pouvez pas vous enfuir avant 30s", NamedTextColor.RED));
		new BukkitRunnable() {
			
			@Override
			public void run() {
				setCanLeaveMap(true);
				wrapped.sendMessage(Component.text("Vous pouvez désormais vous enfuir", NamedTextColor.GOLD));
			}
			
		}.runTaskLater(Ioc.resolve(JavaPlugin.class), 30*20);
	}
	
	public void thiefScheduleCanCaptureArtefact() {
		setCanCaptureArtefact(false);
		wrapped.sendMessage(Component.text("Vous pouvez pas voler d'artefacts avant 30s", NamedTextColor.RED));
		new BukkitRunnable() {
			
			@Override
			public void run() {
				setCanCaptureArtefact(true);
				wrapped.sendMessage(Component.text("Vous pouvez désormais voler des artefacts", NamedTextColor.GOLD));
			}
			
		}.runTaskLater(Ioc.resolve(JavaPlugin.class), 30*20);
	}
	
	public void thiefEnterInside(Entrance entrance) {
		state = InMapState.INSIDE;

		var separator = Component.text(" | ");
		defaultActionBar.addComponents(new ActionBarComponent(separator,1),new ActionBarComponent(separator,3));
		defaultActionBar.addComponents(captureComponent,leaveComponent);
		captureComponent.setPriority(4);leaveComponent.setPriority(2);
		defaultActionBar.addViewers(GetWrapped());
		
		var game = Ioc.resolve(Vi6Game.class);
		game.getThiefs().sendMessage(wrapped.displayName().color(NamedTextColor.AQUA).append(Component.text(" est entré dans la carte par l'entrée ", NamedTextColor.GOLD).append(Component.text(entrance.getName(), NamedTextColor.AQUA))));
		game.getGuards().sendMessage(wrapped.displayName().color(NamedTextColor.AQUA).append(Component.text(" est rentré dans la carte", NamedTextColor.GOLD)));
		for (var guard : game.getGuards()) {
			guard.showEntity(Ioc.resolve(JavaPlugin.class), wrapped);
		}
		var effectModule = Ioc.resolve(StatusEffectModule.class);
		effectModule.removeEffect(wrapped, invisibleEffect);
		thiefScheduleCanLeaveMap();
		thiefScheduleCanCaptureArtefact();
		var infiltrationPhase = game.getPhaseMachine().getPhase(Vi6PhaseInfiltration.class);
		if (infiltrationPhase != null) {
			infiltrationPhase.setIngameScannerDelay();
		}
	}
	
	public void thiefLeaveMap() {
		thiefLeaveMap(false);
	}
	
	public void thiefLeaveMap(boolean dead) {
		var game = Ioc.resolve(Vi6Game.class);
		state = InMapState.LEFT;
		wrapped.setGameMode(GameMode.SPECTATOR);
		
		// Send message
		var infiltrationWrapper = Ioc.resolve(WrappingModule.class).getWrapperOptional(wrapped, InfiltrationPhasePlayerWrapper.class);
		if (infiltrationWrapper.isPresent()) {
			infiltrationWrapper.get().setDead(dead);
			if (dead) {
				game.sendMessage(wrapped.displayName().color(NamedTextColor.AQUA)
						.append(Component.text(" est mort avec ", NamedTextColor.GOLD))
						.append(Component.text(infiltrationWrapper.get().getStolenArtefacts().size(), NamedTextColor.AQUA))
						.append(Component.text(" artéfacts!", NamedTextColor.GOLD)));
			}else {
				game.sendMessage(wrapped.displayName().color(NamedTextColor.AQUA)
						.append(Component.text(" s'est échappé avec ", NamedTextColor.GOLD))
						.append(Component.text(infiltrationWrapper.get().getStolenArtefacts().size(), NamedTextColor.AQUA))
						.append(Component.text(" artéfacts!", NamedTextColor.GOLD)));
			}
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
		return Ioc.resolve(WrappingModule.class).getWrapper(wrapped, PlayerWrapper.class);
	}

	public boolean canCaptureArtefact() {
		return canCaptureArtefact && isInside();
	}

	public void setCanCaptureArtefact(boolean canCaptureArtefact) {
		this.canCaptureArtefact = canCaptureArtefact;
		var cannot = Component.text("Volable ✘", NamedTextColor.RED);
		var can = Component.text("Volable ✓", NamedTextColor.GREEN);
		var text = canCaptureArtefact ? can : cannot;
		captureComponent.setText(text);
	}

	public InMapState getState() {
		return state;
	}

	public void setState(InMapState state) {
		this.state = state;
	}

	public String getRoom() {
		return room;
	}

	public void setRoom(String room) {
		this.room = room;
		var text = 
				Component.text("Salle: ",NamedTextColor.GOLD).append(
				Component.text(room,NamedTextColor.AQUA));
		roomComponent.setText(text);
	}
	
	public void showArtefactBar() {
		artefactActionBar.addViewers(GetWrapped());
		defaultActionBar.removeViewers(GetWrapped());
	}
	
	public void showDefaultBar() {
		defaultActionBar.addViewers(GetWrapped());
		artefactActionBar.removeViewers(GetWrapped());
	}
	
	public ActionBarComponent getArtefactComponent() {
		return artefactComponent;
	}
}
