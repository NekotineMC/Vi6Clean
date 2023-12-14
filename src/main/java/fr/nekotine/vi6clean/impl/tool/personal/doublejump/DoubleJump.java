package fr.nekotine.vi6clean.impl.tool.personal.doublejump;

import org.bukkit.configuration.Configuration;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.impl.tool.Tool;

public class DoubleJump extends Tool{
	
	private boolean canDoubleJump;
	
	private boolean emp;
	
	private double power = Ioc.resolve(Configuration.class).getDouble("tool.double_jump.power", 0.5d);
	
	@Override
	protected ItemStack makeInitialItemStack() {
		return DoubleJumpHandler.ITEM;
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
		if (emp) {
			return;
		}
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
		setItemStack(DoubleJumpHandler.EMP_ITEM);
		emp = true;
	}
	@Override
	protected void onEmpEnd() {
		setItemStack(DoubleJumpHandler.ITEM);
		emp = false;
	}
}
