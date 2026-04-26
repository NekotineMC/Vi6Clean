package fr.nekotine.vi6clean.impl.tool.personal.trapwire;

import fr.nekotine.core.ticking.TickTimeStamp;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.impl.status.effect.OmniCaptedStatusEffectType;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;

import net.minecraft.world.level.block.Blocks;

import java.util.Set;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Transformation;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;

import org.bukkit.event.player.PlayerMoveEvent;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.status.effect.StatusEffect;
import fr.nekotine.core.status.effect.StatusEffectModule;

@ToolCode("trapwire")
public class TrapWireHandler extends ToolHandler<TrapWireHandler.TrapWire> {
	private final double PLACE_RANGE = getConfiguration().getDouble("place_range", 5);
	private final double WIRE_LENGTH = getConfiguration().getDouble("wire_length", 5);
	private final double DAMAGE = getConfiguration().getDouble("damage", 5);
	private final int OMNI_DURATION = Math.round((float) (20 * getConfiguration().getDouble("omni_duration", 2)));
	private final StatusEffect omniEffect = new StatusEffect(OmniCaptedStatusEffectType.get(), OMNI_DURATION);

	public TrapWireHandler() {
		super(TrapWire::new);
	}

	@Override
	protected void onAttachedToPlayer(TrapWire tool) {
		tool.owner = tool.getOwner();
		var player = tool.owner;
		if (player != null) {
			var item = player.getInventory().getItemInMainHand();
			if (itemMatch(tool, item)) {
				tool.previewActivated = true;
			}
		}
	}

	@Override
	protected void onDetachFromPlayer(TrapWire tool) {
		cleanupPreview(tool);
	}

	@Override
	protected void onToolCleanup(TrapWire tool) {
		cleanupPreview(tool);
		if (tool.placedEntity != null) {
			tool.placedEntity.remove();
			tool.placedEntity = null;
		}
	}

	@EventHandler
	private void onPlayerItemHeld(PlayerItemHeldEvent evt) {
		for (var tool : getTools()) {
			if (evt.getPlayer().equals(tool.owner)) {
				var item = evt.getPlayer().getInventory().getItem(evt.getNewSlot());
				if (itemMatch(tool, item)) {
					tool.previewActivated = true;
				} else {
					tool.previewActivated = false;
					cleanupPreview(tool);
				}
			}
		}
	}

	@EventHandler
	private void onPlayerInterract(PlayerInteractEvent evt) {
		if (evt.getHand() != EquipmentSlot.HAND || !EventUtil.isCustomAction(evt, CustomAction.HIT_ANY)) {
			return;
		}
		var player = evt.getPlayer();
		var tool = getToolFromItem(evt.getItem());
		if (tool == null || tool.placed) {
			return;
		}

		var points = getTrapWirePoints(player);
		if (points == null || isNearExistingTrap(points.p1)) {
			return;
		}

		var transform = getTrapWireTransform(points.p1, points.p2);

		cleanupPreview(tool);
		tool.placed = true;

		var world = player.getWorld();
		var loc = points.p1.toLocation(world);
		loc.setYaw(transform.yaw);
		loc.setPitch(transform.pitch);
		var display = world.spawn(loc, BlockDisplay.class);
		display.setBlock(Material.REDSTONE_BLOCK.createBlockData());
		display.setTransformation(new Transformation(new Vector3f(-0.025f, -0.025f, 0f), new Quaternionf(),
				new Vector3f(0.05f, 0.05f, transform.distance), new Quaternionf()));
		tool.placedEntity = display;
		detachFromOwner(tool);
	}

