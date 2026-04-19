package fr.nekotine.vi6clean.impl.tool.personal.speed;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.util.InventoryUtil;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpEndEvent;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpStartEvent;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

@ToolCode("speed")
public class SpeedHandler extends ToolHandler<SpeedHandler.SpeedBuff> {

	private final double SPEED_BONUS = getConfiguration().getDouble("speed_bonus", 0.02); // 0.1 is the default
																							// mouvement speed

	public SpeedHandler() {
		super(SpeedBuff::new);
	}

	@Override
	protected void onAttachedToPlayer(SpeedBuff tool) {
		var player = tool.getOwner();
		player.getAttribute(Attribute.MOVEMENT_SPEED)
				.addModifier(new AttributeModifier(
						NamespacedKey.fromString(getToolCode() + '/' + tool.getId(), Ioc.resolve(JavaPlugin.class)),
						SPEED_BONUS, Operation.ADD_NUMBER));
	}

	@Override
	protected void onDetachFromPlayer(SpeedBuff tool) {
		var player = tool.getOwner();
		player.getAttribute(Attribute.MOVEMENT_SPEED).removeModifier(
				NamespacedKey.fromString(getToolCode() + '/' + tool.getId(), Ioc.resolve(JavaPlugin.class)));
	}

	@Override
	protected void onToolCleanup(SpeedBuff tool) {
	}

	@Override
	protected ItemStack makeItem(SpeedBuff tool) {
		return ItemStackUtil.make(Material.GOLDEN_NAUTILUS_ARMOR, getDisplayName(), getLore());
	}

	@EventHandler
	private void onEmpStart(EntityEmpStartEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				item.setData(DataComponentTypes.ITEM_MODEL, Material.NETHERITE_NAUTILUS_ARMOR.key());
				item.editMeta(m -> m.displayName(getDisplayName().decorate(TextDecoration.STRIKETHROUGH)
						.append(Component.text(" - ")).append(Component.text("Brouillé", NamedTextColor.RED))));
				onDetachFromPlayer(getToolFromItem(item));
			});
		}
	}

	@EventHandler
	private void onEmpStop(EntityEmpEndEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				item.resetData(DataComponentTypes.ITEM_MODEL); // back to default model
				item.editMeta(m -> m.displayName(getDisplayName()));
				onAttachedToPlayer(getToolFromItem(item));
			});
		}
	}

	public static class SpeedBuff extends Tool {

		public SpeedBuff(ToolHandler<?> handler) {
			super(handler);
		}
	}
}
