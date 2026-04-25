package fr.nekotine.vi6clean.impl.tool.personal.trapwire;

import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.tuple.Pair;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;

import java.lang.reflect.Type;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.block.BlockState;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.bukkit.craftbukkit.entity.CraftPlayer;

@ToolCode("hogrider")
public class TrapWireHandler extends ToolHandler<TrapWireHandler.TrapWire> {
	private final double PLACE_RANGE = 5;
	private final double WIRE_LENGTH = 5;
	public TrapWireHandler() {
		super(TrapWire::new);
	}

	@Override
	protected void onAttachedToPlayer(TrapWire tool) {
	}

	@Override
	protected void onDetachFromPlayer(TrapWire tool) {
	}

	@Override
	protected void onToolCleanup(TrapWire tool) {
	}

	@EventHandler
	private void onPlayerInterract(PlayerInteractEvent evt) {
	}

	@EventHandler
	private void onTick(TickElapsedEvent evt) {
	}

	@EventHandler
	private void onPlayerMove(PlayerMoveEvent evt) {
		for (var tool : getTools()) {
			if (tool.placed || !evt.getPlayer().equals(tool.getOwner()))
				continue;

			var player = tool.getOwner();
			var start = player.getWorld().rayTraceBlocks(player.getEyeLocation(),
					player.getEyeLocation().getDirection(), PLACE_RANGE, FluidCollisionMode.NEVER, true);
			if (start == null)
				continue;

			var startLoc = start.getHitPosition().toLocation(player.getWorld());
			var startFace = start.getHitBlockFace();
			var end = player.getWorld().rayTraceBlocks(startLoc, startFace.getDirection(), WIRE_LENGTH,
					FluidCollisionMode.NEVER, true);
			if (end == null)
				continue;

			var endVector = end.getHitPosition();
			var endBlock = end.getHitBlock();
			var endFace = end.getHitBlockFace();
			var packets = trapwireSpawnPacket(start.getHitPosition(), endVector);
			for (var pack : packs.b()) {
				pmanager.sendServerPacket(player, pack);
			}
		}
	}

	private List<Packet<? super ClientGamePacketListener>> trapwireSpawnPacket(Vector p1, Vector p2) {
		var direction = p2.clone().subtract(p1);
		var distance = direction.length();
		float yaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
		float pitch = (float) Math.toDegrees(Math.asin(direction.getY() / distance));
		Quaternionf rotation = new Quaternionf().rotationYXZ((float) Math.toRadians(yaw),
				(float) Math.toRadians(-pitch), 0);
		Transformation transform = new Transformation(new Vector3f(0, 0, 0), // Translation (already at r1)
				rotation, // Left Rotation
				new Vector3f(0.05f, 0.05f, (float) distance), // Scale
				new Quaternionf() // Right Rotation
		);

		var eid = Bukkit.getUnsafe().nextEntityId();

		var spawnPacket = pmanager.createPacket(PacketType.Play.Server.SPAWN_ENTITY);
		spawnPacket.getIntegers().write(0, eid);
		spawnPacket.getUUIDs().write(0, UUID.randomUUID());
		spawnPacket.getEntityTypeModifier().write(0, EntityType.BLOCK_DISPLAY);
		spawnPacket.getDoubles().write(0, p1.getX()).write(1, p1.getY()).write(2, p1.getZ());
		spawnPacket.getBytes().write(0, (byte) pitch).write(1, (byte) yaw);

		// Entity Metadata Packet
		var metadataPacket = pmanager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
		metadataPacket.getIntegers().write(0, eid);
		var dataValues = new ArrayList<WrappedDataValue>(2);
		dataValues.add(new WrappedDataValue(12, WrappedDataWatcher.Registry.get((Type) Vector3f.class),
				new Vector3f(0.05f, 0.05f, (float) distance)));
		dataValues.add(new WrappedDataValue(13, WrappedDataWatcher.Registry.get((Type) Quaternionf.class), rotation));
		metadataPacket.getDataValueCollectionModifier().write(0, dataValues);
		return new Pair<>(eid, new PacketContainer[]{spawnPacket, metadataPacket});
	}

	public static class TrapWire extends Tool {
		private boolean placed = false;
		public TrapWire(ToolHandler<?> handler) {
			super(handler);
		}
	}
}
