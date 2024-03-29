package fr.nekotine.vi6clean.impl.tool.personal.bush;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.plugin.java.JavaPlugin;

import com.destroystokyo.paper.MaterialSetTag;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;

@ToolCode("bush")
public class BushHandler extends ToolHandler<Bush>{

	public BushHandler() {
		super(Bush::new);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
	}
	
	private final MaterialSetTag bushMaterials = new MaterialSetTag(NamespacedKey.fromString("bush_hideout", Ioc.resolve(JavaPlugin.class)),
			Material.PEONY, Material.TALL_GRASS, Material.LARGE_FERN,Material.LILAC, Material.ROSE_BUSH, Material.SMALL_DRIPLEAF, Material.KELP
			);
	
	private final int FADE_OFF_DELAY = getConfiguration().getInt("fadeoff", 30);
	
	private final double DETECTION_BLOCK_RANGE = getConfiguration().getDouble("reveal_range", 2);
	
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
			var wrap = Ioc.resolve(WrappingModule.class).getWrapperOptional(owner, PlayerWrapper.class);
			if (wrap.isPresent()) {
				var ploc = owner.getLocation();
				tool.setRevealed(wrap.get().ennemiTeamInMap().anyMatch(e -> e.getLocation().distanceSquared(ploc) <= DETECTION_RANGE_SQUARED));
			}
		}
	}
	
	public int getFadeOffDelay() {
		return FADE_OFF_DELAY;
	}
	
}
