package fr.nekotine.vi6clean.impl.tool.personal.forcefield;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.vi6clean.impl.tool.Tool;
import net.kyori.adventure.text.Component;

public class Forcefield extends Tool{

	@Override
	protected ItemStack makeInitialItemStack() {
		return ItemStackUtil.make(Material.IRON_DOOR, Component.text("Champ de force"));
	}

	@Override
	protected void cleanup() {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void onEmpStart() {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void onEmpEnd() {
		// TODO Auto-generated method stub
		
	}
}
