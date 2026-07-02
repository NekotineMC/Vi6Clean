package fr.nekotine.vi6clean.impl.tool.personal.scanner;

import java.util.List;
import java.util.LinkedList;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.tuple.Pair;
import fr.nekotine.core.util.InventoryUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.constant.Vi6Team;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpEndEvent;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpStartEvent;
import fr.nekotine.vi6clean.impl.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.phys.Vec3;

@ToolCode("scanner")
public class ScannerHandler extends ToolHandler<ScannerHandler.Scanner> {
	private final int SCAN_DELAY_TICK = (int) (20 * getConfiguration().getDouble("delay", 30));

	private final int SCAN_LIFETIME_TICK = (int) (20 * getConfiguration().getDouble("duration", 30));

	private @NotNull BukkitTask task;

	public ScannerHandler() {
		super(Scanner::new);
	}

	public void startScanning() {
		task = Bukkit.getScheduler().runTaskTimer(Ioc.resolve(JavaPlugin.class), this::performScan, 0, SCAN_DELAY_TICK);
	}

	public void stopScanning() {
		if (task == null) {
			return;
		}
		task.cancel();
		task = null;
	}

	@Override
	protected void onStartHandling() {
		startScanning();
	}

	@Override
	protected void onStopHandling() {
		stopScanning();
	}

	public void performScan() {
		var game = Ioc.resolve(Vi6Game.class);
		var wrappingModule = Ioc.resolve(WrappingModule.class);
		var guardOwners = getTools().stream().filter(t -> t.getOwner() != null).map(Tool::getOwner).filter(p -> {
			var flagModule = Ioc.resolve(StatusFlagModule.class);
			return !flagModule.hasAny(p, EmpStatusFlag.get());
		}).filter(p -> {
			var w = wrappingModule.getWrapperOptional(p, PlayerWrapper.class);
			return w.isPresent() && w.get().getTeam() == Vi6Team.GUARD;
		}).collect(Collectors.toCollection(LinkedList::new));
		var thiefOwners = getTools().stream().filter(t -> t.getOwner() != null).map(Tool::getOwner).filter(p -> {
			var flagModule = Ioc.resolve(StatusFlagModule.class);
			return !flagModule.hasAny(p, EmpStatusFlag.get());
		}).filter(p -> {
			var w = wrappingModule.getWrapperOptional(p, PlayerWrapper.class);
			return w.isPresent() && w.get().getTeam() == Vi6Team.THIEF;
		}).collect(Collectors.toCollection(LinkedList::new));
		Vi6Sound.SCANNER_SCAN.play(game);
		if (guardOwners.size() > 0) {
			var thiefScansIds = new LinkedList<Integer>();
			for (var thief : game.getThiefs()) {
				var scanInfo = makeScanCreationPackets(thief);
				thiefScansIds.add(scanInfo.a());
				for (var guard : guardOwners) {
					var connection = ((CraftPlayer) guard).getHandle().connection;
					for (var p : scanInfo.b()) {
						connection.send(p);
					}
					guard.setCooldown(Material.CLOCK, SCAN_DELAY_TICK);
				}
			}
			new BukkitRunnable() {

				@Override
				public void run() {

					var destroyPacket = new ClientboundRemoveEntitiesPacket(
							thiefScansIds.stream().mapToInt(Integer::intValue).toArray());

					for (var guard : guardOwners) {
						((CraftPlayer) guard).getHandle().connection.send(destroyPacket);
					}
				}
			}.runTaskLater(Ioc.resolve(JavaPlugin.class), SCAN_LIFETIME_TICK);
		}
		if (thiefOwners.size() > 0) {
			var guardScansIds = new LinkedList<Integer>();
			for (var guard : game.getGuards()) {
				var scanInfo = makeScanCreationPackets(guard);
				guardScansIds.add(scanInfo.a());
				for (var thief : thiefOwners) {
					var connection = ((CraftPlayer) thief).getHandle().connection;
					for (var p : scanInfo.b()) {
						connection.send(p);
					}
					thief.setCooldown(Material.CLOCK, SCAN_DELAY_TICK);
				}
			}
			new BukkitRunnable() {

				@Override
				public void run() {

					var destroyPacket = new ClientboundRemoveEntitiesPacket(
							guardScansIds.stream().mapToInt(Integer::intValue).toArray());

					for (var thief : thiefOwners) {
						((CraftPlayer) thief).getHandle().connection.send(destroyPacket);
					}
				}
			}.runTaskLater(Ioc.resolve(JavaPlugin.class), SCAN_LIFETIME_TICK);
		}
	}

	private Pair<Integer, List<Packet<?>>> makeScanCreationPackets(Player player) {
		var scanLoc = player.getLocation();
		@SuppressWarnings("deprecation")
		var eid = Bukkit.getUnsafe().nextEntityId(player.getWorld());

		var createPacket = new ClientboundAddEntityPacket(eid, UUID.randomUUID(), scanLoc.getX(), scanLoc.getY(),
				scanLoc.getZ(), scanLoc.getPitch(), scanLoc.getYaw(), EntityTypes.MANNEQUIN, 0, Vec3.ZERO,
				scanLoc.getYaw());

		List<SynchedEntityData.DataValue<?>> dataValues = List.of(
				new SynchedEntityData.DataValue<>(0, EntityDataSerializers.BYTE, (byte) (0x20 | 0x40)), // Invisible +
																										// Glowing
																										// effect
				new SynchedEntityData.DataValue<>(18, EntityDataSerializers.BOOLEAN, true) // Immovable
		);
		var metadataPacket = new ClientboundSetEntityDataPacket(eid, dataValues);

		return new Pair<>(eid, List.of(createPacket, metadataPacket));
	}

	@Override
	protected void onAttachedToPlayer(Scanner tool) {
	}

	@Override
	protected void onDetachFromPlayer(Scanner tool) {
	}

	@Override
	protected void onToolCleanup(Scanner tool) {
	}

	@EventHandler
	private void onEmpStart(EntityEmpStartEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				item.setData(DataComponentTypes.ITEM_MODEL, Material.COMPASS.key());
				item.editMeta(m -> m.displayName(getDisplayName().decorate(TextDecoration.STRIKETHROUGH)
						.append(Component.text(" - ")).append(Component.text("Brouillé", NamedTextColor.RED))));
			});
		}
	}

	@EventHandler
	private void onEmpStop(EntityEmpEndEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				item.resetData(DataComponentTypes.ITEM_MODEL);
				item.editMeta(m -> m.displayName(getDisplayName()));
			});
		}
	}

	public static class Scanner extends Tool {

		public Scanner(ToolHandler<?> handler) {
			super(handler);
		}
	}
}
