package fr.nekotine.vi6clean.impl.tool.personal.doublejump;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

@ToolCode("double_jump")
public class DoubleJumpHandler extends ToolHandler<DoubleJump>{
	private final ItemStack ITEM = ItemStackUtil.make(
			Material.GOLDEN_BOOTS, 
			getDisplayName(), 
			getLore());
	private final ItemStack EMP_ITEM = ItemStackUtil.make(
			Material.CHAINMAIL_BOOTS, 
			getDisplayName().append(Component.text(" - ")).append(Component.text("Brouillé" , NamedTextColor.RED)), 
			getLore());
	private final double POWER = getConfiguration().getDouble("power", 0.5);
	
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
	
	protected double getPower() {
		return POWER;
	}
	protected ItemStack getItem() {
		return ITEM;
	}
	protected ItemStack getEmpItem() {
		return EMP_ITEM;
	}
	
}
