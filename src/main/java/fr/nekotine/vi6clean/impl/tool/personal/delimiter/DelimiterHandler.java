package fr.nekotine.vi6clean.impl.tool.personal.delimiter;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.status.effect.StatusEffect;
import fr.nekotine.core.status.effect.StatusEffectModule;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.util.SpatialUtil;
import fr.nekotine.core.util.InventoryUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6Keys;
import fr.nekotine.vi6clean.impl.status.effect.SuffocatingStatusEffectType;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpEndEvent;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpStartEvent;
import fr.nekotine.vi6clean.impl.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

@ToolCode("delimiter")
public class DelimiterHandler extends ToolHandler<DelimiterHandler.Delimiter> {

	private final int COOLDOWN_TICKS = (int) (getConfiguration().getDouble("cooldown", 10) * 20);
	private final int DURATION_TICKS = (int) (getConfiguration().getDouble("duration", 10) * 20);
	private final double DIAMETER = getConfiguration().getDouble("diameter", 16);
	private final StatusEffect SUFF_EFFECT = new StatusEffect(SuffocatingStatusEffectType.get(), -1);

	public DelimiterHandler() {
		super(Delimiter::new);
	}

	@Override
	protected void onAttachedToPlayer(Delimiter tool) {
		editItem(tool, item -> updateItem(tool.mode, item));
	}

	@Override
	protected void onDetachFromPlayer(Delimiter tool) {
	}

	@Override
	protected void onToolCleanup(Delimiter tool) {
		if (!tool.placed) {
			return;
		}
		var it = tool.cagePlayersInside.iterator();
		while (it.hasNext()) {
			var player = it.next();
			leaveCageEffect(player, tool);
		}
	}

	@EventHandler
	private void onPlayerInteract(PlayerInteractEvent evt) {
		if (evt.getHand() != EquipmentSlot.HAND) {
			return;
		}
		var tool = getToolFromItem(evt.getItem());
		if (tool == null) {
			return;
		}
		var player = evt.getPlayer();
		if (EventUtil.isCustomAction(evt, CustomAction.INTERACT_ANY)) {
			tool.mode = tool.mode.next();
			updateItem(tool.mode, evt.getItem());
			evt.setCancelled(true);
		} else if (EventUtil.isCustomAction(evt, CustomAction.HIT_ANY)) {
			if (!tool.placed && !player.hasCooldown(evt.getItem())
					&& !Ioc.resolve(StatusFlagModule.class).hasAny(player, EmpStatusFlag.get())) {
				shoot(player, tool);
				evt.setCancelled(true);
			}
		}
	}

	private boolean isInside(Location loc, Location center) {
		var radius = DIAMETER / 2;
		var cage_min_x = center.getX() - radius;
		var cage_max_x = center.getX() + radius;
		var cage_min_y = center.getY() - radius;
		var cage_max_y = center.getY() + radius;
		var cage_min_z = center.getZ() - radius;
		var cage_max_z = center.getZ() + radius;
		return (loc.getX() >= cage_min_x && loc.getX() <= cage_max_x && loc.getY() >= cage_min_y
				&& loc.getY() <= cage_max_y && loc.getZ() >= cage_min_z && loc.getZ() <= cage_max_z);
	}

	private void bounceOffCage(Player player, Delimiter tool) {
		Location playerLoc = player.getLocation();
		double radius = DIAMETER / 2.0;
		double margin = 0.75;
		double limit = radius - margin;
		double dx = Math.abs(playerLoc.getX() - tool.cageCenter.getX());
		double dy = Math.abs(playerLoc.getY() - tool.cageCenter.getY());
		double dz = Math.abs(playerLoc.getZ() - tool.cageCenter.getZ());
		if (dx > limit || dy > limit || dz > limit) {
			Vector centerVec = tool.cageCenter.toVector();
			Vector playerVec = playerLoc.toVector();
			Vector bounceDirection = centerVec.subtract(playerVec).normalize();
			double bounceForce = 0.25;
			bounceDirection.multiply(bounceForce);
			bounceDirection.setY(Math.max(0.2, bounceDirection.getY()));
			player.setVelocity(bounceDirection);
		}
	}

