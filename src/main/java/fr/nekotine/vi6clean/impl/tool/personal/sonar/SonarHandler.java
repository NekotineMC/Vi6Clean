package fr.nekotine.vi6clean.impl.tool.personal.sonar;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.ticking.TickTimeStamp;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.vi6clean.constant.Vi6ToolLoreText;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

@ToolCode("sonar")
public class SonarHandler extends ToolHandler<Sonar>{
	protected static final ItemStack ITEM = ItemStackUtil.make(Material.TARGET, Component.text("Sonar", NamedTextColor.GOLD), SonarHandler.LORE);
	protected static final ItemStack EMP_ITEM = ItemStackUtil.make(Material.QUARTZ_PILLAR, Component.text("Sonar", NamedTextColor.GOLD), SonarHandler.LORE);

	public SonarHandler() {
		super(Sonar::new);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
	}
	
	public static final int DETECTION_BLOCK_RANGE = 5;
	
	public static final int DETECTION_RANGE_SQUARED = DETECTION_BLOCK_RANGE * DETECTION_BLOCK_RANGE;
	
	public static final int DELAY_SECOND = 3;
	
	public static final List<Component> LORE = Vi6ToolLoreText.SONAR.make(
			Placeholder.unparsed("range", DETECTION_BLOCK_RANGE+" blocs"),
			Placeholder.parsed("delay", DELAY_SECOND+" secondes")
			);
	
	private int counter = 0;
	
	@Override
	protected void onAttachedToPlayer(Sonar tool, Player player) {
	}

	@Override
	protected void onDetachFromPlayer(Sonar tool, Player player) {
	}
	
	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		if (evt.timeStampReached(TickTimeStamp.Second)){
			if (++counter >= DELAY_SECOND) {
				counter = 0;
				for (var tool : getTools()) {
					tool.pulse();
				}
			}
		}
	}
	
}
