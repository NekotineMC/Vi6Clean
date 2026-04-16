package fr.nekotine.vi6clean.impl.tool.personal.doublejump;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.util.EntityUtil;
import fr.nekotine.core.util.InventoryUtil;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpEndEvent;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpStartEvent;
import fr.nekotine.vi6clean.impl.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

@ToolCode("double_jump")
public class DoubleJumpHandler extends ToolHandler<DoubleJump> {

	private final NamespacedKey bootNoArmorAttributeKey = NamespacedKey.fromString("double_jump/remove_armor",
			Ioc.resolve(JavaPlugin.class));

	private final double POWER = getConfiguration().getDouble("power", 0.5);

	public DoubleJumpHandler() {
		super(DoubleJump::new);
	}

	@EventHandler
	private void onPlayerMove(PlayerMoveEvent evt) {
		for (var tool : getTools()) {
			var owner = tool.getOwner();
			if (evt.getPlayer().equals(owner) && !owner.getAllowFlight() && isOnGround(tool.getOwner())) {
				owner.setAllowFlight(true);
				return;
			}
		}
	}

	@EventHandler
	private void onPlayerToggleFlight(PlayerToggleFlightEvent evt) {
		var player = evt.getPlayer();
		if (Ioc.resolve(StatusFlagModule.class).hasAny(player, EmpStatusFlag.get())) {
			return;
		}
		if (InventoryUtil.containTaggedItem(player.getInventory(), TOOL_TYPE_KEY, getToolCode())) {
			player.setVelocity(player.getVelocity().setY(POWER));
			Vi6Sound.DOUBLE_JUMP.play(player, player.getLocation());
			player.setAllowFlight(false);
			evt.setCancelled(true);
		}
	}

	public boolean isOnGround(Player p) {
		return !p.isFlying() && EntityUtil.IsOnGround((Entity) p) && p.getFallDistance() <= 0;
	}

	@Override
	protected void onAttachedToPlayer(DoubleJump tool) {
		var player = tool.getOwner();
		var boots = ItemStackUtil.make(Material.GOLDEN_BOOTS, getDisplayName(), getLore());
		boots.addEnchantment(Enchantment.BINDING_CURSE, 1);
		boots.addItemFlags(ItemFlag.values());
		boots.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.itemAttributes().addModifier(
				Attribute.ARMOR, new AttributeModifier(bootNoArmorAttributeKey, -1, Operation.MULTIPLY_SCALAR_1)));
		boots.unsetData(DataComponentTypes.DAMAGE);
		boots.unsetData(DataComponentTypes.MAX_DAMAGE);
		player.getInventory().setBoots(boots);
	}

	@Override
	protected void onDetachFromPlayer(DoubleJump tool) {
		var owner = tool.getOwner();
		owner.getInventory().setBoots(null);
		var gm = owner.getGameMode();
		if (gm == GameMode.ADVENTURE || gm == GameMode.SURVIVAL) {
			owner.setAllowFlight(false);
		}
	}

	@Override
	protected void onToolCleanup(DoubleJump tool) {
	}

	@Override
	protected ItemStack makeItem(DoubleJump tool) {
		return ItemStackUtil.make(Material.GOLDEN_BOOTS, getDisplayName(), getLore());
	}

	@EventHandler
	private void onEmpStart(EntityEmpStartEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				var boots = p.getInventory().getBoots();
				item.setData(DataComponentTypes.ITEM_MODEL, Material.CHAINMAIL_BOOTS.key());
				boots.setData(DataComponentTypes.ITEM_MODEL, Material.CHAINMAIL_BOOTS.key());
				item.editMeta(m -> m.displayName(getDisplayName().decorate(TextDecoration.STRIKETHROUGH)
						.append(Component.text(" - ")).append(Component.text("Brouillé", NamedTextColor.RED))));
				boots.editMeta(m -> m.displayName(getDisplayName().decorate(TextDecoration.STRIKETHROUGH)
						.append(Component.text(" - ")).append(Component.text("Brouillé", NamedTextColor.RED))));
			});
		}
	}

	@EventHandler
	private void onEmpStop(EntityEmpEndEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				var boots = p.getInventory().getBoots();
				item.resetData(DataComponentTypes.ITEM_MODEL); // back to default model
				boots.resetData(DataComponentTypes.ITEM_MODEL);
				item.editMeta(m -> m.displayName(getDisplayName()));
				boots.editMeta(m -> m.displayName(getDisplayName()));
			});
		}
	}
}
