package fr.nekotine.vi6clean.it;

import static fr.nekotine.vi6clean.it.Vi6Asserts.assertNear;
import static fr.nekotine.vi6clean.it.Vi6TestHooks.game;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;

import fr.nekotine.core.bot.Bot;
import fr.nekotine.core.bot.BotRoster;
import fr.nekotine.core.bot.BotState;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.vi6clean.game.phase.Vi6PhaseInfiltration;
import fr.nekotine.vi6clean.tool.Tool;
import fr.nekotine.vi6clean.tool.ToolHandler;
import fr.xxgoldenbluexx.paperjunitserver.PaperServer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * A guard bot drives the Portable Teleporter through real client inputs: it
 * places and picks pads up, teleports between two pads, and receives the glow
 * packets the tool sends.
 */
class PortableTeleporterBotIT {

	private static final String TOOL = "portable_teleporter";

	/** The tool's item, also the material its cooldown is set on. */
	private static final Material TOOL_MATERIAL = Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE;

	/**
	 * Ticks for a control or a click to reach the server and its effect to show.
	 */
	private static final int SETTLE_TICKS = 3;

	/** {@code teleportation_delay: 2.0}, in ticks. */
	private static final int CHANNEL_TICKS = 40;

	/** Distance between the two pads: within block reach, rays apart. */
	private static final int PAD_SPACING = 3;

	/** Bit of the entity flags byte (metadata index 0) that makes it glow. */
	private static final byte GLOW_MASK = 0x40;

	// Each test starts with a reset; this one keeps the next test class clean
	@AfterEach
	void resetForOtherTests() throws Exception {
		Vi6TestHooks.reset();
	}

	@RepeatedTest(3)
	void guardPlacesAndPicksUpAPad() throws Exception {
		Vi6TestHooks.reset();
		try (var capture = new LogCapture()) {
			var bot = setUp();
			var guard = bot.player();
			var tool = Vi6TestHooks.giveTool(guard, TOOL);
			holdTool(bot, tool);
			assertEquals(List.of(), pads(guard), "pads before placing");
			assertEquals("[3/3]", charges(guard, tool), "charges before placing");

			var feet = PaperServer.onMain(() -> guard.getLocation());
			sneakAndClickFloor(bot);
			var placed = pads(guard);
			assertEquals(1, placed.size(), "pads after placing");
			assertPadAt(feet, placed.get(0), "pad not at the guard's feet");
			assertEquals("[2/3]", charges(guard, tool), "charges after placing");

			// Still sneaking and looking down at the pad: the same gesture picks it up
			clickFloor(bot);
			assertEquals(List.of(), pads(guard), "pads after picking up");
			PaperServer.onMain(() -> {
				assertTrue(placed.get(0).isDead() || !placed.get(0).isValid(), "picked-up pad entity still exists");
				return null;
			});
			assertEquals("[3/3]", charges(guard, tool), "charges after picking up");
			assertEquals(List.of(), capture.failures(), "an exception was logged");
			assertEquals(List.of(), capture.errors(), "an error was logged");
		}
	}

