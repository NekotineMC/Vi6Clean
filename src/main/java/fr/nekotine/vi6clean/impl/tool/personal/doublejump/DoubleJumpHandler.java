package fr.nekotine.vi6clean.impl.tool.personal.doublejump;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.vi6clean.constant.Vi6ToolLoreText;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

@ToolCode("double_jump")
public class DoubleJumpHandler extends ToolHandler<DoubleJump>{
	protected static final ItemStack ITEM = ItemStackUtil.make(Material.GOLDEN_BOOTS, Component.text("Double Saut", NamedTextColor.GOLD), Vi6ToolLoreText.DOUBLEJUMP.make());
	protected static final ItemStack EMP_ITEM = ItemStackUtil.make(Material.CHAINMAIL_BOOTS, Component.text("Double Saut", NamedTextColor.GOLD).append(Component.text(" - ")).append(Component.text("Brouillé" , NamedTextColor.RED)), Vi6ToolLoreText.DOUBLEJUMP.make());

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
