package fr.nekotine.vi6clean.impl.tool.personal.parabolic_mic;

import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.vi6clean.impl.tool.Tool;

public class ParabolicMic extends Tool{
	
	private Entity vibrationTargetEntity;
	
	@Override
	protected ItemStack makeInitialItemStack() {
		var handler = Ioc.resolve(ParabolicMicHandler.class);
		return ItemStackUtil.make(
				Material.CALIBRATED_SCULK_SENSOR,
				handler.getDisplayName(),
				handler.getLore());
	}

	@Override
	protected void cleanup() {
	}

	public Entity getVibrationTargetEntity() {
		return vibrationTargetEntity;
	}

	public void setVibrationTargetEntity(Entity vibrationTargetEntity) {
		this.vibrationTargetEntity = vibrationTargetEntity;
	}

	//

	@Override
	protected void onEmpStart() {
	}
	@Override
	protected void onEmpEnd() {
	}
}
