package fr.nekotine.vi6clean.impl.tool.personal.bush;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.vi6clean.constant.Vi6ToolLoreText;
import fr.nekotine.vi6clean.impl.tool.Tool;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class Bush extends Tool{
	
	private boolean inBush;
	
	private boolean revealed;
	
	private final ItemStack VISIBLE_ITEM = ItemStackUtil.make(Material.WHITE_STAINED_GLASS_PANE,
			Component.text("Buisson furtif - ",NamedTextColor.GOLD).append(Component.text("Visible", NamedTextColor.WHITE)),
			Vi6ToolLoreText.BUSH.make());
	
	private final ItemStack INVISIBLE_ITEM = ItemStackUtil.make(Material.GLASS_PANE,
			Component.text("Buisson furtif - ",NamedTextColor.GOLD).append(Component.text("Invisible", NamedTextColor.GRAY)),
			Vi6ToolLoreText.BUSH.make());
	
	private final ItemStack REVEALED_ITEM = ItemStackUtil.make(Material.RED_STAINED_GLASS_PANE,
			Component.text("Buisson furtif - ",NamedTextColor.GOLD).append(Component.text("Découvert", NamedTextColor.RED)),
			Vi6ToolLoreText.BUSH.make());
	
	@Override
	protected ItemStack makeInitialItemStack() {
		return VISIBLE_ITEM;
	}
	
	public void updateStatus() {
		if (inBush) {
			if (revealed) {
				
			}else {
				
			}
		}else {
			//TODO HERE
		}
	}

	@Override
	protected void cleanup() {
		
	}
}
