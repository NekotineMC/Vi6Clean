package fr.nekotine.vi6clean.impl.tool.personal.trapwire;

import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
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
			// for (var pack : packs.b()) {
			// pmanager.sendServerPacket(player, pack);
			// }
		}
	}

	private List<Packet<? super ClientGamePacketListener>> trapwireSpawnPacket(Vector p1, Vector p2) {
		var direction = p2.clone().subtract(p1);
		var distance = direction.length();
		float yaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
		float pitch = (float) Math.toDegrees(Math.asin(direction.getY() / distance));
		Quaternionf rotation = new Quaternionf().rotationYXZ((float) Math.toRadians(yaw),
				(float) Math.toRadians(-pitch), 0);

		var eid = Bukkit.getUnsafe().nextEntityId();
		var spawnPacket = new ClientboundAddEntityPacket(eid, UUID.randomUUID(), p1.getX(), p1.getY(), p1.getZ(), pitch,
				yaw, EntityType.BLOCK_DISPLAY, 0, Vec3.ZERO, yaw);
		var dataValues = List.of(
				new SynchedEntityData.DataValue<>(12, EntityDataSerializers.VECTOR3,
						new Vector3f(0.05f, 0.05f, (float) distance)),
				new SynchedEntityData.DataValue<>(13, EntityDataSerializers.QUATERNION, rotation));
		var metadataPacket = new ClientboundSetEntityDataPacket(eid, dataValues);
		return List.of(spawnPacket, metadataPacket);
	}

	public static class TrapWire extends Tool {
		private boolean placed = false;
		public TrapWire(ToolHandler<?> handler) {
			super(handler);
		}
	}
}
