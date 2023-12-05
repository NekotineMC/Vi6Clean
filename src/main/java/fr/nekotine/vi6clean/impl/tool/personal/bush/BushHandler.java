package fr.nekotine.vi6clean.impl.tool.personal.bush;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.Configuration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.plugin.java.JavaPlugin;

import com.destroystokyo.paper.MaterialSetTag;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.tool.ToolType;

public class BushHandler extends ToolHandler<Bush>{

	public BushHandler() {
		super(ToolType.BUSH, Bush::new);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
	}
	
	private final MaterialSetTag bushMaterials = new MaterialSetTag(NamespacedKey.fromString("bush_hideout", Ioc.resolve(JavaPlugin.class)),
			Material.PEONY, Material.TALL_GRASS, Material.LARGE_FERN,Material.LILAC, Material.ROSE_BUSH, Material.SMALL_DRIPLEAF, Material.KELP
			);
	
	private final double DETECTION_BLOCK_RANGE = Ioc.resolve(Configuration.class).getDouble("tool.bush.reveal_range", 2);
	
	private final double DETECTION_RANGE_SQUARED = DETECTION_BLOCK_RANGE * DETECTION_BLOCK_RANGE;
	
	@Override
	protected void onAttachedToPlayer(Bush tool, Player player) {
		tool.setInBush(bushMaterials.isTagged(player.getLocation().getBlock()));
	}

	@Override
	protected void onDetachFromPlayer(Bush tool, Player player) {
		tool.cleanup();
	}
	
	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		for (var tool : getTools()) {
			var owner = tool.getOwner();
			if (owner == null) {
				continue;
			}
			tool.setInBush(bushMaterials.isTagged(owner.getLocation().getBlock()));
			tool.setRevealed(false);// TODO HERE
		}
	}
	
}
