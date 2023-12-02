package fr.nekotine.vi6clean.impl.tool.personal.parabolic_mic;

import java.util.List;

import org.bukkit.configuration.Configuration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.vi6clean.constant.Vi6ToolLoreText;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.tool.ToolType;
import io.papermc.paper.event.entity.EntityMoveEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public class ParabolicMicHandler extends ToolHandler<ParabolicMic>{

	public ParabolicMicHandler() {
		super(ToolType.PARABOLIC_MIC, ParabolicMic::new);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
	}
	
	public static final double DETECTION_BLOCK_RANGE = Ioc.resolve(Configuration.class).getDouble("tool.parabolic_mic.range", 20);
	
	private static final double DETECTION_RANGE_SQUARED = DETECTION_BLOCK_RANGE * DETECTION_BLOCK_RANGE;
	
	public static final List<Component> LORE = Vi6ToolLoreText.INVISNEAK.make(
			Placeholder.unparsed("range", Ioc.resolve(Configuration.class).getDouble("tool.parabolic_mic.range", 20)+" blocs")
			);
	
	@Override
	protected void onAttachedToPlayer(ParabolicMic tool, Player player) {
	}

	@Override
	protected void onDetachFromPlayer(ParabolicMic tool, Player player) {
	}
	
	@EventHandler
	private void onMove(EntityMoveEvent evt) {
		
	}
	
}