	@RepeatedTest(3)
	void guardTeleportsBetweenTwoPadsAndSeesThemGlow() throws Exception {
		Vi6TestHooks.reset();
		try (var capture = new LogCapture()) {
			var bot = setUp();
			var guard = bot.player();
			var tool = Vi6TestHooks.giveTool(guard, TOOL);
			var metadata = new ConcurrentLinkedQueue<MetadataSeen>();
			var recorder = metadataRecorder(guard, metadata);
			PaperServer.onMain(() -> {
				ProtocolLibrary.getProtocolManager().addPacketListener(recorder);
				return null;
			});
			try {
				holdTool(bot, tool);

				// Pad A at the guard's spawn
				var a = PaperServer.onMain(() -> guard.getLocation());
				sneakAndClickFloor(bot);
				var padsAfterA = pads(guard);
				assertEquals(1, padsAfterA.size(), "pads after placing A");
				var padA = padsAfterA.get(0);
				assertPadAt(a, padA, "pad A not at the guard's feet");

				// Pad B on an open floor spot PAD_SPACING blocks away
				var b = PaperServer.onMain(() -> openFloorAround(a));
				Vi6TestHooks.place(guard, b);
				PaperServer.onMain(() -> {
					assertNear(b, guard.getLocation(), "guard not placed on B");
					return null;
				});
				clickFloor(bot);
				var padsAfterB = pads(guard);
				assertEquals(2, padsAfterB.size(), "pads after placing B");
				assertEquals("[1/3]", charges(guard, tool), "charges after placing B");

				// Stand up, then look at A from B
				PaperServer.onMain(() -> {
					bot.setSneak(false);
					return null;
				});
				PaperServer.awaitTicks(SETTLE_TICKS);
				PaperServer.onMain(() -> {
					aimAt(bot, a);
					return null;
				});
				PaperServer.awaitTicks(SETTLE_TICKS);
				var attributesBefore = PaperServer.onMain(() -> movementAttributes(guard));
				var clickTick = PaperServer.onMain(() -> {
					assertFalse(guard.isSneaking(), "guard still sneaking");
					assertFalse(guard.hasCooldown(TOOL_MATERIAL), "teleport cooldown before the click");
					bot.startBreaking(a.clone().subtract(0, 0.1, 0).getBlock(), BlockFace.UP);
					return Bukkit.getCurrentTick();
				});
				PaperServer.awaitTicks(2);

				// The channel started: the bot holds forward and jump, and stays put
				var held = PaperServer.onMain(() -> {
					assertTrue(guard.hasCooldown(TOOL_MATERIAL), "teleport channel did not start");
					bot.setForward(true);
					bot.setJump(true);
					return guard.getLocation();
				});
				assertNear(b, held, "guard moved before the channel");
				// Sampled each tick until a few ticks before the channel can end
				var releaseTick = clickTick + CHANNEL_TICKS - 5;
				var samples = 0;
				while (true) {
					PaperServer.awaitTicks(1);
					var sample = PaperServer.onMain(() -> new Sample(Bukkit.getCurrentTick(), guard.getLocation()));
					if (sample.tick() >= releaseTick) {
						break;
					}
					samples++;
					assertTrue(held.distanceSquared(sample.location()) < 1e-5,
							"guard moved during the channel, " + (sample.tick() - clickTick)
									+ " ticks after the click: " + held.toVector() + " to "
									+ sample.location().toVector());
				}
				assertTrue(samples >= 5, "only " + samples + " position samples during the channel");
				PaperServer.onMain(() -> {
					bot.setForward(false);
					bot.setJump(false);
					return null;
				});

				// The channel ends with a teleport the bot acks
				PaperServer.awaitUntil(() -> Bukkit.getCurrentTick() >= clickTick + CHANNEL_TICKS + 10, 100);
				PaperServer.onMain(() -> {
					assertNear(a, guard.getLocation(), "guard not at pad A after the channel");
					assertTrue(guard.isOnline(), "guard disconnected");
					assertEquals(BotState.ACTIVE, bot.state(), "bot state");
					assertTrue(Ioc.resolve(BotRoster.class).list().contains(bot), "bot left the roster");
					return null;
				});
				PaperServer.awaitTicks(SETTLE_TICKS);
				PaperServer.onMain(() -> {
					assertNear(a, guard.getLocation(), "teleport to pad A rolled back");
					// The channel cleaned up: modifiers removed, teleport cooldown set
					assertEquals(attributesBefore, movementAttributes(guard), "movement attributes after the channel");
					assertTrue(guard.hasCooldown(TOOL_MATERIAL), "no teleport cooldown after the channel");
					bot.setForward(true);
					return null;
				});
				PaperServer.awaitTicks(5);
				PaperServer.onMain(() -> {
					bot.setForward(false);
					var now = guard.getLocation();
					assertTrue(a.toVector().distance(now.toVector()) > 0.1,
							"forward did not move the guard after the channel: still at " + now.toVector());
					return null;
				});

				// The tool, in hand with pads placed, made both pads glow for the bot
				var glowing = metadata.stream().filter(MetadataSeen::glowing).map(MetadataSeen::entityId).distinct()
						.toList();
				for (var pad : padsAfterB) {
					assertTrue(glowing.contains(pad.getEntityId()),
							"no glowing ENTITY_METADATA for pad " + pad.getEntityId() + " sent to the bot, saw "
									+ metadata.stream().filter(m -> m.entityId() == pad.getEntityId()).toList());
				}
			} finally {
				PaperServer.onMain(() -> {
					ProtocolLibrary.getProtocolManager().removePacketListener(recorder);
					return null;
				});
			}
			assertEquals(List.of(), capture.failures(), "an exception was logged");
			assertEquals(List.of(), capture.errors(), "an error was logged");
		}
	}

