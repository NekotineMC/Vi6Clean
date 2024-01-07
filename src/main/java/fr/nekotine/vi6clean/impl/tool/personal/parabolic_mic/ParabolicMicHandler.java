package fr.nekotine.vi6clean.impl.tool.personal.parabolic_mic;

import org.bukkit.Particle;
import org.bukkit.Vibration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.impl.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;

@ToolCode("parabolic_mic")
public class ParabolicMicHandler extends ToolHandler<ParabolicMic>{

	public ParabolicMicHandler() {
		super(ParabolicMic::new);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
	}
	
	private final double DETECTION_BLOCK_RANGE = getConfiguration().getDouble("range", 20d);
	
	private final double DETECTION_RANGE_SQUARED = DETECTION_BLOCK_RANGE * DETECTION_BLOCK_RANGE;
	
	@Override
	protected void onAttachedToPlayer(ParabolicMic tool, Player player) {
		var passenger = (TextDisplay)player.getWorld().spawnEntity(player.getLocation(), EntityType.TEXT_DISPLAY);
		tool.setVibrationTargetEntity(passenger);
	}

	@Override
	protected void onDetachFromPlayer(ParabolicMic tool, Player player) {
		var passenger = tool.getVibrationTargetEntity();
		tool.setVibrationTargetEntity(null);
		passenger.remove();
		
	}
	
	@EventHandler
	private void onMove(PlayerMoveEvent evt) {
		if (!evt.hasChangedBlock()) {
			return;
		}
		var flagModule = Ioc.resolve(StatusFlagModule.class);
		for (var tool : getTools()) {
			var owner = tool.getOwner();
			var vibrationTarget = tool.getVibrationTargetEntity();
			var evtPlayer = evt.getPlayer();
			if (owner == null || vibrationTarget == null || evtPlayer.equals(vibrationTarget)) {
				continue;
			}
			var ownerloc = owner.getLocation();
			var destloc = evt.getTo();
			var enemyTeam = Ioc.resolve(WrappingModule.class).getWrapper(owner, PlayerWrapper.class).ennemiTeamInMap();
			if (evtPlayer.equals(owner)) {
				vibrationTarget.teleport(owner);
				continue;
			}
			if (!ownerloc.getWorld().equals(destloc.getWorld()) ||
					!enemyTeam.anyMatch(e -> e.equals(evtPlayer)) ||
					evtPlayer.equals(owner) ||
					evt.getTo().distanceSquared(owner.getLocation()) > DETECTION_RANGE_SQUARED ||
					flagModule.hasAny(owner, EmpStatusFlag.get())) {
				continue;
			}
			var vibration = new Vibration(new Vibration.Destination.EntityDestination(vibrationTarget), 20);
			owner.spawnParticle(Particle.VIBRATION, evt.getTo(), 1, vibration);
		}
	}
	
}