	@EventHandler
	private void onPlayerMove(PlayerMoveEvent evt) {
		var player = evt.getPlayer();
		var wrappingModule = Ioc.resolve(WrappingModule.class);
		for (var tool : getTools()) {
			if (!tool.placed) {
				continue;
			}
			var opt = wrappingModule.getWrapperOptional(tool.getOwner(), PlayerWrapper.class);
			if (opt.isEmpty()) {
				continue;
			}
			var wrapper = opt.get();
			if (!wrapper.enemyTeamInMap().anyMatch(p -> p.equals(player))) {
				continue;
			}
			if (tool.cagePlayersInside.contains(player)) {
				if (tool.cageMode == DelimiterMode.BORDER) {
					bounceOffCage(player, tool);
				}
				if (!isInside(player.getLocation(), tool.cageCenter)) {
					tool.cagePlayersInside.remove(player);
					leaveCageEffect(player, tool);
				}
			} else {
				if (isInside(player.getLocation(), tool.cageCenter)) {
					tool.cagePlayersInside.add(player);
					enterCageEffect(player, tool);
				}
			}
		}
	}

	private void enterCageEffect(Player player, Delimiter tool) {
		var sef = Ioc.resolve(StatusEffectModule.class);
		if (tool.cageMode == DelimiterMode.SUFFOCATING) {
			sef.addEffect(player, SUFF_EFFECT);
		}
	}

	private void leaveCageEffect(Player player, Delimiter tool) {
		var sef = Ioc.resolve(StatusEffectModule.class);
		if (tool.cageMode == DelimiterMode.SUFFOCATING) {
			sef.removeEffect(player, SUFF_EFFECT);
		}
	}

	private void shoot(Player player, Delimiter tool) {
		var eyeLoc = player.getEyeLocation();
		var raytrace = player.getWorld().rayTrace(eyeLoc, eyeLoc.getDirection(), 100, FluidCollisionMode.NEVER, true,
				0.1, (e) -> !e.equals(player));
		if (raytrace == null || raytrace.getHitPosition() == null) {
			return;
		}
		var impact = raytrace.getHitPosition().toLocation(player.getWorld());

		var start = eyeLoc.toVector();
		var dir = eyeLoc.getDirection();
		var dist = eyeLoc.distance(impact);
		SpatialUtil.line3DFromDir(start, dir, dist, 2.0, (v) -> {
			player.getWorld().spawnParticle(Particle.FIREWORK, v.toLocation(player.getWorld()), 1, 0, 0, 0, 0);
		});

		tool.cageMode = tool.mode;
		tool.cageCenter = impact;
		tool.placed = true;

		var world = player.getWorld();

		float s = (float) DIAMETER;
		float r = s / 2;
		float thickness = 0.1f;
		Vector3f scale = new Vector3f(s, s, thickness);

		Vector3f[] translations = {new Vector3f(0, 0, r), new Vector3f(0, 0, -r), new Vector3f(r, 0, 0),
				new Vector3f(-r, 0, 0), new Vector3f(0, r, 0), new Vector3f(0, -r, 0)};

		Quaternionf[] rotations = {new Quaternionf(), // Front
				new Quaternionf(new AxisAngle4f((float) Math.PI, 0, 1, 0)), // Back
				new Quaternionf(new AxisAngle4f((float) Math.PI / 2f, 0, 1, 0)), // Right
				new Quaternionf(new AxisAngle4f((float) -Math.PI / 2f, 0, 1, 0)), // Left
				new Quaternionf(new AxisAngle4f((float) -Math.PI / 2f, 1, 0, 0)), // Top
				new Quaternionf(new AxisAngle4f((float) Math.PI / 2f, 1, 0, 0)) // Bottom
		};

		for (int i = 0; i < 6; i++) {
			int index = i;
			world.spawnEntity(impact, EntityType.ITEM_DISPLAY, CreatureSpawnEvent.SpawnReason.CUSTOM, b -> {
				if (b instanceof ItemDisplay dis) {
					b.setPersistent(false);
					var is = new ItemStack(Material.GLASS_PANE);
					var im = is.getItemMeta();
					var model_key = Material.BARRIER.key();
					switch (tool.cageMode) {
						case BORDER :
							model_key = Key.key(Vi6Keys.DELIMITER_BORDER_ZONE_MODEL);
							break;
						case SUFFOCATING :
							model_key = Key.key(Vi6Keys.DELIMITER_MIASMA_ZONE_MODEL);
							break;
					}
					im.setItemModel(new NamespacedKey(model_key.namespace(), model_key.value()));
					is.setItemMeta(im);
					dis.setItemStack(is);
					Transformation transform = new Transformation(translations[index], rotations[index], scale,
							new Quaternionf());
					dis.setTransformation(transform);
					tool.cageWalls.add(dis);
				}
			});
		}

		var wrappingModule = Ioc.resolve(WrappingModule.class);
		var opt = wrappingModule.getWrapperOptional(player, PlayerWrapper.class);
		if (opt.isEmpty()) {
			return;
		}
		var wrapper = opt.get();
		wrapper.enemyTeamInMap().forEach(enemy -> {
			if (isInside(enemy.getLocation(), tool.cageCenter)) {
				tool.cagePlayersInside.add(enemy);
				enterCageEffect(enemy, tool);
			}
		});

		player.setCooldown(player.getInventory().getItemInMainHand().getType(), COOLDOWN_TICKS);

		Bukkit.getScheduler().runTaskLater(Ioc.resolve(JavaPlugin.class), () -> {
			for (var wall : tool.cageWalls) {
				wall.remove();
			}
			tool.cageWalls.clear();
			for (var inside : tool.cagePlayersInside) {
				leaveCageEffect(inside, tool);
			}
			tool.cagePlayersInside.clear();
			tool.placed = false;
		}, DURATION_TICKS);
	}

