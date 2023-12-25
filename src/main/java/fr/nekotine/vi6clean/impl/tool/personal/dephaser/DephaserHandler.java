package fr.nekotine.vi6clean.impl.tool.personal.dephaser;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;

@ToolCode("dephaser")
public class DephaserHandler extends ToolHandler<Dephaser>{
	
	private final int DELAY_BETWEEN_INVISIBILITY_TICKS=
			(int)(20 * getConfiguration().getDouble("inv_delay", 20));
	
	private final int INVISIBILITY_DURATION_TICKS = 
			(int)(20*getConfiguration().getDouble("inv_duration", 2));
	
	private final int DELAY_BETWEEN_WARNING_SOUND=10;

	private int count = DELAY_BETWEEN_INVISIBILITY_TICKS - INVISIBILITY_DURATION_TICKS + 1;
	
	public DephaserHandler() {
		super(Dephaser::new);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
	}

	//
	
	@EventHandler
	public void onTick(TickElapsedEvent evt) {
		count--;
		if (count == -INVISIBILITY_DURATION_TICKS+DELAY_BETWEEN_INVISIBILITY_TICKS+DELAY_BETWEEN_WARNING_SOUND*2) {
			getTools().forEach(t -> t.playerWarningHigh());
			return;
		}
		if (count == -INVISIBILITY_DURATION_TICKS+DELAY_BETWEEN_INVISIBILITY_TICKS+DELAY_BETWEEN_WARNING_SOUND) {
			getTools().forEach(t -> t.playerWarningMid());
			return;
		}
		if (count == -INVISIBILITY_DURATION_TICKS+DELAY_BETWEEN_INVISIBILITY_TICKS) {
			getTools().forEach(t -> t.deactivate());
			return;
		}
		if (count == DELAY_BETWEEN_WARNING_SOUND*2) {
			getTools().forEach(t -> t.playWarningLow());
			return;
		}
		if (count == DELAY_BETWEEN_WARNING_SOUND) {
			getTools().forEach(t -> t.playerWarningMid());
			return;
		}
		if (count == 0) {
			getTools().forEach(t -> t.activate());
			count=DELAY_BETWEEN_INVISIBILITY_TICKS;
			return;
		}
	}
	
	//
	
	@Override
	protected void onAttachedToPlayer(Dephaser tool, Player player) {
	}

	@Override
	protected void onDetachFromPlayer(Dephaser tool, Player player) {
	}
	
	public int getDelayBetweenInvisibilityTick() {
		return DELAY_BETWEEN_INVISIBILITY_TICKS;
	}
	
	public int getInvisibilityDurationTick() {
		return INVISIBILITY_DURATION_TICKS;
	}
}
