package fr.nekotine.vi6clean.impl.tool.personal.parabolic_mic;

import java.util.List;

import org.bukkit.Particle;
import org.bukkit.Vibration;
import org.bukkit.configuration.Configuration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.vi6clean.constant.Vi6ToolLoreText;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.tool.ToolType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public class ParabolicMicHandler extends ToolHandler<ParabolicMic>{

	public ParabolicMicHandler() {
		super(ToolType.PARABOLIC_MIC, ParabolicMic::new);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
	}
	
	private final double DETECTION_BLOCK_RANGE = Ioc.resolve(Configuration.class).getDouble("tool.parabolic_mic.range", 20d);
	
	private final double DETECTION_RANGE_SQUARED = DETECTION_BLOCK_RANGE * DETECTION_BLOCK_RANGE;
	
	public static final List<Component> LORE = Vi6ToolLoreText.PARABOLIC_MIC.make(
			Placeholder.unparsed("range", Ioc.resolve(Configuration.class).getDouble("tool.parabolic_mic.range", 20)+" blocs")
			);
	
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
		for (var tool : getTools()) {
			var owner = tool.getOwner();
			var vibrationTarget = tool.getVibrationTargetEntity();
			if (owner == null || vibrationTarget == null || evt.getPlayer().equals(vibrationTarget)) {
				continue;
			}
			var ownerloc = owner.getLocation();
			var destloc = evt.getTo();
			if (evt.getPlayer().equals(owner)) {
				vibrationTarget.teleport(owner);
			}
			if (!ownerloc.getWorld().equals(destloc.getWorld()) || evt.getPlayer().equals(owner) || evt.getTo().distanceSquared(owner.getLocation()) > DETECTION_RANGE_SQUARED) {
				continue;
			}
			var vibration = new Vibration(new Vibration.Destination.EntityDestination(vibrationTarget), 20);
			owner.spawnParticle(Particle.VIBRATION, evt.getTo(), 1, vibration);
		}
	}
	
}
