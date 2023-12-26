package fr.nekotine.vi6clean.impl.tool.personal.invisneak;

import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.ticking.TickTimeStamp;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

@ToolCode("invisneak")
public class InviSneakHandler extends ToolHandler<InviSneak>{
	private final double DETECTION_BLOCK_RANGE = getConfiguration().getDouble("range",3);
	private final double DETECTION_RANGE_SQUARED = DETECTION_BLOCK_RANGE * DETECTION_BLOCK_RANGE;
	
	private final ItemStack VISIBLE_ITEM = ItemStackUtil.make(
			Material.WHITE_STAINED_GLASS_PANE,
			getDisplayName().append(Component.text(" - ")).append(Component.text("Visible", NamedTextColor.WHITE)),
			getLore());
	
	private final ItemStack INVISIBLE_ITEM = ItemStackUtil.make(Material.GLASS_PANE,
			getDisplayName().append(Component.text(" - ")).append(Component.text("Invisible", NamedTextColor.GRAY)),
			getLore());
	
	private final ItemStack REVEALED_ITEM = ItemStackUtil.make(Material.RED_STAINED_GLASS_PANE,
			getDisplayName().append(Component.text(" - ")).append(Component.text("Découvert", NamedTextColor.RED)),
			getLore());
	
	public InviSneakHandler() {
		super(InviSneak::new);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
	}
	@Override
	protected void onAttachedToPlayer(InviSneak tool, Player player) {
		tool.setSneaking(false);
	}

	@Override
	protected void onDetachFromPlayer(InviSneak tool, Player player) {
	}
	
	@EventHandler
	private void onPlayerToggleSneak(PlayerToggleSneakEvent evt) {
		var tools = getTools().stream().filter(t -> evt.getPlayer().equals(t.getOwner())).collect(Collectors.toUnmodifiableSet());
		for (var tool : tools) {
			tool.setSneaking(evt.isSneaking());
		}
	}
	
	private boolean isEnnemiNear(PlayerWrapper wrap) {
		var player = wrap.GetWrapped();
		return wrap.ennemiTeamInMap().anyMatch(ennemi -> player.getLocation().distanceSquared(ennemi.getLocation()) <= DETECTION_RANGE_SQUARED);
	}
	
	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		var wrappingModule = Ioc.resolve(WrappingModule.class);
		for (var tool : getTools()) {
			var wrap = wrappingModule.getWrapperOptional(tool.getOwner(), PlayerWrapper.class);
			if (wrap.isEmpty()) {
				continue;
			}
			tool.setRevealed(isEnnemiNear(wrap.get()));
		}
		if (evt.timeStampReached(TickTimeStamp.QuartSecond)){
			for (var tool : getTools()) {
				tool.lowTick();
			}
		}
	}
	
	public double getDetectionBlockRange() {
		return DETECTION_BLOCK_RANGE;
	}
	public ItemStack getVisibleItem() {
		return VISIBLE_ITEM;
	}
	public ItemStack getInvisibleItem() {
		return INVISIBLE_ITEM;
	}
	public ItemStack getRevealedItem() {
		return REVEALED_ITEM;
	}
	
}
