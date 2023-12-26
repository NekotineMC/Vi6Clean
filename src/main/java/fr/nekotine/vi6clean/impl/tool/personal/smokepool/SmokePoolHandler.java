package fr.nekotine.vi6clean.impl.tool.personal.smokepool;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.inventory.ItemStackBuilder;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;

@ToolCode("smokepool")
public class SmokePoolHandler extends ToolHandler<SmokePool>{
	private final int DURATION_TICK = (int)(20*getConfiguration().getDouble("duration", 8));
	private final int COOLDOWN_TICK = (int)(20*getConfiguration().getDouble("cooldown", 20));
	private final ItemStack ITEM = new ItemStackBuilder(
			Material.FIREWORK_STAR)
			.name(null)
			.lore(getLore()).build();
			
	public SmokePoolHandler() {
		super(SmokePool::new);
	}

	@Override
	protected void onAttachedToPlayer(SmokePool tool, Player player) {
	}
	@Override
	protected void onDetachFromPlayer(SmokePool tool, Player player) {
	}
	
	public int getDurationTick() {
		return DURATION_TICK;
	}
	public int getCooldownTick() {
		return COOLDOWN_TICK;
	}
}