	/**
	 * Two idle bots, one guard and one thief, in infiltration.
	 *
	 * @return the guard
	 */
	private static Bot setUp() throws Exception {
		var bots = Vi6TestHooks.spawnBots(2);
		var guard = bots.get(0);
		Vi6TestHooks.putOnTeams(List.of(guard.player()), List.of(bots.get(1).player()));
		Vi6TestHooks.goToInfiltration(null);
		// Let the guard land on its spawn
		PaperServer.awaitTicks(10);
		PaperServer.onMain(() -> {
			assertNotNull(game().getPhaseMachine().getPhase(Vi6PhaseInfiltration.class), "not in infiltration");
			var floor = guard.player().getLocation().subtract(0, 0.1, 0).getBlock();
			assertTrue(floor.isSolid(), "guard spawn not on solid floor: " + floor);
			return null;
		});
		return guard;
	}

	/** Selects the hotbar slot holding {@code tool}, as a client does. */
	private static void holdTool(Bot bot, Tool tool) throws Exception {
		PaperServer.onMain(() -> {
			var inventory = bot.player().getInventory();
			var slot = -1;
			for (int i = 0; i < 9; i++) {
				if (isStackOf(inventory.getItem(i), tool)) {
					slot = i;
					break;
				}
			}
			assertTrue(slot >= 0, "tool not in the hotbar");
			bot.setSlot(slot);
			return null;
		});
		PaperServer.awaitTicks(SETTLE_TICKS);
		PaperServer.onMain(() -> {
			assertTrue(isStackOf(bot.player().getInventory().getItemInMainHand(), tool), "tool not in the main hand");
			return null;
		});
	}

	/** Sneaks, looks straight down, then left-clicks the floor under the bot. */
	private static void sneakAndClickFloor(Bot bot) throws Exception {
		PaperServer.onMain(() -> {
			bot.setSneak(true);
			bot.setLook(bot.getLookYaw(), 90);
			return null;
		});
		PaperServer.awaitTicks(SETTLE_TICKS);
		clickFloor(bot);
	}

	/**
	 * Left-clicks the top of the floor block under the bot, then lets it settle.
	 */
	private static void clickFloor(Bot bot) throws Exception {
		PaperServer.onMain(() -> {
			assertTrue(bot.player().isSneaking(), "guard not sneaking");
			var floor = bot.player().getLocation().subtract(0, 0.1, 0).getBlock();
			bot.startBreaking(floor, BlockFace.UP);
			return null;
		});
		PaperServer.awaitTicks(SETTLE_TICKS);
	}

	/** Looks from the bot's eye at {@code target}; main thread only. */
	private static void aimAt(Bot bot, Location target) {
		var eye = bot.player().getEyeLocation();
		var dx = target.getX() - eye.getX();
		var dy = target.getY() - eye.getY();
		var dz = target.getZ() - eye.getZ();
		var yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		var pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		bot.setLook(yaw, pitch);
	}

