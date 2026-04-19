package fr.nekotine.vi6clean.impl.tool.personal.radar;

import fr.nekotine.core.inventory.ItemStackBuilder;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.text.TextModule;
import fr.nekotine.core.text.style.NekotineStyles;
import fr.nekotine.core.text.tree.Leaf;
import fr.nekotine.core.ticking.TickTimeStamp;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EntityUtil;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.util.InventoryUtil;
import fr.nekotine.core.util.SpatialUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpEndEvent;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpStartEvent;
import fr.nekotine.vi6clean.impl.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.UseCooldown;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

@ToolCode("radar")
public class RadarHandler extends ToolHandler<RadarHandler.Radar> {

	private final double DETECTION_BLOCK_RANGE = getConfiguration().getDouble("range", 20);
	private final double DETECTION_RANGE_SQUARED = DETECTION_BLOCK_RANGE * DETECTION_BLOCK_RANGE;
	private final int DELAY_TICK = (int) (20 * getConfiguration().getDouble("delay", 5));
	private final int COOLDOWN_TICK = (int) (20 * getConfiguration().getDouble("cooldown", 20));
	private final String DETECTION_SUCCESS = getConfiguration().getString("detection_success");
	private final String DETECTION_FAIL = getConfiguration().getString("detection_fail");
	private final String DETECTION_DETECTED = getConfiguration().getString("detection_detected");

	private static final Transformation TOP_TRANSFORMATION = new Transformation(new Vector3f(0, 0, 0),
			new AxisAngle4f((float) Math.PI, new Vector3f(0, 1, 0)), new Vector3f(1, 1, 1),
			new AxisAngle4f((float) Math.PI, new Vector3f(0, 1, 0)));

	public RadarHandler() {
		super(Radar::new);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
	}

	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		var wrappingModule = Ioc.resolve(WrappingModule.class);
		var textModule = Ioc.resolve(TextModule.class);
		for (var tool : getTools()) {
			var owner = tool.getOwner();
			// TICK CHARGE
			if (tool.top != null) {
				if (--tool.chargeTime <= 0) {
					var opt = wrappingModule.getWrapperOptional(owner, PlayerWrapper.class);
					var ennemiNear = opt.get().ennemiTeamInMap().filter(
							e -> tool.top.getLocation().distanceSquared(e.getLocation()) <= DETECTION_RANGE_SQUARED)
							.collect(Collectors.toList());
					var ennemiNearCount = ennemiNear.size();

					// Son
					if (ennemiNearCount > 0) {
						Vi6Sound.RADAR_POSITIVE.play(tool.bottom.getWorld(), tool.bottom.getLocation());
					} else {
						Vi6Sound.RADAR_NEGATIVE.play(tool.bottom.getWorld(), tool.bottom.getLocation());
					}

					// Message
					owner.sendMessage(
							textModule
									.message(Leaf.builder()
											.addStyle(Placeholder.unparsed("number", String.valueOf(ennemiNearCount)))
											.addStyle(NekotineStyles.STANDART)
											.addLine(ennemiNearCount > 0 ? DETECTION_SUCCESS : DETECTION_FAIL))
									.buildFirst());
					ennemiNear.forEach(p -> p.sendMessage(textModule
							.message(Leaf.builder().addStyle(NekotineStyles.STANDART).addLine(DETECTION_DETECTED))
							.buildFirst()));

					// Particules
					Location loc = tool.bottom.getLocation();
					var x = loc.getX();
					var y = loc.getY();
					var z = loc.getZ();

					SpatialUtil.ball3DDensity(DETECTION_BLOCK_RANGE, 0.1f, SpatialUtil.SphereAlgorithm.FIBONACCI,
							(offsetX, offsetY, offsetZ) -> {
								loc.getWorld().spawnParticle((ennemiNearCount > 0 ? Particle.COMPOSTER : Particle.DUST),
										x + offsetX, y + offsetY, z + offsetZ, 1, 0, 0, 0, 0,
										(ennemiNearCount > 0 ? null : new DustOptions(Color.RED, 2)));
							});

					editItem(tool, item -> {
						owner.setCooldown(item, COOLDOWN_TICK);
						item.unsetData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE);
					});

					onToolCleanup(tool);
				}
			}

