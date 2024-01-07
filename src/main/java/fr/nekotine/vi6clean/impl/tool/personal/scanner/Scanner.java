package fr.nekotine.vi6clean.impl.tool.personal.scanner;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.inventory.ItemStackBuilder;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.vi6clean.impl.tool.Tool;

public class Scanner extends Tool{

	@Override
	protected ItemStack makeInitialItemStack() {
		var handler = Ioc.resolve(ScannerHandler.class);
		return new ItemStackBuilder(Material.CLOCK)
		.name(handler.getDisplayName())
		.lore(handler.getLore())
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
