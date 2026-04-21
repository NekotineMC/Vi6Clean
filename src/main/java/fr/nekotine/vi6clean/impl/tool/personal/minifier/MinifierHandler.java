package fr.nekotine.vi6clean.impl.tool.personal.minifier;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.plugin.java.JavaPlugin;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.util.InventoryUtil;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpEndEvent;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpStartEvent;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

@ToolCode("minifier")
public class MinifierHandler extends ToolHandler<MinifierHandler.Minifier> {

	public static final NamespacedKey SCALE_ATTRIBUTE_KEY = NamespacedKey.fromString("minifier/scale",
			Ioc.resolve(JavaPlugin.class));

	private final NamespacedKey gravityAttributeKey = NamespacedKey.fromString("minifier/gravity",
			Ioc.resolve(JavaPlugin.class));

	private final NamespacedKey healthAttributeKey = NamespacedKey.fromString("minifier/health",
			Ioc.resolve(JavaPlugin.class));

	private final double SCALE_MULTIPLIER = getConfiguration().getDouble("scale_multiplier", 0.5);

	private final double GRAVITY_MULTIPLIER = getConfiguration().getDouble("gravity_multiplier", 0.5);

	private final double HEALTH_MULTIPLIER = getConfiguration().getDouble("max_health_multiplier", 0.5);

	public MinifierHandler() {
		super(Minifier::new);
	}

	@Override
	protected void onAttachedToPlayer(Minifier tool) {
		var player = tool.getOwner();
		player.getAttribute(Attribute.SCALE).addModifier(
				new AttributeModifier(SCALE_ATTRIBUTE_KEY, SCALE_MULTIPLIER - 1, Operation.MULTIPLY_SCALAR_1));
		player.getAttribute(Attribute.GRAVITY).addModifier(
				new AttributeModifier(gravityAttributeKey, GRAVITY_MULTIPLIER - 1, Operation.MULTIPLY_SCALAR_1));
		player.getAttribute(Attribute.MAX_HEALTH).addModifier(
				new AttributeModifier(healthAttributeKey, HEALTH_MULTIPLIER - 1, Operation.MULTIPLY_SCALAR_1));
	}

	@Override
	protected void onDetachFromPlayer(Minifier tool) {
		var player = tool.getOwner();
		player.getAttribute(Attribute.SCALE).removeModifier(SCALE_ATTRIBUTE_KEY);
		player.getAttribute(Attribute.GRAVITY).removeModifier(gravityAttributeKey);
		player.getAttribute(Attribute.MAX_HEALTH).removeModifier(healthAttributeKey);
	}

	@Override
	protected void onToolCleanup(Minifier tool) {
	}

	@EventHandler
	private void onEmpStart(EntityEmpStartEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				item.setData(DataComponentTypes.ITEM_MODEL, Material.COPPER_GOLEM_STATUE.key());
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

	public static class Minifier extends Tool {

		public Minifier(ToolHandler<?> handler) {
			super(handler);
		}
	}
}