			if (evt.timeStampReached(TickTimeStamp.QuartSecond))
				if (tool.top != null) {
					Location loc = tool.bottom.getLocation();
					var x = loc.getX();
					var y = loc.getY();
					var z = loc.getZ();
					SpatialUtil.ball3DDensity(DETECTION_BLOCK_RANGE, 0.1f, SpatialUtil.SphereAlgorithm.FIBONACCI,
							(offsetX, offsetY, offsetZ) -> loc.getWorld().spawnParticle(Particle.WITCH, x + offsetX,
									y + offsetY, z + offsetZ, 1, 0, 0, 0, 0, null));
				} else if (owner.isSneaking() && itemMatch(tool, owner.getInventory().getItemInMainHand())) {
					Location loc = owner.getLocation();
					var x = loc.getX();
					var y = loc.getY();
					var z = loc.getZ();
					SpatialUtil.sphere3DDensity(DETECTION_BLOCK_RANGE, 0.1f, SpatialUtil.SphereAlgorithm.FIBONACCI,
							(offsetX, offsetY, offsetZ) -> owner.spawnParticle(Particle.WITCH, x + offsetX, y + offsetY,
									z + offsetZ, 1, 0, 0, 0, 0, null));
				}
			if (evt.timeStampReached(TickTimeStamp.Second)) {
				if (tool.top != null) {
					// Faire un son custom
					Vi6Sound.RADAR_SCAN.play(tool.bottom.getWorld(), tool.bottom.getLocation());
				}
			}
		}
	}

	@EventHandler
	private void onPlayerInterract(PlayerInteractEvent evt) {
		if (evt.getHand() != EquipmentSlot.HAND && !EventUtil.isCustomAction(evt, CustomAction.HIT_ANY)) {
			return;
		}
		var player = evt.getPlayer();
		var tool = getToolFromItem(evt.getItem());
		if (tool == null || player.getCooldown(
				NamespacedKey.fromString(getToolCode() + '/' + tool.getId(), Ioc.resolve(JavaPlugin.class))) > 0) {
			return;
		}

		// TRY PLACE

		Location ploc = player.getLocation();
		var flagModule = Ioc.resolve(StatusFlagModule.class);
		if (tool.top != null || !EntityUtil.IsOnGround(player) || flagModule.hasAny(player, EmpStatusFlag.get())) {
			return;
		}

		var rot = player.getEyeLocation().getYaw() + 180;
		tool.bottom = (ItemDisplay) player.getWorld().spawnEntity(ploc.add(0, 0.6, 0), EntityType.ITEM_DISPLAY,
				SpawnReason.CUSTOM, e -> {
					if (e instanceof ItemDisplay dis) {
						dis.setRotation(rot, 0);
						dis.setItemStack(ItemStack.of(Material.ORANGE_SHULKER_BOX));
					}
				});
		tool.middle = (ItemDisplay) player.getWorld().spawnEntity(ploc.add(0, 1, 0), EntityType.ITEM_DISPLAY,
				SpawnReason.CUSTOM, e -> {
					if (e instanceof ItemDisplay dis) {
						dis.setRotation(rot, 0);
						dis.setItemStack(ItemStack.of(Material.LIGHTNING_ROD));
					}
				});
		tool.top = (ItemDisplay) player.getWorld().spawnEntity(ploc.add(0, 0.8, 0), EntityType.ITEM_DISPLAY,
				SpawnReason.CUSTOM, e -> {
					if (e instanceof ItemDisplay dis) {
						dis.setRotation(rot, 0);
						dis.setItemStack(ItemStack.of(Material.CALIBRATED_SCULK_SENSOR));
						dis.setInterpolationDelay(0);
						dis.setInterpolationDuration(DELAY_TICK);
						Bukkit.getScheduler().runTask(Ioc.resolve(JavaPlugin.class), () -> {
							dis.setTransformation(RadarHandler.TOP_TRANSFORMATION);
						});
					}
				});

		tool.chargeTime = DELAY_TICK;
		Vi6Sound.RADAR_POSE.play(tool.bottom.getWorld(), tool.bottom.getLocation());
		editItem(tool, i -> i.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true));
		evt.setCancelled(true);
	}

	@Override
	protected void onAttachedToPlayer(Radar tool) {
	}

	@Override
	protected void onDetachFromPlayer(Radar tool) {
	}

	@Override
	protected void onToolCleanup(Radar tool) {
		if (tool.top != null) {
			tool.top.remove();
			tool.top = null;
		}
		if (tool.middle != null) {
			tool.middle.remove();
			tool.middle = null;
		}
		if (tool.bottom != null) {
			tool.bottom.remove();
			tool.bottom = null;
		}
	}

	@Override
	protected ItemStack makeItem(Radar tool) {
		return new ItemStackBuilder(Material.DAYLIGHT_DETECTOR).unstackable().name(getDisplayName()).lore(getLore())
				.flags(ItemFlag.values())
				.postApply(
						item -> item.setData(DataComponentTypes.USE_COOLDOWN,
								UseCooldown.useCooldown(COOLDOWN_TICK).cooldownGroup(NamespacedKey
										.fromString(getToolCode() + '/' + tool.getId(), Ioc.resolve(JavaPlugin.class)))
										.build()))
				.build();
	}

	@EventHandler
	private void onEmpStart(EntityEmpStartEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				item.editMeta(m -> m.displayName(getDisplayName().decorate(TextDecoration.STRIKETHROUGH)
						.append(Component.text(" - ")).append(Component.text("Brouillé", NamedTextColor.RED))));
				p.setCooldown(item, COOLDOWN_TICK);
				item.unsetData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE);
				onToolCleanup(getToolFromItem(item));
			});
		}
	}

	@EventHandler
	private void onEmpStop(EntityEmpEndEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				item.editMeta(m -> m.displayName(getDisplayName()));
			});
		}
	}

	public static class Radar extends Tool {

		public Radar(ToolHandler<?> handler) {
			super(handler);
		}

		private ItemDisplay top;

		private ItemDisplay middle;

		private ItemDisplay bottom;

		private int chargeTime;
	}
}