	@EventHandler
	private void onPlayerMove(PlayerMoveEvent evt) {
		if (!evt.hasChangedPosition()) {
			return;
		}
		var player = evt.getPlayer();
		var wrappingModule = Ioc.resolve(WrappingModule.class);

		var it = getTools().iterator();
		while (it.hasNext()) {
			var tool = it.next();
			if (!tool.placed || tool.owner == null) {
				continue;
			}

			var ownerWrapO = wrappingModule.getWrapperOptional(tool.owner, PlayerWrapper.class);
			if (ownerWrapO.isEmpty()) {
				continue;
			}

			var isEnemy = ownerWrapO.get().ennemiTeamInMap().anyMatch(e -> e.equals(player));
			if (!isEnemy) {
				continue;
			}

			var display = tool.placedEntity;
			var loc = display.getLocation();
			var p1 = loc.toVector();
			var dir = loc.getDirection();
			var distance = display.getTransformation().getScale().z();

			var hit = player.getBoundingBox().rayTrace(p1, dir, distance);
			if (hit != null) {
				var effectModule = Ioc.resolve(StatusEffectModule.class);
				effectModule.addEffect(player, omniEffect);
				player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation().add(0, .5, 0), 1, 0, 0, 0, 0);
				Vi6Sound.TRAPWIRE_TRIGGER.play(player.getLocation());
				player.damage(DAMAGE,
						DamageSource.builder(DamageType.EXPLOSION).withDirectEntity(ownerWrapO.get().GetWrapped())
								.withCausingEntity(ownerWrapO.get().GetWrapped()).build());
				player.setNoDamageTicks(0);

				it.remove();
				detachFromOwner(tool);
				onToolCleanup(tool);
			}
		}
	}

	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		// if (!evt.timeStampReached(TickTimeStamp.QuartSecond))
		// return;

		for (var tool : getTools()) {
			if (tool.placed || !tool.previewActivated)
				continue;
			var player = tool.getOwner();
			if (player == null || !player.isOnline())
				continue;

			if (!tool.equals(getToolFromItem(player.getInventory().getItemInMainHand())))
				continue;

			var eye = player.getEyeLocation();
			var pos = eye.toVector();
			var yaw = eye.getYaw();
			var pitch = eye.getPitch();

			if (tool.previewId != -1 && tool.lastPos != null && tool.lastPos.equals(pos) && tool.lastYaw == yaw
					&& tool.lastPitch == pitch) {
				continue;
			}

			tool.lastPos = pos;
			tool.lastYaw = yaw;
			tool.lastPitch = pitch;

			var points = getTrapWirePoints(player);
			if (points == null || isNearExistingTrap(points.p1)) {
				cleanupPreview(tool);
				continue;
			}

			var transform = getTrapWireTransform(points.p1, points.p2);

			if (tool.previewId != -1) {
				sendUpdatePackets(player, tool, points.p1, transform.yaw, transform.pitch, transform.distance);
			} else {
				sendSpawnPackets(player, tool, points.p1, transform.yaw, transform.pitch, transform.distance);
			}
		}
	}

	private record TrapWirePoints(Vector p1, Vector p2) {
	}

	private record TrapWireTransform(float yaw, float pitch, float distance) {
	}

	private boolean isNearExistingTrap(Vector p) {
		for (var tool : getTools()) {
			if (tool.placed && tool.placedEntity != null) {
				var loc = tool.placedEntity.getLocation();
				var p1 = loc.toVector();
				var dir = loc.getDirection();
				var distance = tool.placedEntity.getTransformation().getScale().z();
				var p2 = p1.clone().add(dir.clone().multiply(distance));
				if (segmentDistanceSquared(p, p1, p2) < 0.25) { // 0.5 blocks
					return true;
				}
			}
		}
		return false;
	}

	private double segmentDistanceSquared(Vector p, Vector a, Vector b) {
		var ab = b.clone().subtract(a);
		var ap = p.clone().subtract(a);
		var bp = p.clone().subtract(b);
		double e = ap.dot(ab);
		if (e <= 0)
			return ap.lengthSquared();
		double f = ab.lengthSquared();
		if (e >= f)
			return bp.lengthSquared();
		return ap.lengthSquared() - e * e / f;
	}

	private TrapWirePoints getTrapWirePoints(Player player) {
		var eye = player.getEyeLocation();
		var start = player.getWorld().rayTraceBlocks(eye, eye.getDirection(), PLACE_RANGE, FluidCollisionMode.NEVER,
				true);
		if (start == null) {
			return null;
		}

		var startLoc = start.getHitPosition().toLocation(player.getWorld());
		var startFace = start.getHitBlockFace();
		var end = player.getWorld().rayTraceBlocks(startLoc, startFace.getDirection(), WIRE_LENGTH,
				FluidCollisionMode.NEVER, true);
		if (end == null) {
			return null;
		}

		var dir = end.getHitPosition().clone().subtract(start.getHitPosition());
		var len = dir.length();
		if (len < 0.02) {
			return null;
		}
		var unitDir = dir.clone().multiply(1.0 / len);
		var p1 = start.getHitPosition().clone().add(unitDir.clone().multiply(0.005));
		var p2 = end.getHitPosition().clone().subtract(unitDir.clone().multiply(0.005));
		return new TrapWirePoints(p1, p2);
	}

	private TrapWireTransform getTrapWireTransform(Vector p1, Vector p2) {
		var direction = p2.clone().subtract(p1);
		var distance = (float) direction.length();
		float yaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
		float pitch = (float) -Math.toDegrees(Math.asin(direction.getY() / distance));
		return new TrapWireTransform(yaw, pitch, distance);
	}

	private void sendSpawnPackets(Player player, TrapWire tool, Vector p1, float yaw, float pitch, float distance) {
		var connection = ((CraftPlayer) player).getHandle().connection;
		var eid = Bukkit.getUnsafe().nextEntityId();
		var spawnPacket = new ClientboundAddEntityPacket(eid, UUID.randomUUID(), p1.getX(), p1.getY(), p1.getZ(), pitch,
				yaw, EntityType.BLOCK_DISPLAY, 0, Vec3.ZERO, yaw);
		var dataValues = List.of(
				new SynchedEntityData.DataValue<>(11, EntityDataSerializers.VECTOR3,
						new Vector3f(-0.025f, -0.025f, 0f)),
				new SynchedEntityData.DataValue<>(12, EntityDataSerializers.VECTOR3,
						new Vector3f(0.05f, 0.05f, distance)),
				new SynchedEntityData.DataValue<>(23, EntityDataSerializers.BLOCK_STATE,
						Blocks.REDSTONE_BLOCK.defaultBlockState()));
		var metadataPacket = new ClientboundSetEntityDataPacket(eid, dataValues);
		connection.send(spawnPacket);
		connection.send(metadataPacket);
		tool.previewId = eid;
		tool.lastDistance = distance;
	}

	private void sendUpdatePackets(Player player, TrapWire tool, Vector p1, float yaw, float pitch, float distance) {
		var connection = ((CraftPlayer) player).getHandle().connection;
		var teleportPacket = new ClientboundTeleportEntityPacket(tool.previewId,
				new PositionMoveRotation(new Vec3(p1.getX(), p1.getY(), p1.getZ()), Vec3.ZERO, yaw, pitch), Set.of(),
				false);
		connection.send(teleportPacket);
		if (Math.abs(distance - tool.lastDistance) > 0.001) {
			List<SynchedEntityData.DataValue<?>> dataValues = List.of(new SynchedEntityData.DataValue<>(12,
					EntityDataSerializers.VECTOR3, new Vector3f(0.05f, 0.05f, distance)));
			var metadataPacket = new ClientboundSetEntityDataPacket(tool.previewId, dataValues);
			connection.send(metadataPacket);
			tool.lastDistance = distance;
		}
	}

	public static class TrapWire extends Tool {
		private boolean placed = false;
		private boolean previewActivated = false;
		private int previewId = -1;
		private Vector lastPos;
		private float lastYaw;
		private float lastPitch;
		private double lastDistance;
		private BlockDisplay placedEntity;
		private Player owner;
		public TrapWire(ToolHandler<?> handler) {
			super(handler);
		}
	}

	private void cleanupPreview(TrapWire tool) {
		if (tool.previewId != -1) {
			var player = tool.getOwner();
			if (player != null && player.isOnline()) {
				var packet = new ClientboundRemoveEntitiesPacket(tool.previewId);
				((CraftPlayer) player).getHandle().connection.send(packet);
			}
			tool.previewId = -1;
		}
	}
}
