package fr.nekotine.vi6clean.impl.tool.personal.doublejump;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;

import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;

@ToolCode("double_jump")
public class DoubleJumpHandler extends ToolHandler<DoubleJump>{

	public DoubleJumpHandler() {
		super(DoubleJump::new);
	}
	
	@Override
	protected void onAttachedToPlayer(DoubleJump tool, Player player) {
	}

	@Override
	protected void onDetachFromPlayer(DoubleJump tool, Player player) {
		var gm = player.getGameMode();
		if (gm == GameMode.ADVENTURE || gm == GameMode.SURVIVAL) {
			player.setAllowFlight(false);
		}
	}
	
	@EventHandler
	private void onPlayerMove(PlayerMoveEvent evt) {
		for (var tool : getTools()) {
			if (tool.canDoubleJump()) {
				continue;
			}
			if (evt.getPlayer().equals(tool.getOwner())) {
				tool.setCanDoubleJump(tool.isOnGround());
				return;
			}
		}
	}
	
	@EventHandler
	private void onPlayerToggleFlight(PlayerToggleFlightEvent evt) {
		for (var tool : getTools()) {
			if (!tool.canDoubleJump()) {
				continue;
			}
			if (evt.getPlayer().equals(tool.getOwner())) {
				tool.doubleJump();
				evt.setCancelled(true);
				return;
			}
		}
	}
	
}
