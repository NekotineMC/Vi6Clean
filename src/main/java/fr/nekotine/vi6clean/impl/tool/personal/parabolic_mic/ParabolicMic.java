package fr.nekotine.vi6clean.impl.tool.personal.parabolic_mic;

import org.bukkit.Material;
import org.bukkit.configuration.Configuration;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.vi6clean.constant.Vi6ToolLoreText;
import fr.nekotine.vi6clean.impl.tool.Tool;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public class ParabolicMic extends Tool{
	
	private Entity vibrationTargetEntity;
	
	@Override
	protected ItemStack makeInitialItemStack() {
		return ItemStackUtil.make(Material.CALIBRATED_SCULK_SENSOR,
				Component.text("Micro parabolique",NamedTextColor.GOLD),
						Vi6ToolLoreText.INVISNEAK.make(
								Placeholder.unparsed("range", Ioc.resolve(Configuration.class).getDouble("tool.parabolic_mic.range", 20)+" blocs")
								));
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
}
