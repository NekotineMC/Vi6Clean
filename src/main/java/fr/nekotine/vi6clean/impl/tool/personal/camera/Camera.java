package fr.nekotine.vi6clean.impl.tool.personal.camera;

import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.vi6clean.impl.tool.Tool;

public class Camera extends Tool{
	@Override
	protected ItemStack makeInitialItemStack() {
		Ioc.resolve(CameraHandler.class).addPlayerCharges();
		return Ioc.resolve(CameraHandler.class).ITEM;
	}
	@Override
	protected void cleanup() {
		Ioc.resolve(CameraHandler.class).removePlayerCharges();
	}
	@Override
	protected void onEmpStart() {
	}
	@Override
	protected void onEmpEnd() {
	}
}
