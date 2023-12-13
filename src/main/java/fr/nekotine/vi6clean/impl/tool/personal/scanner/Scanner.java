package fr.nekotine.vi6clean.impl.tool.personal.scanner;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.inventory.ItemStackBuilder;
import fr.nekotine.vi6clean.impl.tool.Tool;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class Scanner extends Tool{

	@Override
	protected ItemStack makeInitialItemStack() {
		return new ItemStackBuilder(Material.CLOCK)
		.name(Component.text("Scanner",NamedTextColor.GOLD))
		.lore(ScannerHandler.LORE)
		.unstackable()
		.flags(ItemFlag.values())
		.build();
	}

	@Override
	protected void cleanup() {
	}

	//

	@Override
	protected void onEmpStart() {
	}
	@Override
	protected void onEmpEnd() {
	}
}
