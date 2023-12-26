package fr.nekotine.vi6clean.impl.tool.personal.sonar;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;

@ToolCode("sonar")
public class SonarHandler extends ToolHandler<Sonar>{
	private final ItemStack ITEM = ItemStackUtil.make(
			Material.TARGET, 
			getDisplayName(), 
			getLore());
	private final ItemStack EMP_ITEM = ItemStackUtil.make(
			Material.QUARTZ_PILLAR, 
			getDisplayName(), 
			getLore());
	private final double DETECTION_BLOCK_RANGE = getConfiguration().getDouble("range", 5);
	private final double DETECTION_RANGE_SQUARED = DETECTION_BLOCK_RANGE * DETECTION_BLOCK_RANGE;
	private final int DELAY_TICK = (int)(20*getConfiguration().getDouble("delay", 3));
	
	public SonarHandler() {
		super(Sonar::new);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
	}
	
	
	private int counter = 0;
	
	@Override
	protected void onAttachedToPlayer(Sonar tool, Player player) {
	}

	@Override
	protected void onDetachFromPlayer(Sonar tool, Player player) {
	}
	
	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		if(++counter >= DELAY_TICK) {
			counter = 0;
			for (var tool : getTools()) {
				tool.pulse();
			}
		}
	}
	
	public double getDetectionBlockRange() {
		return DETECTION_BLOCK_RANGE;
	}
	public double getDetectionBlockRangeSquared() {
		return DETECTION_RANGE_SQUARED;
	}
	public ItemStack getItem() {
		return ITEM;
	}
	public ItemStack getEmpItem() {
		return EMP_ITEM;
	}
	public int getDelayTick() {
		return DELAY_TICK;
	}
}
