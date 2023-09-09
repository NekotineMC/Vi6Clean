package fr.nekotine.vi6clean.impl.tool.personal;

import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.NekotineCore;
import fr.nekotine.core.ticking.TickTimeStamp;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6EffectType;
import fr.nekotine.vi6clean.constant.Vi6ToolLoreText;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.tool.ToolType;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public class InviSneakHandler extends ToolHandler<InviSneak>{

	public InviSneakHandler() {
		super(ToolType.INVISNEAK, InviSneak::new);
	}
	
	public static final int DETECTION_BLOCK_RANGE = 3;
	
	private static final int DETECTION_RANGE_SQUARED = DETECTION_BLOCK_RANGE * DETECTION_BLOCK_RANGE;
	
	public static final List<Component> LORE = Vi6ToolLoreText.INVISNEAK.make(
			Placeholder.unparsed("range", DETECTION_BLOCK_RANGE+" block"),
			Placeholder.parsed("statusname", Vi6EffectType.INVISIBLE.getStatusName()),
			Placeholder.component("statusdescription", Vi6EffectType.INVISIBLE.getDescription())
			);
	
	public static final ItemStack VISIBLE_ITEM = ItemStackUtil.make(Material.WHITE_STAINED_GLASS_PANE,
			Component.text("InviSneak - ",NamedTextColor.GOLD).append(Component.text("Visible", NamedTextColor.WHITE)),
			LORE);
	
	public static final ItemStack INVISIBLE_ITEM = ItemStackUtil.make(Material.GLASS_PANE,
			Component.text("InviSneak - ",NamedTextColor.GOLD).append(Component.text("Invisible", NamedTextColor.GRAY)),
			LORE);
	
	public static final ItemStack REVEALED_ITEM = ItemStackUtil.make(Material.RED_STAINED_GLASS_PANE,
			Component.text("InviSneak - ",NamedTextColor.GOLD).append(Component.text("Découvert", NamedTextColor.RED)),
			LORE);
	
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
		for (var ennemi : wrap.ennemiTeam()) {
			if (player.getLocation().distanceSquared(ennemi.getLocation()) <= DETECTION_RANGE_SQUARED) {
				return true;
			}
		}
		return false;
	}
	
	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		var wrappingModule = NekotineCore.MODULES.get(WrappingModule.class);
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
	
}
