package fr.nekotine.vi6clean.tool.personal.parabolic_mic;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Color;
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
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.InventoryUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.game.Vi6Game;
import fr.nekotine.vi6clean.status.event.EntityEmpEndEvent;
import fr.nekotine.vi6clean.status.event.EntityEmpStartEvent;
import fr.nekotine.vi6clean.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.tool.Tool;
import fr.nekotine.vi6clean.tool.ToolCode;
import fr.nekotine.vi6clean.tool.ToolHandler;
import fr.nekotine.vi6clean.wrapper.PlayerWrapper;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

@ToolCode("parabolic_mic")
public class ParabolicMicHandler extends ToolHandler<ParabolicMicHandler.ParabolicMic> {

	private final NamespacedKey transmitTrackingRangeAttributeKey = NamespacedKey
			.fromString("parabolic_mic/transmit_range", Ioc.resolve(JavaPlugin.class));

	private final NamespacedKey guardWaypointStyleKey = NamespacedKey.fromString("guard",
			Ioc.resolve(JavaPlugin.class));

	public ParabolicMicHandler() {
		super(ParabolicMic::new);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
	}

	private final double DETECTION_BLOCK_RANGE = getConfiguration().getDouble("range");

	private final Map<Player, ArmorStand> emitters = new HashMap<>();

	private final Map<Player, Double> playerSpeedCache = new HashMap<>();

	@EventHandler
	private void onPlayerMove(PlayerMoveEvent evt) {
		var player = evt.getPlayer();
		playerSpeedCache.put(player, evt.getTo().distance(evt.getFrom()));
	}

	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		var statusModule = Ioc.resolve(StatusFlagModule.class);
		var empFlag = EmpStatusFlag.get();
		var wrappingModule = Ioc.resolve(WrappingModule.class);
		var plugin = Ioc.resolve(JavaPlugin.class);
		for (var player : Ioc.resolve(Vi6Game.class).getPlayerList()) {
			var emi = emitters.computeIfAbsent(player, (p) -> (ArmorStand) p.getWorld().spawnEntity(p.getLocation(),
					EntityType.ARMOR_STAND, SpawnReason.CUSTOM, e -> {
						e.setPersistent(false);
						e.setVisibleByDefault(false);
						if (e instanceof ArmorStand stand) {
							stand.setMarker(true);
							stand.setInvisible(true);
							stand.setWaypointStyle(guardWaypointStyleKey);
							stand.setWaypointColor(Color.NAVY);
						}
						wrappingModule.getWrapper(p, PlayerWrapper.class).enemyTeam().forEach(en -> {
							if (!statusModule.hasAny(en, empFlag) && InventoryUtil.containTaggedItem(en.getInventory(),
									TOOL_TYPE_KEY, getToolCode())) {
								en.showEntity(plugin, e);
							}
						});
					}));
			emi.teleport(player);
			var attribute = emi.getAttribute(Attribute.WAYPOINT_TRANSMIT_RANGE);
			attribute.removeModifier(transmitTrackingRangeAttributeKey);

			var spd = playerSpeedCache.getOrDefault(player, 0d);
			playerSpeedCache.put(player, 0d);
			var range = spd > 0 ? DETECTION_BLOCK_RANGE : 0;
			if (player.isSneaking()) {
				range /= 2;
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
		emitters.clear();
	}

	@Override
	protected void onAttachedToPlayer(ParabolicMic tool) {
		var plugin = Ioc.resolve(JavaPlugin.class);
		var wrappingModule = Ioc.resolve(WrappingModule.class);
		var player = tool.getOwner();
		wrappingModule.getWrapper(player, PlayerWrapper.class).enemyTeam().forEach(en -> {
			if (emitters.containsKey(en)) {
				player.showEntity(plugin, emitters.get(en));
			}
		});
	}

	@Override
	protected void onDetachFromPlayer(ParabolicMic tool) {
		var plugin = Ioc.resolve(JavaPlugin.class);
		var player = tool.getOwner();
		for (var value : emitters.values()) {
			player.hideEntity(plugin, value);
		}
	}

	@Override
	protected void onToolCleanup(ParabolicMic tool) {
	}

	@EventHandler
	private void onEmpStart(EntityEmpStartEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				item.setData(DataComponentTypes.ITEM_MODEL, Material.SCULK.key());
				item.editMeta(m -> m.displayName(getDisplayName().decorate(TextDecoration.STRIKETHROUGH)
						.append(Component.text(" - ")).append(Component.text("Brouillé", NamedTextColor.RED))));
			});
			var plugin = Ioc.resolve(JavaPlugin.class);
			for (var emi : emitters.values()) {
				p.hideEntity(plugin, emi);
			}
		}
	}

	@EventHandler
	private void onEmpStop(EntityEmpEndEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				item.resetData(DataComponentTypes.ITEM_MODEL); // back to default model
				item.editMeta(m -> m.displayName(getDisplayName()));
			});
			var plugin = Ioc.resolve(JavaPlugin.class);
			var wrappingModule = Ioc.resolve(WrappingModule.class);
			for (var entry : emitters.entrySet()) {
				var source = entry.getKey();
				var emi = entry.getValue();
				if (wrappingModule.getWrapper(source, PlayerWrapper.class).enemyTeam().contains(p)) {
					p.showEntity(plugin, emi);
				}
			}
		}
	}

	public static class ParabolicMic extends Tool {

		public ParabolicMic(ToolHandler<?> handler) {
			super(handler);
		}
	}
}
