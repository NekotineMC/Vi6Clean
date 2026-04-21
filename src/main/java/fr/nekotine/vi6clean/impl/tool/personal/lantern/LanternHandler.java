package fr.nekotine.vi6clean.impl.tool.personal.lantern;

import java.util.LinkedList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Silverfish;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import com.comphenix.protocol.wrappers.EnumWrappers;

import fr.nekotine.core.glow.EntityGlowModule;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.ticking.TickTimeStamp;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.util.MobAiUtil;
import fr.nekotine.core.util.SpatialUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.impl.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.Sound.Source;

@ToolCode("lantern")
public class LanternHandler extends ToolHandler<LanternHandler.Lantern> {

	private final int MAX_LANTERN = getConfiguration().getInt("max_lantern", 2);

	private final double SQUARED_PICKUP_BLOCK_RANGE = getConfiguration().getDouble("squared_pickup_range", 2.25);

	public LanternHandler() {
		super(Lantern::new);
	}

	@EventHandler
	private void onPlayerInteract(PlayerInteractEvent evt) {
		if (evt.getHand() != EquipmentSlot.HAND) {
			return;
		}
		var player = evt.getPlayer();
		var optWrap = Ioc.resolve(WrappingModule.class).getWrapperOptional(player, PlayerWrapper.class);
		if (optWrap.isEmpty()) {
			return;
		}

		// Pickup check
		var allies = optWrap.get().ourTeam();
		if (getTools().stream().filter(t -> allies.contains(t.getOwner())).anyMatch(tool -> {
			// Ally try pickup
			var owner = tool.getOwner();
			if (owner == null) {
				return false;
			}
			var flagModule = Ioc.resolve(StatusFlagModule.class);
			if (flagModule.hasAny(owner, EmpStatusFlag.get())) {
				return false;
			}
			var ite = tool.displayedLanterns.iterator();
			var changed = false;
			while (ite.hasNext()) {
				var lantern = ite.next();
				var lanternLoc = lantern.getLocation();
				var lanternScale = lantern.getTransformation().getScale();
				var meanScale = (lanternScale.x() + lanternScale.y() + lanternScale.z()) / 3;
				if (player.getLocation().distanceSquared(lanternLoc) <= SQUARED_PICKUP_BLOCK_RANGE * meanScale
						* meanScale) {
					changed = true;
					var w = lanternLoc.getWorld();
					Vi6Sound.LANTERNE_PRE_TELEPORT.play(w, lanternLoc);
					if (!owner.equals(player)) {
						var ownerLoc = owner.getLocation();
						w.spawnParticle(Particle.EXPLOSION, lanternLoc, 1);
						player.teleport(ownerLoc);
						Vi6Sound.LANTERNE_POST_TELEPORT.play(w, ownerLoc);
					}
					lantern.remove();
					ite.remove();
					break;
				}
			}
			if (changed) {

				var amount = MAX_LANTERN - tool.displayedLanterns.size();
				editItem(tool, item -> {
					item.resetData(DataComponentTypes.ITEM_MODEL);
					item.setAmount(amount);
				});
				return true;
			}
			return false;
		})) {
			return; // If the lantern got picked up then no need to drop a new one
		}

		// Drop check
		var tool = getToolFromItem(evt.getItem());
		if (tool == null) {
			return;
		}
		if (EventUtil.isCustomAction(evt, CustomAction.HIT_ANY)) {
			// TRY PLACE
			var owner = tool.getOwner();
			if (tool.displayedLanterns.size() >= MAX_LANTERN || tool.fallingEntity != null || owner == null) {
				Vi6Sound.LANTERNE_CANNOT_PLACE.play(player);
				evt.getPlayer()
						.playSound(Sound.sound(NamespacedKey.minecraft("entity.villager.no"), Source.MASTER, 1, 1));
				return;
			}
			var loc = owner.getLocation();
			loc.setPitch(0);
			var lantern = (BlockDisplay) owner.getWorld().spawnEntity(loc, EntityType.BLOCK_DISPLAY, SpawnReason.CUSTOM,
					e -> {
						if (e instanceof BlockDisplay display) {
							var scale = (float) owner.getAttribute(Attribute.SCALE).getValue();
							var transf = new Transformation(new Vector3f(-0.5f, /*-0.7405f*/0, -0.5f),
									new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f());
							display.setTransformation(transf);
							display.setBlock(Bukkit.createBlockData(Material.LANTERN));
							display.setPersistent(false);
						}
					});

			tool.fallingEntity = owner.getWorld().spawnEntity(owner.getLocation(), EntityType.SILVERFISH,
					SpawnReason.CUSTOM, e -> {
						if (e instanceof Silverfish ent) {
							ent.addPassenger(lantern);
							ent.setInvisible(true);
							ent.setSilent(true);
							ent.setInvulnerable(true);
							ent.setPersistent(false);
							MobAiUtil.clearBrain(ent);
						}
					});
			tool.displayedLanterns.add(lantern);
			var amountRemaining = MAX_LANTERN - tool.displayedLanterns.size();
			editItem(tool, item -> {
				if (amountRemaining <= 0) {
					item.setData(DataComponentTypes.ITEM_MODEL, Material.IRON_CHAIN.key());
				} else {
					item.setAmount(amountRemaining);
				}
			});
			var glowModule = Ioc.resolve(EntityGlowModule.class);
			optWrap.get().ourTeam().stream().filter(p -> !p.equals(owner))
					.forEach(p -> glowModule.glowEntityFor(lantern, p, EnumWrappers.ChatFormatting.DARK_BLUE));
			glowModule.glowEntityFor(lantern, owner, EnumWrappers.ChatFormatting.YELLOW);
			evt.setCancelled(true);
		}
	}

	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		for (var tool : getTools()) {

			if (tool.fallingEntity != null && tool.fallingEntity.isOnGround()) {
				Vi6Sound.LANTERNE_POSE.play(tool.fallingEntity.getWorld(), tool.fallingEntity.getLocation());
				var loc = tool.fallingEntity.getLocation();
				loc.setPitch(0);
				for (var passenger : tool.fallingEntity.getPassengers()) {
					tool.fallingEntity.removePassenger(passenger);
					passenger.teleport(loc);
				}
				tool.fallingEntity.remove();
				tool.fallingEntity = null;
			}

			if (evt.timeStampReached(TickTimeStamp.HalfSecond)) {
				SpatialUtil.circle2DDensity(1.5, 3, Math.random() * 6, (offsetX, offsetZ) -> {
					for (var lantern : tool.displayedLanterns) {
						var loc = lantern.getLocation();
						var x = loc.getX();
						var y = loc.getY();
						var z = loc.getZ();
						lantern.getWorld().spawnParticle(Particle.FIREWORK, x + offsetX, y, z + offsetZ, 0, 0, 0, 0,
								0f);
					}
				});
			}
		}
	}

	@Override
	protected void onAttachedToPlayer(Lantern tool) {
		var amount = MAX_LANTERN - tool.displayedLanterns.size();
		editItem(tool, item -> {
			item.resetData(DataComponentTypes.ITEM_MODEL);
			item.setAmount(amount);
		});
	}

	@Override
	protected void onDetachFromPlayer(Lantern tool) {
		onToolCleanup(tool);
	}

	@Override
	protected void onToolCleanup(Lantern tool) {
		for (var lantern : tool.displayedLanterns) {
			lantern.remove();
		}
		tool.displayedLanterns.clear();
		if (tool.fallingEntity != null) {
			tool.fallingEntity.remove();
			tool.fallingEntity = null;
		}
	}

	public static class Lantern extends Tool {

		public Lantern(ToolHandler<?> handler) {
			super(handler);
		}

		private Entity fallingEntity;

		private List<BlockDisplay> displayedLanterns = new LinkedList<>();
	}
}