	@EventHandler
	private void onEmpStart(EntityEmpStartEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				item.setData(DataComponentTypes.ITEM_MODEL, Material.SPAWNER.key());
				item.editMeta(m -> m.displayName(getDisplayName().decorate(TextDecoration.STRIKETHROUGH)
						.append(Component.text(" - ")).append(Component.text("Brouillé", NamedTextColor.RED))));
			});
		}
	}

	@EventHandler
	private void onEmpStop(EntityEmpEndEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				var tool = getToolFromItem(item);
				updateItem(tool.mode, item);
			});
		}
	}

	private void updateItem(DelimiterMode mode, ItemStack item) {
		var im = item.getItemMeta();
		switch (mode) {
			case SUFFOCATING :
				var key = Key.key(Vi6Keys.DELIMITER_MIASMA_ITEM_MODEL);
				im.setItemModel(new NamespacedKey(key.namespace(), key.value()));
				im.displayName(Component.text("Cage de miasma", NamedTextColor.GREEN));
				im.lore(Arrays.asList(Component.text("Un nuage de poison remplit la cage", NamedTextColor.AQUA)));
				break;
			case BORDER :
				var borderKey = Key.key(Vi6Keys.DELIMITER_BORDER_ITEM_MODEL);
				im.setItemModel(new NamespacedKey(borderKey.namespace(), borderKey.value()));
				im.displayName(Component.text("Cage barrière", NamedTextColor.RED));
				im.lore(Arrays.asList(
						Component.text("Ceux qui rentrent dans cette cage n'en sortent jamais", NamedTextColor.AQUA)));
		}
		item.setItemMeta(im);
	}

	public enum DelimiterMode {
		SUFFOCATING, BORDER;
		public DelimiterMode next() {
			return values()[(ordinal() + 1) % values().length];
		}
	}

	public static class Delimiter extends Tool {
		private DelimiterMode mode = DelimiterMode.SUFFOCATING;
		private DelimiterMode cageMode;
		private boolean placed = false;
		private Location cageCenter = null;
		private final Collection<ItemDisplay> cageWalls = new HashSet<>();
		private final Set<Player> cagePlayersInside = new HashSet<>();
		public Delimiter(ToolHandler<?> handler) {
			super(handler);
		}
	}
}