	/**
	 * @return a spot {@link #PAD_SPACING} blocks from {@code from} along an axis,
	 *         at the same height, on solid floor with room to stand; main thread
	 *         only
	 */
	private static Location openFloorAround(Location from) {
		int[][] offsets = {{PAD_SPACING, 0}, {-PAD_SPACING, 0}, {0, PAD_SPACING}, {0, -PAD_SPACING}};
		for (var offset : offsets) {
			var spot = from.clone().add(offset[0], 0, offset[1]);
			var floor = spot.clone().subtract(0, 0.1, 0).getBlock();
			var feet = spot.getBlock();
			var head = feet.getRelative(BlockFace.UP);
			if (floor.isSolid() && feet.isPassable() && !feet.isLiquid() && head.isPassable() && !head.isLiquid()) {
				return spot;
			}
		}
		throw new IllegalStateException("no open floor " + PAD_SPACING + " blocks around " + from.toVector());
	}

	/** @return the tool's pads: structure-block displays in the guard's world */
	private static List<BlockDisplay> pads(Player guard) throws Exception {
		return PaperServer.onMain(() -> new ArrayList<>(guard.getWorld().getEntitiesByClass(BlockDisplay.class).stream()
				.filter(d -> d.isValid() && d.getBlock().getMaterial() == Material.STRUCTURE_BLOCK).toList()));
	}

	/** @return the {@code [n/3]} part of the tool stack's name, as plain text */
	private static String charges(Player guard, Tool tool) throws Exception {
		return PaperServer.onMain(() -> {
			for (var item : guard.getInventory().getContents()) {
				if (isStackOf(item, tool)) {
					var name = PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
					var open = name.lastIndexOf('[');
					return open < 0 ? name : name.substring(open);
				}
			}
			throw new AssertionError("tool stack not in the guard's inventory");
		});
	}

	private static boolean isStackOf(ItemStack item, Tool tool) {
		if (item == null || item.isEmpty()) {
			return false;
		}
		var pdc = item.getPersistentDataContainer();
		return TOOL.equals(pdc.get(ToolHandler.TOOL_TYPE_KEY, PersistentDataType.STRING))
				&& Integer.valueOf(tool.getId()).equals(pdc.get(ToolHandler.TOOL_ID_KEY, PersistentDataType.INTEGER));
	}

	private static void assertPadAt(Location feet, BlockDisplay pad, String what) throws Exception {
		var at = PaperServer.onMain(() -> pad.getLocation());
		assertNear(feet, at, what);
	}

	/**
	 * @return a listener recording each {@code ENTITY_METADATA} packet sent to
	 *         {@code viewer}, after every other listener changed it
	 */
	private static PacketAdapter metadataRecorder(Player viewer, Queue<MetadataSeen> seen) {
		return new PacketAdapter(Ioc.resolve(JavaPlugin.class), ListenerPriority.MONITOR,
				PacketType.Play.Server.ENTITY_METADATA) {

			@Override
			public void onPacketSending(PacketEvent event) {
				if (!viewer.getUniqueId().equals(event.getPlayer().getUniqueId())) {
					return;
				}
				var packet = event.getPacket();
				var glowing = packet.getDataValueCollectionModifier().read(0).stream().anyMatch(
						v -> v.getIndex() == 0 && v.getRawValue() instanceof Byte flags && (flags & GLOW_MASK) != 0);
				seen.add(new MetadataSeen(packet.getIntegers().read(0), glowing));
			}
		};
	}

	private record MetadataSeen(int entityId, boolean glowing) {
	}

	/** @return gravity, movement speed and jump strength, modifiers applied */
	private static List<Double> movementAttributes(Player player) {
		return List.of(player.getAttribute(Attribute.GRAVITY).getValue(),
				player.getAttribute(Attribute.MOVEMENT_SPEED).getValue(),
				player.getAttribute(Attribute.JUMP_STRENGTH).getValue());
	}

	private record Sample(int tick, Location location) {
	}
}
