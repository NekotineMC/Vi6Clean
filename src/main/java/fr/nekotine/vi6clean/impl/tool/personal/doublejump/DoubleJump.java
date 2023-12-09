package fr.nekotine.vi6clean.impl.tool.personal.doublejump;

import org.bukkit.Material;
import org.bukkit.configuration.Configuration;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.constant.Vi6ToolLoreText;
import fr.nekotine.vi6clean.impl.tool.Tool;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class DoubleJump extends Tool{
	
	private boolean canDoubleJump;
	
	private double power = Ioc.resolve(Configuration.class).getDouble("tool.double_jump.power", 0.5d);
	
	@Override
	protected ItemStack makeInitialItemStack() {
		return ItemStackUtil.make(Material.GOLDEN_BOOTS, Component.text("Double Saut", NamedTextColor.GOLD), Vi6ToolLoreText.DOUBLEJUMP.make());
	}
	
	@Override
	protected void cleanup() {
	}

	public boolean canDoubleJump() {
		return canDoubleJump;
	}
	
	public void setCanDoubleJump(boolean canDoubleJump) {
		this.canDoubleJump = canDoubleJump;
		getOwner().setAllowFlight(canDoubleJump);
	}
	
	public void doubleJump() {
		var player = getOwner();
		var loc = player.getLocation();
		player.setVelocity(player.getVelocity().setY(power));
		Vi6Sound.DOUBLE_JUMP.play(loc.getWorld(), loc);
		setCanDoubleJump(false);
	}
	
	public boolean isOnGround() {
		var player = getOwner();
		return (!player.isFlying()
				&& player.getLocation().subtract(0.0D, 0.1D, 0.0D).getBlock().getType().isSolid());
	}

	//

	@Override
	protected void onEmpStart() {
	}
	@Override
	protected void onEmpEnd() {
	}
}
