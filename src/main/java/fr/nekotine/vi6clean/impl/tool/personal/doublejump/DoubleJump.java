package fr.nekotine.vi6clean.impl.tool.personal.doublejump;

import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.util.EntityUtil;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.impl.tool.Tool;

public class DoubleJump extends Tool{
	
	private boolean canDoubleJump;
	
	private boolean emp;
	
	private double power = Ioc.resolve(DoubleJumpHandler.class).getPower();
	
	@Override
	protected ItemStack makeInitialItemStack() {
		return Ioc.resolve(DoubleJumpHandler.class).getItem();
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
		Vi6Sound.DOUBLE_JUMP.play(player, loc);
		setCanDoubleJump(false);
	}
	
	public boolean isOnGround() {
		return !getOwner().isFlying() && EntityUtil.IsOnGround(getOwner());
	}

	//

	@Override
	protected void onEmpStart() {
		setItemStack(Ioc.resolve(DoubleJumpHandler.class).getEmpItem());
		emp = true;
	}
	@Override
	protected void onEmpEnd() {
		setItemStack(Ioc.resolve(DoubleJumpHandler.class).getItem());
		emp = false;
	}
}
