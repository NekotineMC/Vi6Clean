package fr.nekotine.vi6clean.impl.tool.personal.sixth_sense;

import org.bukkit.event.EventHandler;

import fr.nekotine.core.glow.EntityGlowModule;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;

@ToolCode("sixth_sense")
public class SixthSenseHandler extends ToolHandler<SixthSenseHandler.SixthSense> {

	private final double DETECTION_BLOCK_RANGE = getConfiguration().getDouble("range", 6);

	private final double DETECTION_RANGE_SQUARED = DETECTION_BLOCK_RANGE * DETECTION_BLOCK_RANGE;

	public SixthSenseHandler() {
		super(SixthSense::new);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
	}

	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		var glowModule = Ioc.resolve(EntityGlowModule.class);
		var wrappingModule = Ioc.resolve(WrappingModule.class);
		for (var tool : getTools()) {
			var owner = tool.getOwner();
			if (owner == null /* || Ioc.resolve(StatusFlagModule.class).hasAny(owner, EmpStatusFlag.get()) */) {
				continue;
			}
			var wrap = wrappingModule.getWrapper(owner, PlayerWrapper.class);
			var ownerloc = owner.getLocation();
			wrap.enemyTeamInMap().forEach(en -> {
				if (ownerloc.distanceSquared(en.getLocation()) <= DETECTION_RANGE_SQUARED) {
					glowModule.glowEntityFor(en, owner);
				} else {
					glowModule.unglowEntityFor(en, owner);
				}
			});
		}
	}

	@Override
	protected void onAttachedToPlayer(SixthSense tool) {
	}

	@Override
	protected void onDetachFromPlayer(SixthSense tool) {
	}

	@Override
	protected void onToolCleanup(SixthSense tool) {
	}

	public static class SixthSense extends Tool {

		public SixthSense(ToolHandler<?> handler) {
			super(handler);
		}

	}
}
