package fr.nekotine.vi6clean.impl.tool.personal.dephaser;

import java.util.List;

import org.bukkit.configuration.Configuration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.vi6clean.constant.Vi6ToolLoreText;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

@ToolCode("dephaser")
public class DephaserHandler extends ToolHandler<Dephaser>{
	protected static final int DELAY_BETWEEN_INVISIBILITY_TICKS=
			20 * Ioc.resolve(Configuration.class).getInt("tool.dephaser.inv_delay", 20);
	protected static final int INVISIBILITY_DURATION_TICKS= 
			20 * Ioc.resolve(Configuration.class).getInt("tool.dephaser.inv_duration", 2);
	
	private static final int DELAY_BETWEEN_WARNING_SOUND=10;
	public static final List<Component> LORE = Vi6ToolLoreText.DEPHASER.make(
			Placeholder.unparsed("delay", (int)(DELAY_BETWEEN_INVISIBILITY_TICKS/20)+"s"),
			Placeholder.unparsed("duration", (int)(INVISIBILITY_DURATION_TICKS/20)+"s")
	);
	private int count = DELAY_BETWEEN_INVISIBILITY_TICKS - INVISIBILITY_DURATION_TICKS + 1;
	public DephaserHandler() {
		super(Dephaser::new);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
	}

	//
	
	@EventHandler
	public void onTick(TickElapsedEvent evt) {
		count--;
		switch(count) {
		case -INVISIBILITY_DURATION_TICKS+DELAY_BETWEEN_INVISIBILITY_TICKS+DELAY_BETWEEN_WARNING_SOUND*2:
			getTools().forEach(t -> t.playerWarningHigh());
			break;
		case -INVISIBILITY_DURATION_TICKS+DELAY_BETWEEN_INVISIBILITY_TICKS+DELAY_BETWEEN_WARNING_SOUND:
			getTools().forEach(t -> t.playerWarningMid());
			break;
		case -INVISIBILITY_DURATION_TICKS+DELAY_BETWEEN_INVISIBILITY_TICKS:
			getTools().forEach(t -> t.deactivate());
			break;
		case DELAY_BETWEEN_WARNING_SOUND*2:
			getTools().forEach(t -> t.playWarningLow());
			break;
		case DELAY_BETWEEN_WARNING_SOUND:
			getTools().forEach(t -> t.playerWarningMid());
			break;
		case 0:
			getTools().forEach(t -> t.activate());
			count=DELAY_BETWEEN_INVISIBILITY_TICKS;
			break;
		default:
			break;
		}
	}
	
	//
	
	@Override
	protected void onAttachedToPlayer(Dephaser tool, Player player) {
	}

	@Override
	protected void onDetachFromPlayer(Dephaser tool, Player player) {
	}
}
