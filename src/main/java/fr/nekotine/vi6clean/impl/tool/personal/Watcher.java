package fr.nekotine.vi6clean.impl.tool.personal;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Silverfish;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.vi6clean.constant.Vi6ToolLoreText;
import fr.nekotine.vi6clean.impl.tool.Tool;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class Watcher extends Tool{

	private List<Silverfish> watchers = new LinkedList<>();
	
	private boolean isSneaking;
	
	private Collection<Player> ennemiesInRange = new LinkedList<>();
	
	@Override
	protected ItemStack makeInitialItemStack() {
		return ItemStackUtil.make(Material.WHITE_STAINED_GLASS_PANE,
				Component.text("Observateur",NamedTextColor.GOLD),
				Vi6ToolLoreText.WATCHER.make());
	}
	
	public void dropWatcher() {
		
	}

	public List<Silverfish> getWatcherList(){
		return watchers;
	}
	
	@Override
	protected void cleanup() {
		for (var watcher : watchers) {
			watcher.remove();
		}
		watchers.clear();
	}
	
	public void setSneaking(boolean sneaking) {
		isSneaking = sneaking;
	}
	
	public boolean isSneaking() {
		return isSneaking;
	}
}
