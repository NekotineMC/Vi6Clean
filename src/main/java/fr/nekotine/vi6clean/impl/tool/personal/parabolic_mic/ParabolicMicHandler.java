package fr.nekotine.vi6clean.impl.tool.personal.parabolic_mic;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.plugin.java.JavaPlugin;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.EntityUtil;
import fr.nekotine.core.util.InventoryUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpEndEvent;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpStartEvent;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

@ToolCode("parabolic_mic")
public class ParabolicMicHandler extends ToolHandler<ParabolicMicHandler.ParabolicMic> {

	private final NamespacedKey receptionTrackingRangeAttributeKey = NamespacedKey
			.fromString("parabolic_mic/reception_range", Ioc.resolve(JavaPlugin.class));

	private final NamespacedKey transmitTrackingRangeAttributeKey = NamespacedKey
			.fromString("parabolic_mic/transmit_range", Ioc.resolve(JavaPlugin.class));

	public ParabolicMicHandler() {
		super(ParabolicMic::new);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
	}

	private final double DETECTION_BLOCK_RANGE = getConfiguration().getDouble("range", 20d);

	private final Map<Player, ArmorStand> emitters = new HashMap<>();

	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		for (var player : Ioc.resolve(Vi6Game.class).getPlayerList()) {
			var emi = emitters.computeIfAbsent(player, (p) -> (ArmorStand) p.getWorld().spawnEntity(p.getLocation(),
					EntityType.ARMOR_STAND, SpawnReason.CUSTOM, e -> {
						e.setPersistent(false);
						e.setVisibleByDefault(false);
						if (e instanceof ArmorStand stand) {
							stand.setMarker(true);
							stand.setInvisible(true);
						}
						Ioc.resolve(WrappingModule.class).getWrapper(p, PlayerWrapper.class).enemyTeam().forEach(en -> {
							en.showEntity(Ioc.resolve(JavaPlugin.class), e);
						});
					}));
			emi.teleport(player);
			var attribute = emi.getAttribute(Attribute.WAYPOINT_TRANSMIT_RANGE);
			attribute.removeModifier(transmitTrackingRangeAttributeKey);

			var spd = player.getVelocity().length();
			var range = DETECTION_BLOCK_RANGE;
			if (player.isSneaking()) {
				range /= 2;
			}
			if (!EntityUtil.IsOnGround(player)) {
				range = 0;
			}

			attribute
					.addModifier(new AttributeModifier(transmitTrackingRangeAttributeKey, range, Operation.ADD_NUMBER));
		}
	}

	@Override
	protected void onStopHandling() {
		for (var e : emitters.values()) {
			if (e != null) {
				e.remove();
			}
		}
	}

	@Override
	protected void onAttachedToPlayer(ParabolicMic tool) {
		var player = tool.getOwner();
		player.getAttribute(Attribute.WAYPOINT_RECEIVE_RANGE).addModifier(
				new AttributeModifier(receptionTrackingRangeAttributeKey, DETECTION_BLOCK_RANGE, Operation.ADD_NUMBER));
	}

	@Override
	protected void onDetachFromPlayer(ParabolicMic tool) {
		var player = tool.getOwner();
		player.getAttribute(Attribute.SCALE).removeModifier(receptionTrackingRangeAttributeKey);
	}

	@Override
	protected void onToolCleanup(ParabolicMic tool) {
	}

	@EventHandler
	private void onEmpStart(EntityEmpStartEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				item.setData(DataComponentTypes.ITEM_MODEL, Material.SCULK_SENSOR.key());
				item.editMeta(m -> m.displayName(getDisplayName().decorate(TextDecoration.STRIKETHROUGH)
						.append(Component.text(" - ")).append(Component.text("Brouillé", NamedTextColor.RED))));
				p.getAttribute(Attribute.SCALE).removeModifier(receptionTrackingRangeAttributeKey);
			});
		}
	}

	@EventHandler
	private void onEmpStop(EntityEmpEndEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				item.resetData(DataComponentTypes.ITEM_MODEL); // back to default model
				item.editMeta(m -> m.displayName(getDisplayName()));
				p.getAttribute(Attribute.WAYPOINT_RECEIVE_RANGE).addModifier(new AttributeModifier(
						receptionTrackingRangeAttributeKey, DETECTION_BLOCK_RANGE, Operation.ADD_NUMBER));
			});
		}
	}

	public static class ParabolicMic extends Tool {

		public ParabolicMic(ToolHandler<?> handler) {
			super(handler);
		}
	}
}
