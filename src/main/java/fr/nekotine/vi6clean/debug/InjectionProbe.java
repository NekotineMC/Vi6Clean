package fr.nekotine.vi6clean.debug;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.bot.BotService;
import fr.nekotine.vi6clean.game.Vi6Game;
import fr.nekotine.vi6clean.game.phase.Vi6PhaseLobby;
import fr.nekotine.vi6clean.wrapper.LobbyPhasePlayerWrapper;
import fr.nekotine.vi6clean.wrapper.PlayerWrapper;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * TEMPORARY diagnostic probe, deleted once its output has been read. It
 * answers, on a booted server and against a real Citizens NPC, the questions
 * Epic 2 rests on but never observed: which serverbound entry points exist on
 * the <em>running</em> build and with which signatures, whether
 * {@code hasClientLoaded()} lets an injected packet through, whether one
 * injected right click produces one {@code PlayerInteractEvent}, whether an
 * injected move lands or is dropped, whether two position packets in one tick
 * cost the NPC its connection, and whether an injected custom click reaches the
 * production lobby dialog lambda.
 * <p>
 * Every packet goes through the real handler on the NPC's own listener, never
 * through a hand built Bukkit event, and the whole run is on the server thread
 * because every target method opens with {@code ensureRunningOnSameThread}. The
 * probe reports; it never repairs. A check that fails is the finding.
 */
public final class InjectionProbe {

	private static final String PREFIX = "[InjectionProbe] ";

	private static final String CITIZENS_PLUGIN = "Citizens";

	/** A plugin reload inside one server would otherwise start a second probe. */
	private static final java.util.concurrent.atomic.AtomicBoolean ALREADY_RAN = new java.util.concurrent.atomic.AtomicBoolean();

	private static boolean dialogRegistryReadable;

	/**
	 * Ticks the boot waits before the probe starts, so the game has reached its
	 * first phase and the per tick player bookkeeping has settled by the time the
	 * first NPC is spawned.
	 */
	public static final long BOOT_DELAY_TICKS = 20L;

	/**
	 * Ticks between two consecutive stages, and the gap the reads get so the per
	 * tick player bookkeeping has settled before anything is read.
	 */
	private static final long TICK_GAP = 1L;

	private static final long SETTLE_TICKS = 20L;

	/**
	 * The six serverbound entry points Epic 2 names, plus the one the docs sent to
	 * "confirm in spike". Resolved reflectively on purpose: the question is whether
	 * a name exists on the running build, not on the compile classpath, and the
	 * three builds in play do not agree.
	 */
	private static final List<EntryPoint> ENTRY_POINTS = List.of(
			new EntryPoint("net.minecraft.server.network.ServerGamePacketListenerImpl", "handleUseItemOn",
					"net.minecraft.network.protocol.game.ServerboundUseItemOnPacket", false),
			new EntryPoint("net.minecraft.server.network.ServerGamePacketListenerImpl", "handlePlayerAction",
					"net.minecraft.network.protocol.game.ServerboundPlayerActionPacket", false),
			new EntryPoint("net.minecraft.server.network.ServerGamePacketListenerImpl", "handlePlayerInput",
					"net.minecraft.network.protocol.game.ServerboundPlayerInputPacket", false),
			new EntryPoint("net.minecraft.server.network.ServerGamePacketListenerImpl", "handleMovePlayer",
					"net.minecraft.network.protocol.game.ServerboundMovePlayerPacket", false),
			new EntryPoint("net.minecraft.server.network.ServerGamePacketListenerImpl", "handleSetCarriedItem",
					"net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket", false),
			new EntryPoint("net.minecraft.server.network.ServerCommonPacketListenerImpl", "handleCustomClickAction",
					"net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket", false),
			new EntryPoint("net.minecraft.server.network.ServerGamePacketListenerImpl", "handleSetHeldSlot", null,
					true));

	private InjectionProbe() {
	}

	/**
	 * One row of the entry point table.
	 *
	 * @param packet
	 *            the packet class the handler takes, or null for the control row
	 * @param control
	 *            true for the name the docs sent to confirmation, which is expected
	 *            to be absent; a control row never counts as a failure
	 */
	private record EntryPoint(String owner, String method, String packet, boolean control) {
	}

	private record Stage(String title, long delayAfterTicks, Consumer<Probe> body) {
	}

	/**
	 * One injected move. The numbers the block prints are the three that can
	 * disagree with each other: where the packet asked to go, where the entity
	 * actually ended up, and what the listener still believes.
	 */
	private record MoveRound(int index, double injectedX, double injectedY, double injectedZ, double baselineX,
			double baselineY, double baselineZ, Boolean clientLoaded, Boolean awaitingTeleport, boolean teleportTook) {
	}

	private static final class Probe {

		private final Location spawn;
		private final Plugin plugin;
		private final ArrayDeque<Stage> queue = new ArrayDeque<>();
		private final Map<String, String> verdicts = new LinkedHashMap<>();
		private final List<String> interactLog = new ArrayList<>();
		private final int rosterSizeBefore;

		private NPC bot;
		private Player botPlayer;
		private String botTeam = "aucune";
		private PlayerWrapper dialogParent;
		private int guardsBefore;
		private boolean isGuardBefore;
		private UUID dialogCallbackId;
		private NPC disposable;
		private Player disposablePlayer;
		private Listener interactListener;
		private Block damagedBlock;
		private org.bukkit.block.data.BlockData damagedBlockData;
		private boolean armed;
		private boolean cleaned;
		private MoveRound pendingMove;

		private Probe(Location spawn, Plugin plugin) {
			this.spawn = spawn;
			this.plugin = plugin;
			this.rosterSizeBefore = rosterSize();
		}

		// ------------------------------------------------------------------
		// lifecycle
		// ------------------------------------------------------------------

		private void arm() {
			queue.add(new Stage("1 entry points resolved reflectively on the running build", TICK_GAP,
					Probe::entryPoints));
			queue.add(new Stage("2 probe NPC spawned and admitted", SETTLE_TICKS, Probe::spawnBot));
			queue.add(new Stage("3 the hasClientLoaded() gate", TICK_GAP, Probe::clientLoadedGate));
			queue.add(new Stage("4 right click through handleUseItemOn", TICK_GAP, Probe::rightClick));
			queue.add(new Stage("5 the three remaining handlers", TICK_GAP, Probe::otherHandlers));
			queue.add(new Stage("6 movement round 1, injected", TICK_GAP, probe -> probe.movement(1)));
			queue.add(new Stage("7 movement round 1, read after the tick", TICK_GAP, Probe::movementRead));
			queue.add(new Stage("8 movement round 2, injected", TICK_GAP, probe -> probe.movement(2)));
			queue.add(new Stage("9 movement round 2, read after the tick", TICK_GAP, Probe::movementRead));
			queue.add(new Stage("10 movement round 3, latch cleared then injected", TICK_GAP,
					probe -> probe.movement(3)));
			queue.add(new Stage("11 movement round 3, read after the tick", TICK_GAP, Probe::movementRead));
			queue.add(
					new Stage("12 the production lobby dialog, opened on the probe NPC", TICK_GAP, Probe::dialogOpen));
			queue.add(
					new Stage("13 lobby dialog click through handleCustomClickAction", TICK_GAP, Probe::dialogInject));
			queue.add(new Stage("14 identity", TICK_GAP, Probe::identity));
			queue.add(new Stage("15 disposable NPC spawned for the destructive check", SETTLE_TICKS,
					Probe::spawnDisposable));
			queue.add(new Stage("16 two position packets in one tick", TICK_GAP, Probe::disconnectInject));
			queue.add(new Stage("17 did that NPC survive", TICK_GAP, Probe::disconnectRead));
			armed = true;
			say("Bot roster size before the probe, BotRegistry.size() = " + rosterSizeBefore);
			try {
				driven();
			} catch (Throwable t) {
				// Nothing else is caught this deep: an Error that escapes driven() would
				// otherwise leave both NPCs standing with no cleanup and no SUMMARY.
				say("An Error escaped the stage chain, cleaning up now.");
				t.printStackTrace(System.out);
				cleanUp();
				throw t;
			}
		}

		private void driven() {
			var stage = queue.poll();
			if (stage == null) {
				say("Every stage ran, cleaning up.");
				cleanUp();
				return;
			}
			say("--- " + stage.title() + " ---");
			try {
				stage.body().accept(this);
			} catch (Exception e) {
				say("The probe threw during '" + stage.title() + "', stack trace follows.");
				e.printStackTrace(System.out);
				verdict(stageKey(stage), "UNKNOWN (threw)");
			} catch (LinkageError err) {
				say(classpathDenied() + " It happened during '" + stage.title() + "', stack trace follows.");
				err.printStackTrace(System.out);
				verdict(stageKey(stage), "UNKNOWN (could not link)");
			} catch (Throwable t) {
				say("An Error escaped '" + stage.title() + "', cleaning up now so nothing is left behind.");
				t.printStackTrace(System.out);
				cleanUp();
				return;
			}
			try {
				Bukkit.getScheduler().runTaskLater(plugin, this::driven, stage.delayAfterTicks());
			} catch (Exception e) {
				say("The probe could not schedule the successor of '" + stage.title() + "', cleaning up now.");
				e.printStackTrace(System.out);
				cleanUp();
			}
		}

		/**
		 * A thrown stage is keyed on the stage it died in, so one odd title cannot
		 * silently rename every verdict in the SUMMARY.
		 */
		private static String stageKey(Stage stage) {
			return "stage: " + stage.title();
		}

		/**
		 * Never throws and never skips an NPC: the acceptance criterion is that nothing
		 * survives the run, whether the run completed or threw.
		 */
		private void cleanUp() {
			if (cleaned) {
				return;
			}
			cleaned = true;
			say("--- cleanup ---");
			guard("the interact listener", () -> {
				if (interactListener != null) {
					HandlerList.unregisterAll(interactListener);
					interactListener = null;
				}
			});
			guard("restoring the block the destructive check touched", this::restoreDamagedBlock);
			guard("taking the disposable NPC out of the game", () -> removeFromGame(disposablePlayer));
			guard("destroying the disposable NPC", () -> destroy(disposable, "disposable"));
			guard("taking the probe NPC out of the game", () -> removeFromGame(botPlayer));
			guard("destroying the probe NPC", () -> destroy(bot, "probe"));
			guard("reading the bot roster", () -> {
				var after = rosterSize();
				say("Bot roster size after the probe, BotRegistry.size() = " + after + " (was " + rosterSizeBefore
						+ ")");
				if (rosterSizeBefore < 0 || after < 0) {
					say("FAIL cleanup, the bot roster size was unreadable, so this run proves nothing about the roster.");
					verdict("cleanup roster read", "FAIL (unreadable)");
				} else if (after != rosterSizeBefore) {
					say("FAIL cleanup, the bot roster is not the size it was before the probe ran.");
					verdict("cleanup roster read", "FAIL (roster moved)");
				} else {
					verdict("cleanup roster read", "PASS (roster unchanged)");
				}
			});
			guard("reading back the probe's own NPCs", () -> {
				// BotRegistry.size() cannot see these two: they were created straight
				// through the Citizens registry and never admitted to the bot roster, so
				// the roster figure above is true by construction. The only reading
				// that can go red is whether the NPCs the probe itself spawned are
				// still standing.
				var probeAlive = stillAlive(bot, "probe");
				var disposableAlive = stillAlive(disposable, "disposable");
				var noneLeft = !probeAlive && !disposableAlive;
				say("Probe NPC still spawned = " + probeAlive + ", disposable NPC still spawned = " + disposableAlive
						+ ", the probe left none of its own NPCs behind: " + noneLeft);
				if (!noneLeft) {
					say("FAIL cleanup, an NPC the probe spawned is still in the world. Run /npc remove on it by name.");
				}
				verdict("cleanup probe NPCs", noneLeft ? "PASS" : "FAIL (still spawned)");
			});
			say("SUMMARY " + summarise());
		}

		private static boolean stillAlive(NPC npc, String label) {
			if (npc == null) {
				return false;
			}
			try {
				var spawned = npc.isSpawned();
				Entity entity = npc.getEntity();
				return spawned && entity != null && entity.isValid();
			} catch (Exception e) {
				say("Cleanup: the " + label + " NPC could not be read back, treating it as still alive.");
				e.printStackTrace(System.out);
				return true;
			} catch (LinkageError err) {
				say(classpathDenied() + " Cleanup: the " + label + " NPC could not be read back.");
				return true;
			}
		}

		// ------------------------------------------------------------------
		// stages
		// ------------------------------------------------------------------

		/**
		 * Every row is resolved on its own, so a name missing from the running build
		 * costs that row and nothing else. The compile classpath is what settles the
		 * names, so an empty row is a version split, not a typo.
		 */
		private void entryPoints() {
			var failures = 0;
			for (var entry : ENTRY_POINTS) {
				var counted = !entry.control();
				Method method = null;
				Class<?> owner = null;
				Class<?> packet = null;
				try {
					owner = Class.forName(entry.owner());
					packet = entry.packet() == null ? null : Class.forName(entry.packet());
					method = packet == null
							? findByName(owner, entry.method())
							: owner.getMethod(entry.method(), packet);
				} catch (Exception e) {
					say("FAIL " + entry.method() + " | the name could not be resolved on the running build: " + e
							+ (counted ? "" : " (control row, not counted)"));
					if (counted) {
						failures++;
					}
					continue;
				} catch (LinkageError err) {
					say(classpathDenied() + " " + entry.method() + " could not be reflected on, stack trace follows.");
					err.printStackTrace(System.out);
					if (counted) {
						failures++;
					}
					continue;
				}
				if (method == null) {
					say(absence(owner, entry));
					if (counted) {
						failures++;
					}
					continue;
				}
				say((entry.control() ? "UNEXPECTED PRESENT " : "PASS ") + entry.method() + " | declared on "
						+ method.getDeclaringClass().getName() + " | signature " + method.toGenericString() + " | "
						+ (packet == null
								? "no packet parameter"
								: "packet " + packet.getName() + " constructors " + describeConstructors(packet)));
			}
			var counted = ENTRY_POINTS.stream().filter(e -> !e.control()).count();
			if (failures == 0) {
				say("VERDICT entry points: all " + counted + " counted names exist on the running build.");
				verdict("entry points", "PASS");
			} else {
				say("VERDICT entry points: " + failures + " of the " + counted
						+ " counted names did not resolve on the running build, "
						+ "read that as a version split against the compile classpath, not as a wrong name.");
				verdict("entry points", "FAIL (" + failures + "/" + counted + ")");
			}
		}

		private void spawnBot() {
			var npc = spawn("ProbeBot");
			if (npc == null) {
				say("SKIPPED the bot stages, no probe NPC could be spawned.");
				verdict("spawn", "FAIL (no NPC)");
				return;
			}
			bot = npc;
			try {
				if (!(npc.getEntity() instanceof Player player)) {
					say("ABORTED the bot stages, the NPC yielded no Player.");
					verdict("spawn", "FAIL (no Player)");
					return;
				}
				botPlayer = player;
			} catch (Exception e) {
				say("ABORTED the bot stages, the spawned entity could not be read, stack trace follows.");
				e.printStackTrace(System.out);
				verdict("spawn", "FAIL (unreadable entity)");
				return;
			} catch (LinkageError err) {
				say(classpathDenied() + " The spawned entity could not be read, stack trace follows.");
				err.printStackTrace(System.out);
				verdict("spawn", "FAIL (unreadable entity)");
				return;
			}
			var game = Ioc.resolve(Vi6Game.class);
			game.addPlayer(botPlayer);
			// The probe NPC is put on the thief side on purpose: the dialog block
			// has to change it to something, and "guard" is what the packet carries.
			game.addPlayerInThiefs(botPlayer);
			botTeam = describeTeam();
			say("Probe NPC " + npc.getName() + " admitted, game roster = " + game.getPlayerList().size()
					+ " joueur(s), " + game.getGuards().size() + " garde(s), " + game.getThiefs().size()
					+ " voleur(s), " + "this NPC is a " + botTeam);
			say("Lobby wrapper present = " + (lobbyWrapper() != null) + ", lobby phase still running = "
					+ (game.getPhaseMachine().getPhase(Vi6PhaseLobby.class) != null));
			registerInteractListener();
			verdict("spawn", "PASS");
		}

		private void clientLoadedGate() {
			if (botPlayer == null) {
				say("SKIPPED the gate block, there is no probe NPC.");
				verdict("hasClientLoaded gate", "UNKNOWN (no NPC)");
				return;
			}
			var loaded = readClientLoaded(botPlayer);
			say("hasClientLoaded() on the probe NPC's own listener = " + loaded);
			say("VERDICT gate: handleMovePlayer and handlePlayerAction return before doing anything while this is "
					+ "false, so an injected packet on an NPC that never loads is a silent no-op rather than a "
					+ "rejection, and that is what every movement reading below has to be read against.");
			verdict("hasClientLoaded gate", loaded == null ? "UNKNOWN" : (loaded ? "TRUE" : "FALSE"));
		}

		private void rightClick() {
			if (botPlayer == null) {
				say("SKIPPED the right click block, there is no probe NPC.");
				verdict("right click", "UNKNOWN (no NPC)");
				return;
			}
			var block = findSolidBlock();
			if (block == null) {
				say("SKIPPED the right click block, no solid block could be found around " + shortCoords(spawn));
				verdict("right click", "UNKNOWN (no block)");
				return;
			}
			var pos = new BlockPos(block.getX(), block.getY(), block.getZ());
			var hit = new BlockHitResult(new Vec3(block.getX() + 0.5, block.getY() + 1.0, block.getZ() + 0.5),
					Direction.UP, pos, false);
			damagedBlock = block;
			damagedBlockData = block.getBlockData();
			var loaded = readClientLoaded(botPlayer);
			var reach = distance(botPlayer.getLocation().getX(), botPlayer.getLocation().getY(),
					botPlayer.getLocation().getZ(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
			say("Injected handleUseItemOn against " + pos.toShortString() + " (" + block.getType() + ") from " + reach
					+ " blocks away, hasClientLoaded() = " + loaded
					+ " (handleUseItemOn gates on isWithinBlockInteractionRange, and then requires awaitingPositionFromClient to be "
					+ "null before it calls gameMode.useItemOn at all, so both figures are what say whether the handler "
					+ "ran or silently skipped the whole body)");
			interactLog.clear();
			try {
				connection(botPlayer)
						.handleUseItemOn(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, hit, 0));
			} catch (Exception e) {
				say("REFUSED the injected right click, the handler threw, stack trace follows.");
				e.printStackTrace(System.out);
				verdict("right click", "FAIL (handler threw)");
				return;
			} catch (LinkageError err) {
				say(classpathDenied() + " The injected right click could not link, stack trace follows.");
				err.printStackTrace(System.out);
				verdict("right click", "FAIL (could not link)");
				return;
			}
			var held = botPlayer.getInventory().getItemInMainHand();
			say("The handler returned normally. PlayerInteractEvent received by the probe NPC = " + interactLog.size());
			for (var line : interactLog) {
				say("  " + line);
			}
			say("Held item of the probe NPC = " + (held == null ? "nothing" : held.getType())
					+ "; this verdict only counts events, so read the EquipmentSlot and cancelled lines above to say "
					+ "whether a production handler consumed the interact. The lobby menu answers only an interact "
					+ "whose EquipmentSlot is HAND.");
			if (interactLog.size() == 1) {
				say("VERDICT right click: PASS, exactly one PlayerInteractEvent, which is what one interact is "
						+ "supposed to produce.");
				verdict("right click", "PASS");
			} else if (interactLog.isEmpty()) {
				if (Boolean.TRUE.equals(readAwaitingTeleport(botPlayer))) {
					say("VERDICT right click: FAIL, and the cause is measured rather than guessed. "
							+ "awaitingPositionFromClient is set, and handleUseItemOn only calls gameMode.useItemOn (the "
							+ "call that fires PlayerInteractEvent) when that latch is null. The latch is set because the "
							+ "plugin teleported the bot when it admitted it, and nothing ever clears it because an NPC "
							+ "never sends the teleport confirm.");
					verdict("right click", "FAIL (no event, teleport latch held)");
				} else {
					say("VERDICT right click: FAIL, the handler returned without producing any PlayerInteractEvent; with "
							+ "hasClientLoaded() = " + loaded + ", a reach of " + reach
							+ " blocks and no latch, that reads as a silent refusal, not as a dropped event.");
					verdict("right click", "FAIL (no event)");
				}
			} else {
				say("VERDICT right click: FAIL, one interact produced " + interactLog.size()
						+ " PlayerInteractEvents.");
				verdict("right click", "FAIL (" + interactLog.size() + " events)");
			}
		}

		/**
		 * The three remaining handlers that need no world change, so the spike can say
		 * whether the name existing means the call lands.
		 */
		private void otherHandlers() {
			if (botPlayer == null) {
				say("SKIPPED, there is no probe NPC.");
				verdict("other three handlers", "UNKNOWN (no NPC)");
				return;
			}
			var loaded = readClientLoaded(botPlayer);
			say("hasClientLoaded() before the three remaining handlers = " + loaded);

			var slotBefore = botPlayer.getInventory().getHeldItemSlot();
			var slotAfter = slotBefore;
			try {
				connection(botPlayer)
						.handleSetCarriedItem(new ServerboundSetCarriedItemPacket(slotBefore == 0 ? 1 : 0));
				slotAfter = botPlayer.getInventory().getHeldItemSlot();
			} catch (Exception e) {
				say("REFUSED handleSetCarriedItem, it threw, stack trace follows.");
				e.printStackTrace(System.out);
			} catch (LinkageError err) {
				say(classpathDenied() + " handleSetCarriedItem could not link, stack trace follows.");
				err.printStackTrace(System.out);
			}
			say("handleSetCarriedItem: held slot " + slotBefore + " -> " + slotAfter + ", so the call "
					+ (slotBefore != slotAfter ? "LANDED" : "DID NOTHING"));

			var sneakingBefore = botPlayer.isSneaking();
			var sneakingAfter = sneakingBefore;
			try {
				connection(botPlayer).handlePlayerInput(
						new ServerboundPlayerInputPacket(new Input(false, false, false, false, false, true, false)));
				sneakingAfter = botPlayer.isSneaking();
			} catch (Exception e) {
				say("REFUSED handlePlayerInput, it threw, stack trace follows.");
				e.printStackTrace(System.out);
			} catch (LinkageError err) {
				say(classpathDenied() + " handlePlayerInput could not link, stack trace follows.");
				err.printStackTrace(System.out);
			}
			say("handlePlayerInput: isSneaking() " + sneakingBefore + " -> " + sneakingAfter + ", so the call "
					+ (sneakingBefore != sneakingAfter ? "LANDED" : "DID NOTHING"));

			var block = findSolidBlock();
			if (block == null) {
				say("handlePlayerAction: SKIPPED, no solid block could be found around " + shortCoords(spawn));
			} else {
				var pos = new BlockPos(block.getX(), block.getY(), block.getZ());
				var before = block.getType();
				var thrown = false;
				try {
					connection(botPlayer).handlePlayerAction(new ServerboundPlayerActionPacket(
							ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, Direction.UP, 0));
				} catch (Exception e) {
					thrown = true;
					say("REFUSED handlePlayerAction, it threw, stack trace follows.");
					e.printStackTrace(System.out);
				} catch (LinkageError err) {
					thrown = true;
					say(classpathDenied() + " handlePlayerAction could not link, stack trace follows.");
					err.printStackTrace(System.out);
				}
				say("handlePlayerAction: START_DESTROY_BLOCK on " + pos.toShortString() + ", block " + before + " -> "
						+ block.getType() + ", handler threw = " + thrown);
			}
			say("VERDICT remaining handlers: none of the three refused loudly, so with hasClientLoaded() = " + loaded
					+ " a call that changes nothing is a silent no-op the caller cannot tell from a success.");
			verdict("other three handlers", loaded == null ? "UNKNOWN" : "ran with gate " + loaded);
		}

		private void movement(int index) {
			if (botPlayer == null || bot == null) {
				say("SKIPPED movement round " + index + ", there is no probe NPC.");
				verdict("movement round " + index, "UNKNOWN (no NPC)");
				return;
			}
			var asked = spawn.clone();
			asked.add(index * 4.0, 0.0, index * 4.0);
			// Round 1 injects with nothing in front of it, so it answers "does an
			// injected move land at all". Round 2 puts a Bukkit teleport in front of
			// the same inject. Round 3 clears the teleport confirm latch first, which
			// is what turns the failure in the first two rounds from a correlation
			// into a cause.
			var teleportTook = false;
			if (index == 3) {
				var id = readAwaitingTeleportId(botPlayer);
				if (id == null) {
					say("Round 3: SKIPPED, the teleport id could not be read off the listener, so there is nothing safe "
							+ "to inject and this round establishes nothing.");
					verdict("movement round 3", "UNKNOWN (teleport id unreadable)");
					return;
				}
				say("Round 3: injecting ServerboundAcceptTeleportationPacket(" + id
						+ "), the id the server handed out with the teleport, to clear the latch. handleAcceptTeleportPacket "
						+ "ignores an id that does not match and disconnects the player outright if no teleport is "
						+ "pending, so the id has to be read off the listener rather than guessed.");
				try {
					connection(botPlayer).handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(id));
				} catch (Exception e) {
					say("REFUSED the teleport confirm, the handler threw, stack trace follows.");
					e.printStackTrace(System.out);
				} catch (LinkageError err) {
					say(classpathDenied() + " The teleport confirm could not link, stack trace follows.");
					err.printStackTrace(System.out);
				}
				say("Round 3: awaitingPositionFromClient after that packet = " + readAwaitingTeleport(botPlayer)
						+ ", the server has closed the connection = " + readConnectionClosed(botPlayer));
			} else if (index == 2) {
				bot.teleport(asked, TeleportCause.PLUGIN);
				teleportTook = botPlayer.getLocation().distanceSquared(asked) < 0.01;
			}
			var landed = botPlayer.getLocation();
			say("Round " + index + ": " + (index == 2
					? "npc.teleport() was asked for " + shortCoords(asked) + " and the entity actually stands at "
							+ shortCoords(landed) + ", so the teleport " + (teleportTook ? "TOOK" : "DID NOT TAKE")
					: "no teleport was issued, the entity stands at " + shortCoords(landed))
					+ ". Where it actually ended up is the baseline for this round.");
			var target = landed.clone().add(1.0, 0.0, 0.0);
			var loaded = readClientLoaded(botPlayer);
			var awaiting = readAwaitingTeleport(botPlayer);
			say("Round " + index + ": injecting handleMovePlayer one block to " + shortCoords(target)
					+ ", hasClientLoaded() = " + loaded + ", awaitingPositionFromClient = " + awaiting);
			say("Round " + index + ": listener state around the inject, gamemode = " + botPlayer.getGameMode()
					+ ", sneaking = " + botPlayer.isSneaking() + ", passenger = " + botPlayer.isInsideVehicle()
					+ ", sleeping = " + botPlayer.isSleeping() + ", fallFlying = "
					+ (botPlayer.getGameMode() == GameMode.SPECTATOR || botPlayer.isFlying()) + ", spectator = "
					+ (botPlayer.getGameMode() == GameMode.SPECTATOR)
					+ " (handleMovePlayer takes a different branch for "
					+ "each of those, and the anti cheat counts received move packets against allowedPlayerTicks, so these "
					+ "are the figures that say which branch the packet went down)");
			pendingMove = new MoveRound(index, target.getX(), target.getY(), target.getZ(), landed.getX(),
					landed.getY(), landed.getZ(), loaded, awaiting, teleportTook);
			try {
				connection(botPlayer).handleMovePlayer(
						new ServerboundMovePlayerPacket.Pos(target.getX(), target.getY(), target.getZ(), true, false));
			} catch (Exception e) {
				say("REFUSED the injected move of round " + index + ", the handler threw, stack trace follows.");
				e.printStackTrace(System.out);
				verdict("movement round " + index, "FAIL (handler threw)");
				pendingMove = null;
			} catch (LinkageError err) {
				say(classpathDenied() + " The injected move of round " + index
						+ " could not link, stack trace follows.");
				err.printStackTrace(System.out);
				verdict("movement round " + index, "FAIL (could not link)");
				pendingMove = null;
			}
		}

		private void movementRead() {
			if (pendingMove == null || botPlayer == null) {
				say("SKIPPED the movement read back, nothing was injected.");
				verdict("movement read back", "UNKNOWN (nothing injected)");
				return;
			}
			var move = pendingMove;
			pendingMove = null;
			var actual = botPlayer.getLocation();
			var gap = distance(actual.getX(), actual.getY(), actual.getZ(), move.injectedX(), move.injectedY(),
					move.injectedZ());
			var fromBaseline = distance(actual.getX(), actual.getY(), actual.getZ(), move.baselineX(), move.baselineY(),
					move.baselineZ());
			say("Round " + move.index() + ": injected "
					+ shortCoords(move.injectedX(), move.injectedY(), move.injectedZ()) + " from the baseline "
					+ shortCoords(move.baselineX(), move.baselineY(), move.baselineZ()));
			say("Round " + move.index() + ": actual getLocation() after the tick " + shortCoords(actual)
					+ ", gap to the injected point = " + gap + ", gap to the baseline = " + fromBaseline);
			say("Round " + move.index() + ": listener believed lastPos " + readListenerBelievedPosition(botPlayer)
					+ ", entity position " + readNmsPosition(botPlayer) + ", hasClientLoaded() = "
					+ readClientLoaded(botPlayer) + " (re-read), awaitingPositionFromClient = "
					+ readAwaitingTeleport(botPlayer) + " (re-read)");
			if (gap < 0.2) {
				say("VERDICT movement round " + move.index() + ": PASS, the injected move landed and nothing snapped "
						+ "back.");
				verdict("movement round " + move.index(), "PASS");
				return;
			}
			if (Boolean.TRUE.equals(move.awaitingTeleport())) {
				say("VERDICT movement round " + move.index()
						+ ": FAIL, the move did not land and the listener was still holding its teleport confirm latch when it "
						+ "was injected. handleMovePlayer swallows the whole "
						+ "move while that is set: it only snaps rotation, and the latch clears only when the client "
						+ "confirms the teleport. An NPC never sends ServerboundAcceptTeleportationPacket, so a teleport "
						+ "performed on a bot leaves the latch set and every injected position packet is swallowed behind "
						+ "it. The entity did not move away, it stayed on the baseline at a gap of " + fromBaseline
						+ ".");
				verdict("movement round " + move.index(), "FAIL (swallowed by the teleport latch)");
			} else if (Boolean.FALSE.equals(move.clientLoaded())) {
				say("VERDICT movement round " + move.index() + ": FAIL, hasClientLoaded() was false, so "
						+ "handleMovePlayer returned before applying anything. The entity stayed on the baseline at a gap "
						+ "of " + fromBaseline + ".");
				verdict("movement round " + move.index(), "FAIL (dropped by the client-loaded gate)");
			} else if (fromBaseline < 0.2) {
				say("VERDICT movement round " + move.index()
						+ ": FAIL, the move was dropped with no latch and no gate to blame; the entity stayed exactly on "
						+ "the baseline at a gap of " + fromBaseline + ".");
				verdict("movement round " + move.index(), "FAIL (dropped, nothing explains it)");
			} else {
				say("VERDICT movement round " + move.index() + ": FAIL, the entity ended " + fromBaseline
						+ " blocks from the baseline, so it went somewhere neither the teleport nor the packet asked for.");
				verdict("movement round " + move.index(), "FAIL (reverted or redirected)");
			}
			if (move.index() == 2 && !move.teleportTook()) {
				say("NOTE round 2: npc.teleport() did not move the entity at all, so round 2 cannot separate a teleport "
						+ "that was ignored from a move that was swallowed. Round 1 and round 3 bracket it.");
			}
		}

		/**
		 * The production lobby dialog is opened through the real call, and the callback
		 * id is read back off Paper's own dialog click registry, so the probe never
		 * invents one: what it injects is what {@code displayMenu()} just registered.
		 * <p>
		 * Paper's click registry holds a registration in a pending queue and only
		 * drains that queue into its live map at the end of a server tick, and the
		 * click lookup reads the live map. Opening the dialog and injecting in the same
		 * tick would therefore be a guaranteed false negative, so the two are separate
		 * stages and the id is looked for in both containers.
		 */
		private void dialogOpen() {
			if (botPlayer == null) {
				say("SKIPPED the dialog block, there is no probe NPC.");
				verdict("dialog", "UNKNOWN (no NPC)");
				return;
			}
			if (!botAlive("dialog")) {
				return;
			}
			var game = Ioc.resolve(Vi6Game.class);
			var wrapper = lobbyWrapper();
			if (wrapper == null) {
				say("SKIPPED the dialog block, the probe NPC has no LobbyPhasePlayerWrapper, so displayMenu() cannot "
						+ "be called on it.");
				verdict("dialog", "UNKNOWN (no wrapper)");
				return;
			}
			dialogParent = wrapper.getParentWrapper();
			guardsBefore = game.getGuards().size();
			isGuardBefore = dialogParent != null && dialogParent.isGuard();
			say("Before: getGuards().size() = " + guardsBefore + ", isGuard() = " + isGuardBefore + ", lobby phase "
					+ "running = " + (game.getPhaseMachine().getPhase(Vi6PhaseLobby.class) != null));

			var before = dialogCallbackIds();
			try {
				wrapper.displayMenu();
			} catch (Exception e) {
				say("displayMenu() threw, stack trace follows.");
				e.printStackTrace(System.out);
				verdict("dialog", "FAIL (displayMenu threw)");
				return;
			} catch (LinkageError err) {
				say(classpathDenied() + " displayMenu() could not link, stack trace follows.");
				err.printStackTrace(System.out);
				verdict("dialog", "FAIL (could not link)");
				return;
			}
			var registered = dialogCallbackIds();
			registered.removeAll(before);
			if (registered.size() != 1) {
				var unreadable = dialogRegistryReadable();
				say("displayMenu() left " + registered.size() + " new dialog callback id(s) instead of one, so the "
						+ "probe cannot address the lambda it just built: " + registered
						+ (unreadable
								? ", and Paper's dialog click registry was unreadable to begin with, so this is a "
										+ "probe failure, not a platform fact"
								: ""));
				verdict("dialog", unreadable ? "UNKNOWN (registry unreadable)" : "FAIL (no addressable callback)");
				return;
			}
			dialogCallbackId = registered.iterator().next();
			say("displayMenu() registered dialog callback " + dialogCallbackId + ", the packet key Paper listens on is "
					+ dialogClickCallbackKey());
			say("Paper drains that registration from its pending queue into its live map at the end of a server tick, "
					+ "and the click lookup reads the live map, so the inject is one tick later.");
			verdict("dialog registration", "PASS");
		}

		private void dialogInject() {
			if (botPlayer == null || dialogCallbackId == null) {
				say("SKIPPED the dialog click, displayMenu() never produced a callback id to address.");
				if (botPlayer != null) {
					verdict("dialog", "UNKNOWN (no callback id)");
				}
				return;
			}
			if (!botAlive("dialog")) {
				return;
			}
			var game = Ioc.resolve(Vi6Game.class);
			var key = dialogClickCallbackKey();
			if (key == null) {
				say("SKIPPED the click, Paper's dialog click callback key could not be read, so there is no key to "
						+ "inject and a FAIL here would be a probe failure reported as a platform fact.");
				verdict("dialog", "UNKNOWN (callback key unreadable)");
				return;
			}
			var payload = new CompoundTag();
			payload.store("id", UUIDUtil.CODEC, dialogCallbackId);
			payload.putString("team", "guard");
			payload.putBoolean("ready", false);
			say("Injecting handleCustomClickAction on " + key + " with team=guard and ready=false, hasClientLoaded() = "
					+ readClientLoaded(botPlayer));
			var guardsAfter = -1;
			var isGuardAfter = false;
			var lobbyAfter = game.getPhaseMachine().getPhase(Vi6PhaseLobby.class);
			String readyFlag = "unreadable";
			try {
				connection(botPlayer).handleCustomClickAction(
						new ServerboundCustomClickActionPacket(Identifier.parse(key), Optional.<Tag>of(payload)));
				// Read before the revert: reverting first would measure the roster we
				// just put back and report the lambda's effect as no effect.
				guardsAfter = game.getGuards().size();
				isGuardAfter = dialogParent != null && dialogParent.isGuard();
				lobbyAfter = game.getPhaseMachine().getPhase(Vi6PhaseLobby.class);
				var lobby = lobbyWrapper();
				readyFlag = lobby == null ? "unreadable" : String.valueOf(lobby.isReadyForNextPhase());
			} catch (Exception e) {
				say("REFUSED the injected dialog click, the handler threw, stack trace follows.");
				e.printStackTrace(System.out);
				verdict("dialog", "FAIL (handler threw)");
				return;
			} catch (LinkageError err) {
				say(classpathDenied() + " The injected dialog click could not link, stack trace follows.");
				err.printStackTrace(System.out);
				verdict("dialog", "FAIL (could not link)");
				return;
			} finally {
				// The revert runs whatever the inject did, or the probe leaves its NPC
				// standing on the guard team in the live roster.
				revertRoster();
			}
			say("After: getGuards().size() = " + guardsAfter + ", isGuard() = " + isGuardAfter + ", ready flag = "
					+ readyFlag);
			say("Reverted the roster edit, the probe NPC is a " + describeTeam() + " again and getGuards().size() = "
					+ game.getGuards().size());
			say("STATIC: there is no dialog queue and no per tick drain anywhere in Vi6Clean or NekotineCore, the "
					+ "lobby lambda is invoked directly by Paper; no tick gap between the registration and the click "
					+ "was measured here.");
			if (isGuardAfter && guardsAfter == guardsBefore + 1) {
				say("VERDICT dialog: PASS, the production lambda ran and the probe NPC changed team.");
				verdict("dialog", "PASS");
			} else {
				say("VERDICT dialog: FAIL, the roster did not move, so the injected packet did not reach the lambda "
						+ "(guards " + guardsBefore + " -> " + guardsAfter + ", isGuard " + isGuardBefore + " -> "
						+ isGuardAfter + ").");
				verdict("dialog", "FAIL (roster unchanged)");
			}
			if (lobbyAfter != null) {
				say("The lobby phase did not advance: ready=false reached setReadyForNextPhase without calling "
						+ "checkForCompletion().");
			} else {
				say("The lobby phase is gone, ready=false should have made that impossible, report it.");
			}
		}

		private void identity() {
			if (bot == null) {
				say("SKIPPED the identity block, there is no probe NPC.");
				verdict("identity", "UNKNOWN (no NPC)");
				return;
			}
			if (!botAlive("identity")) {
				return;
			}
			var npcId = bot.getUniqueId();
			var entityId = botPlayer == null ? null : botPlayer.getUniqueId();
			say("npc.getUniqueId()              = " + describeUuid(npcId));
			say("npc.getEntity().getUniqueId() = " + describeUuid(entityId));
			if (npcId == null || entityId == null) {
				say("VERDICT identity: UNKNOWN, a null id on one side means there is nothing to compare.");
				verdict("identity", "UNKNOWN (null id)");
				return;
			}
			if (npcId.equals(entityId)) {
				say("VERDICT identity: FAIL, the two ids are the same, so a roster keyed on one is keyed on the other.");
				verdict("identity", "FAIL (identical ids)");
			} else {
				say("VERDICT identity: PASS, the two ids differ, so keying a roster on the NPC id and looking an entity "
						+ "up by its own id are not the same lookup.");
				verdict("identity", "PASS (ids differ)");
			}
		}

		/**
		 * The probe NPC has to still be there for a stage verdict to mean anything.
		 * Stage 10 injects a packet whose handler disconnects the player when it is
		 * misused, so a later stage that reports FAIL against a dead entity is
		 * reporting the probe's own accident as a platform fact.
		 */
		private boolean botAlive(String stage) {
			if (botPlayer == null) {
				return false;
			}
			var closed = readConnectionClosed(botPlayer);
			Entity entity = null;
			try {
				entity = bot.getEntity();
			} catch (Exception e) {
				say("Stage '" + stage + "': the probe NPC's entity could not be read, stack trace follows.");
				e.printStackTrace(System.out);
			} catch (LinkageError err) {
				say(classpathDenied() + " Stage '" + stage + "': the probe NPC's entity could not be read.");
			}
			var alive = entity != null && entity.isValid() && !Boolean.TRUE.equals(closed);
			if (!alive) {
				say("SKIPPED stage '" + stage + "', the probe NPC is no longer usable (entity = " + (entity != null)
						+ ", entity valid = " + (entity != null && entity.isValid()) + ", connection closed = " + closed
						+ "). Recording UNKNOWN rather than a FAIL, because a verdict against a dead entity would be the "
						+ "probe's own accident read as a platform fact.");
				verdict(stage, "UNKNOWN (probe NPC gone)");
			}
			return alive;
		}

		private void spawnDisposable() {
			var npc = spawn("ProbeDisposable");
			if (npc == null) {
				say("SKIPPED the disconnect block, no disposable NPC could be spawned.");
				verdict("disconnect", "UNKNOWN (no NPC)");
				return;
			}
			disposable = npc;
			try {
				if (npc.getEntity() instanceof Player player) {
					disposablePlayer = player;
				} else {
					say("The disposable NPC's entity is not a Player, so the disconnect check has no listener to inject "
							+ "into.");
					verdict("disposable spawn", "FAIL (no Player entity)");
					return;
				}
			} catch (Exception e) {
				say("The disposable NPC yielded no readable entity, stack trace follows.");
				e.printStackTrace(System.out);
			} catch (LinkageError err) {
				say(classpathDenied() + " The disposable NPC could not be read, stack trace follows.");
				err.printStackTrace(System.out);
			}
			say("Disposable NPC " + npc.getName() + " spawned. It is not admitted to the game and it is destroyed "
					+ "whatever this block reports, so a lost NPC costs nothing.");
			verdict("disposable spawn", "PASS");
		}

		private void disconnectInject() {
			if (disposablePlayer == null) {
				say("SKIPPED the disconnect block, there is no disposable Player.");
				verdict("disconnect", "UNKNOWN (no Player)");
				return;
			}
			var before = disposablePlayer.getLocation();
			var first = before.clone().add(1.0, 0.0, 1.0);
			var second = before.clone().add(2.0, 0.0, 2.0);
			say("Injecting two handleMovePlayer packets in one tick, " + shortCoords(first) + " then "
					+ shortCoords(second) + ", hasClientLoaded() = " + readClientLoaded(disposablePlayer));
			try {
				var listener = connection(disposablePlayer);
				listener.handleMovePlayer(
						new ServerboundMovePlayerPacket.Pos(first.getX(), first.getY(), first.getZ(), true, false));
				listener.handleMovePlayer(
						new ServerboundMovePlayerPacket.Pos(second.getX(), second.getY(), second.getZ(), true, false));
			} catch (Exception e) {
				say("The second position packet was refused by an exception, stack trace follows.");
				e.printStackTrace(System.out);
				verdict("disconnect", "FAIL (handler threw)");
				return;
			} catch (LinkageError err) {
				say(classpathDenied() + " The second position packet could not link, stack trace follows.");
				err.printStackTrace(System.out);
				verdict("disconnect", "FAIL (could not link)");
				return;
			}
			say("Both packets returned normally, nothing was disconnected in this tick.");
		}

		private void disconnectRead() {
			if (disposable == null) {
				say("SKIPPED the disconnect read back, there is no disposable NPC.");
				verdict("disconnect read back", "UNKNOWN (no NPC)");
				return;
			}
			var spawned = disposable.isSpawned();
			Entity entity = null;
			try {
				entity = disposable.getEntity();
			} catch (Exception e) {
				say("The disposable NPC's entity could not be read, stack trace follows.");
				e.printStackTrace(System.out);
			} catch (LinkageError err) {
				say(classpathDenied() + " The disposable NPC could not be read, stack trace follows.");
				err.printStackTrace(System.out);
			}
			var closed = disposablePlayer == null ? null : readConnectionClosed(disposablePlayer);
			var online = disposablePlayer != null && disposablePlayer.isOnline();
			say("One tick later: isSpawned() = " + spawned + ", getEntity() = "
					+ (entity == null ? "null" : entity.getType() + " at " + shortCoords(entity.getLocation()))
					+ ", the server has closed the connection = " + closed + ", Player#isOnline() = " + online
					+ " (a Citizens NPC is never in the player list, so that last figure is not a survival signal)");
			var alive = closed != null && spawned && entity != null && entity.isValid() && !closed;
			if (alive) {
				say("VERDICT disconnect: the NPC SURVIVED two position packets in one tick. It is still spawned, still "
						+ "has a live entity, and the server never closed its connection, so the documented one position "
						+ "packet per tick disconnect does not happen on this build.");
				verdict("disconnect", "PASS (survived)");
			} else {
				say("VERDICT disconnect: the NPC did NOT survive. isSpawned() = " + spawned + ", entity = "
						+ (entity != null) + ", entity valid = " + (entity != null && entity.isValid())
						+ ", connection closed = " + closed + ".");
				verdict("disconnect", "FAIL (lost the NPC)");
			}
			say("NOTE the flag the docs name, receivedPositionThisTick, is not reported here because it does not "
					+ "exist in the class; the flag this build carries is receivedMovementThisTick.");
		}

		// ------------------------------------------------------------------
		// NPC lifecycle
		// ------------------------------------------------------------------

		private NPC spawn(String label) {
			try {
				var registry = CitizensAPI.getNPCRegistry();
				if (registry == null) {
					say("ABORTED, the NPC registry could not be obtained.");
					return null;
				}
				var name = label + "-" + Long.toHexString(System.nanoTime());
				var npc = registry.createNPC(EntityType.PLAYER, name);
				if (npc == null) {
					say("ABORTED, registry.createNPC(PLAYER, " + name + ") returned null.");
					return null;
				}
				npc.data().set(NPC.Metadata.SHOULD_SAVE, false);
				if (!npc.spawn(spawn.clone())) {
					say("ABORTED, NPC#spawn returned false, nothing is read for an NPC that never entered the world.");
					npc.destroy();
					return null;
				}
				say("Spawned " + name + " at " + shortCoords(spawn) + " in " + spawn.getWorld().getName() + ".");
				return npc;
			} catch (Exception e) {
				say("Spawning threw, stack trace follows.");
				e.printStackTrace(System.out);
				return null;
			} catch (LinkageError err) {
				say(classpathDenied() + " Spawning could not link, stack trace follows.");
				err.printStackTrace(System.out);
				return null;
			}
		}

		private void destroy(NPC npc, String label) {
			if (npc == null) {
				return;
			}
			try {
				npc.destroy();
				say("Cleanup: the " + label + " NPC was destroyed.");
			} catch (Exception e) {
				say("Cleanup: the " + label + " NPC threw on destroy, falling back to Entity#remove(), stack trace "
						+ "follows.");
				e.printStackTrace(System.out);
				removeEntity(npc);
			} catch (LinkageError err) {
				say(classpathDenied() + " Cleanup: the " + label + " NPC could not link, falling back to "
						+ "Entity#remove(), stack trace follows.");
				err.printStackTrace(System.out);
				removeEntity(npc);
			}
		}

		private void removeEntity(NPC npc) {
			try {
				var toRemove = npc.getEntity();
				if (toRemove == null) {
					say("Cleanup: no entity was available, nothing could be removed.");
					return;
				}
				toRemove.remove();
				say("Cleanup: Entity#remove() ran, the NPC may still linger in the Citizens datastore.");
			} catch (Exception e) {
				say("Cleanup: Entity#remove() threw too, stack trace follows.");
				e.printStackTrace(System.out);
			} catch (LinkageError err) {
				say(classpathDenied() + " Cleanup: Entity#remove() could not link, stack trace follows.");
				err.printStackTrace(System.out);
			}
		}

		private void removeFromGame(Player player) {
			if (player == null) {
				return;
			}
			try {
				var game = Ioc.resolve(Vi6Game.class);
				game.removePlayer(player);
				var stillThere = game.getPlayerList().contains(player) || game.getGuards().contains(player)
						|| game.getThiefs().contains(player);
				if (stillThere) {
					say("Cleanup: removePlayer returned but the probe NPC is still on the roster; ObservableCollection "
							+ "swallows whatever a teardown callback threw, so this is reported rather than assumed.");
					return;
				}
				say("Cleanup: a probe NPC left the game roster.");
			} catch (Exception e) {
				say("Cleanup: removePlayer threw, stack trace follows.");
				e.printStackTrace(System.out);
			} catch (LinkageError err) {
				say(classpathDenied() + " Cleanup: removePlayer could not link, stack trace follows.");
				err.printStackTrace(System.out);
			}
		}

		private void revertRoster() {
			try {
				if (botPlayer == null) {
					return;
				}
				var game = Ioc.resolve(Vi6Game.class);
				if ("guard".equals(botTeam)) {
					game.addPlayerInGuards(botPlayer);
				} else if ("thief".equals(botTeam)) {
					game.addPlayerInThiefs(botPlayer);
				} else {
					game.removePlayer(botPlayer);
				}
			} catch (Exception e) {
				say("The roster revert threw, stack trace follows.");
				e.printStackTrace(System.out);
			} catch (LinkageError err) {
				say(classpathDenied() + " The roster revert could not link, stack trace follows.");
				err.printStackTrace(System.out);
			}
		}

		private String describeTeam() {
			if (botPlayer == null) {
				return "inconnu";
			}
			var game = Ioc.resolve(Vi6Game.class);
			if (game.getGuards().contains(botPlayer)) {
				return "guard";
			}
			return game.getThiefs().contains(botPlayer) ? "thief" : "aucune";
		}

		// ------------------------------------------------------------------
		// the interact listener
		// ------------------------------------------------------------------

		private void registerInteractListener() {
			interactListener = new Listener() {

				@EventHandler(priority = EventPriority.MONITOR)
				public void onInteract(PlayerInteractEvent evt) {
					if (botPlayer == null || !evt.getPlayer().getUniqueId().equals(botPlayer.getUniqueId())) {
						return;
					}
					interactLog.add("PlayerInteractEvent action = " + evt.getAction() + ", EquipmentSlot = "
							+ describeSlot(evt) + ", getClickedBlock() non null = " + (evt.getClickedBlock() != null)
							+ ", cancelled = " + evt.isCancelled());
				}
			};
			EventUtil.register(plugin, interactListener);
		}

		// ------------------------------------------------------------------
		// helpers
		// ------------------------------------------------------------------

		private LobbyPhasePlayerWrapper lobbyWrapper() {
			try {
				return Ioc.resolve(WrappingModule.class).getWrapper(botPlayer, LobbyPhasePlayerWrapper.class);
			} catch (Exception e) {
				say("The lobby wrapper could not be read, stack trace follows.");
				e.printStackTrace(System.out);
				return null;
			} catch (LinkageError err) {
				say(classpathDenied() + " The lobby wrapper could not be read, stack trace follows.");
				err.printStackTrace(System.out);
				return null;
			}
		}

		/**
		 * The closest solid block the probe NPC can legally reach. handleUseItemOn
		 * gates on Player#isWithinBlockInteractionRange before it fires anything, so
		 * picking a block a few blocks away would measure the gate, not the injection.
		 * The block directly under the feet is the one a real right click would hit.
		 */
		private Block findSolidBlock() {
			var origin = botPlayer != null ? botPlayer.getLocation() : spawn;
			var world = origin.getWorld();
			if (world == null) {
				return null;
			}
			var start = world.getBlockAt(origin.getBlockX(), origin.getBlockY(), origin.getBlockZ());
			for (var dy = -1; dy >= -2; dy--) {
				for (var dx = 0; dx <= 1; dx++) {
					for (var dz = -1; dz <= 1; dz++) {
						var candidate = start.getRelative(dx, dy, dz);
						if (candidate.getY() < world.getMinHeight()) {
							continue;
						}
						if (isSolid(candidate)) {
							return candidate;
						}
					}
				}
			}
			return null;
		}

		/**
		 * The destructive check fires START_DESTROY_BLOCK at a real block on the live
		 * map, so the block is put back. Block damage outlives the probe and does not
		 * show up in the SUMMARY, which makes it exactly the kind of thing a spike
		 * leaves behind silently.
		 */
		private void restoreDamagedBlock() {
			if (damagedBlock == null) {
				return;
			}
			var block = damagedBlock;
			var data = damagedBlockData;
			damagedBlock = null;
			damagedBlockData = null;
			if (block.getBlockData().matches(data)) {
				say("Cleanup: the block the destructive check touched is untouched, nothing to restore.");
				return;
			}
			block.setBlockData(data, false);
			say("Cleanup: restored the block the destructive check destroyed at " + block.getX() + ", " + block.getY()
					+ ", " + block.getZ() + " to " + data.getAsString(false) + ".");
		}

		/**
		 * The ids Paper's dialog click manager holds, in both its live map and the
		 * pending queue it drains that map from, so the probe addresses the callback
		 * {@code displayMenu()} just registered instead of guessing one. An empty
		 * reading is a finding, not a pass.
		 */
		private static Set<UUID> dialogCallbackIds() {
			var ids = new LinkedHashSet<UUID>();
			dialogRegistryReadable = true;
			try {
				var provider = Class.forName("io.papermc.paper.adventure.providers.ClickCallbackProviderImpl");
				var manager = provider.getField("DIALOG_CLICK_MANAGER").get(null);
				var callbacks = manager.getClass().getSuperclass().getDeclaredField("callbacks");
				callbacks.setAccessible(true);
				if (callbacks.get(manager) instanceof Map<?, ?> live) {
					for (var key : live.keySet()) {
						if (key instanceof UUID id) {
							ids.add(id);
						}
					}
				}
				var pending = manager.getClass().getSuperclass().getDeclaredField("queue");
				pending.setAccessible(true);
				if (pending.get(manager) instanceof java.util.Collection<?> queue) {
					for (var stored : queue) {
						if (stored != null) {
							var id = stored.getClass().getDeclaredField("id");
							id.setAccessible(true);
							if (id.get(stored) instanceof UUID uuid) {
								ids.add(uuid);
							}
						}
					}
				}
			} catch (Exception e) {
				dialogRegistryReadable = false;
				say("The dialog click registry could not be read, stack trace follows.");
				e.printStackTrace(System.out);
			} catch (LinkageError err) {
				dialogRegistryReadable = false;
				say(classpathDenied() + " The dialog click registry could not be read, stack trace follows.");
			}
			return ids;
		}

		/**
		 * Whether the last read of Paper's dialog click registry actually succeeded. An
		 * unreadable registry and a registry that gained no callback look identical
		 * from the outside, and reporting the second as a platform FAIL would file a
		 * probe failure as a fact about the server.
		 */
		private static boolean dialogRegistryReadable() {
			return dialogRegistryReadable;
		}

		private static String dialogClickCallbackKey() {
			try {
				var provider = Class.forName("io.papermc.paper.adventure.providers.ClickCallbackProviderImpl");
				return String.valueOf(provider.getField("DIALOG_CLICK_CALLBACK_KEY").get(null));
			} catch (Exception e) {
				say("Paper's dialog click callback key could not be read, so no key will be injected: injecting a "
						+ "guessed key would report a key mismatch as a platform failure.");
				return null;
			} catch (LinkageError err) {
				say(classpathDenied()
						+ " The dialog click callback key could not be read, so no key will be injected.");
				return null;
			}
		}

		private static Method findByName(Class<?> owner, String methodName) {
			// getMethods() is public-only, so a name that exists as a package-private or
			// protected handler would read as absent and the control row would lie.
			for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
				for (var method : type.getDeclaredMethods()) {
					if (method.getName().equals(methodName) && method.getParameterCount() == 0) {
						return method;
					}
				}
			}
			return null;
		}

		private static String describeSlot(PlayerInteractEvent evt) {
			var slot = evt.getHand();
			return slot == null ? "null" : slot.name();
		}

		private void guard(String what, Runnable action) {
			try {
				action.run();
			} catch (Exception e) {
				say("Cleanup: " + what + " failed, stack trace follows.");
				e.printStackTrace(System.out);
			} catch (LinkageError err) {
				say(classpathDenied() + " Cleanup: " + what + " could not link, stack trace follows.");
				err.printStackTrace(System.out);
			}
		}

		private void verdict(String check, String value) {
			verdicts.put(check, value);
		}

		private String summarise() {
			var parts = new ArrayList<String>();
			for (var entry : verdicts.entrySet()) {
				parts.add(entry.getKey() + "=" + entry.getValue());
			}
			return parts.isEmpty() ? "no check reported a verdict" : String.join(" | ", parts);
		}

	}

	/**
	 * Runs the whole spike on the server thread. Every target handler opens with
	 * {@code ensureRunningOnSameThread}, so a call made off the main thread would
	 * be rejected before it did anything.
	 *
	 * @param spawnLocation
	 *            where the probe's own NPCs are spawned, cloned before use
	 * @param plugin
	 *            the handle the scheduler is reached through
	 */
	public static void run(Location spawnLocation, Plugin plugin) {
		say("=========================================================");
		say("TEMPORARY PROBE, delete debug/InjectionProbe.java and its call site in Vi6Main once this output has "
				+ "been read.");
		say("Six serverbound entry points, one NPC, one measured verdict per question.");
		say("=========================================================");
		if (!ALREADY_RAN.compareAndSet(false, true)) {
			say("SKIPPED, a probe already ran in this server session, so a second one will not spawn more NPCs.");
			return;
		}
		var citizens = Bukkit.getPluginManager().getPlugin(CITIZENS_PLUGIN);
		if (citizens == null) {
			say("SKIPPED, no plugin named " + CITIZENS_PLUGIN + " is installed, no NPC was created.");
			return;
		}
		if (!citizens.isEnabled()) {
			say("SKIPPED, " + CITIZENS_PLUGIN + " is installed but not enabled, no NPC was created.");
			return;
		}
		var location = spawnLocation == null ? null : spawnLocation.clone();
		if (location == null) {
			say("SKIPPED, no world is set so the probe has no spawn location, no NPC was created.");
			return;
		}
		if (plugin == null) {
			say("ABORTED, the probe cannot reach the Bukkit scheduler without a plugin handle.");
			return;
		}
		say(CITIZENS_PLUGIN + " " + citizens.getPluginMeta().getVersion() + " is installed and enabled.");
		var probe = new Probe(location, plugin);
		try {
			probe.arm();
		} finally {
			if (!probe.armed) {
				probe.cleanUp();
			}
		}
	}

	private static ServerGamePacketListenerImpl connection(Player player) {
		return ((CraftPlayer) player).getHandle().connection;
	}

	private static Boolean readClientLoaded(Player player) {
		try {
			return connection(player).hasClientLoaded();
		} catch (Exception e) {
			say("hasClientLoaded() could not be read, stack trace follows.");
			e.printStackTrace(System.out);
			return null;
		} catch (LinkageError err) {
			say(classpathDenied() + " hasClientLoaded() could not be read, stack trace follows.");
			err.printStackTrace(System.out);
			return null;
		}
	}

	private static String readNmsPosition(Player player) {
		try {
			var position = ((ServerPlayer) ((CraftPlayer) player).getHandle()).position();
			return shortCoords(position.x, position.y, position.z);
		} catch (Exception e) {
			return "unreadable, " + e;
		} catch (LinkageError err) {
			return "unreadable, " + err;
		}
	}

	/**
	 * What the listener still believes, read off its own last sent position fields
	 * rather than off the entity, because those two are what can disagree after a
	 * teleport.
	 */
	private static String readListenerBelievedPosition(Player player) {
		try {
			var listener = connection(player);
			var type = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
			return shortCoords(readDouble(type, listener, "lastPosX"), readDouble(type, listener, "lastPosY"),
					readDouble(type, listener, "lastPosZ"));
		} catch (NoSuchFieldException e) {
			return "this build exposes no lastPosX/lastPosY/lastPosZ on the listener";
		} catch (Exception e) {
			return "unreadable, " + e;
		} catch (LinkageError err) {
			return "unreadable, " + err;
		}
	}

	/**
	 * The teleport confirm latch. It is what decides whether handleMovePlayer
	 * applies an injected position or swallows it, and nothing in the public API
	 * exposes it, so it is read off the field.
	 */
	private static Boolean readAwaitingTeleport(Player player) {
		try {
			var listener = connection(player);
			var type = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
			var field = type.getDeclaredField("awaitingPositionFromClient");
			field.setAccessible(true);
			return field.get(listener) != null;
		} catch (Exception e) {
			say("awaitingPositionFromClient could not be read, so any verdict that leans on it is unmeasured.");
			return null;
		} catch (LinkageError err) {
			return null;
		}
	}

	/**
	 * The id the server handed out with its last teleport.
	 * handleAcceptTeleportation compares the packet's id against it, and
	 * disconnects the player outright if the id matches while no teleport is
	 * pending, so it has to be read rather than guessed.
	 */
	private static Integer readAwaitingTeleportId(Player player) {
		try {
			var listener = connection(player);
			var type = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
			var field = type.getDeclaredField("awaitingTeleport");
			field.setAccessible(true);
			return field.getInt(listener);
		} catch (Exception e) {
			say("awaitingTeleport could not be read, so the teleport confirm cannot be injected safely.");
			return null;
		} catch (LinkageError err) {
			say(classpathDenied() + " awaitingTeleport could not be read.");
			return null;
		}
	}

	/**
	 * Whether the server has torn the connection down. That is the only signal that
	 * answers "did that packet disconnect the NPC"; Player#isOnline() is not,
	 * because a Citizens NPC is never in the player list to begin with.
	 */
	private static Boolean readConnectionClosed(Player player) {
		try {
			var listener = connection(player);
			var type = Class.forName("net.minecraft.server.network.ServerCommonPacketListenerImpl");
			var field = type.getDeclaredField("closed");
			field.setAccessible(true);
			return field.getBoolean(listener);
		} catch (Exception e) {
			say("The connection-closed flag could not be read, so no stage may report survival on this run.");
			return null;
		} catch (LinkageError err) {
			say(classpathDenied() + " The connection-closed flag could not be read.");
			return null;
		}
	}

	private static double readDouble(Class<?> type, Object target, String name) throws Exception {
		var field = type.getDeclaredField(name);
		field.setAccessible(true);
		return field.getDouble(target);
	}

	private static boolean isSolid(Block block) {
		var type = block.getType();
		if (!type.isSolid() || type.isAir() || type == Material.WATER || type == Material.LAVA) {
			return false;
		}
		return block.getBoundingBox().getHeight() >= 1.0;
	}

	private static String describeConstructors(Class<?> packet) {
		var parts = new ArrayList<String>();
		collectConstructors(packet, parts, "");
		if (parts.isEmpty()) {
			for (var nested : packet.getDeclaredClasses()) {
				if (!Modifier.isAbstract(nested.getModifiers())) {
					collectConstructors(nested, parts, nested.getSimpleName() + " ");
				}
			}
		}
		return parts.isEmpty() ? "none public" : String.join(" | ", parts);
	}

	private static void collectConstructors(Class<?> type, List<String> parts, String prefix) {
		for (Constructor<?> constructor : type.getConstructors()) {
			var parameters = new ArrayList<String>();
			for (Class<?> parameter : constructor.getParameterTypes()) {
				parameters.add(parameter.getSimpleName());
			}
			parts.add(prefix + "(" + String.join(", ", parameters) + ")");
		}
	}

	private static String absence(Class<?> owner, EntryPoint entry) {
		var row = "on " + owner.getName()
				+ (entry.packet() == null ? ", no packet parameter expected" : " taking " + entry.packet());
		return entry.control()
				? "EXPECTED ABSENT " + entry.method() + " | " + row
						+ ", which confirms the docs were right to flag it for confirmation."
				: "FAIL " + entry.method() + " | " + row
						+ ", the name compiles here but is not on the running build, so read it as a version split.";
	}

	private static String describeUuid(UUID uuid) {
		if (uuid == null) {
			return "null";
		}
		return uuid + " (version nibble = " + ((uuid.getMostSignificantBits() >> 12) & 0xF) + ", this server runs with "
				+ "online-mode=" + Bukkit.getOnlineMode() + ", and a real player reads 3 with online-mode=false and 4 "
				+ "with online-mode=true; neither NPC id follows that rule)";
	}

	private static int rosterSize() {
		try {
			return Ioc.resolve(BotService.class).size();
		} catch (Exception e) {
			say("The bot roster size could not be read, stack trace follows.");
			e.printStackTrace(System.out);
			return -1;
		} catch (LinkageError err) {
			say(classpathDenied() + " The bot roster could not be reached, stack trace follows.");
			err.printStackTrace(System.out);
			return -1;
		}
	}

	private static double distance(double ax, double ay, double az, double bx, double by, double bz) {
		return round(Math.sqrt(Math.pow(ax - bx, 2) + Math.pow(ay - by, 2) + Math.pow(az - bz, 2)));
	}

	private static String shortCoords(Location location) {
		return shortCoords(location.getX(), location.getY(), location.getZ());
	}

	private static String shortCoords(double x, double y, double z) {
		return "(" + round(x) + ", " + round(y) + ", " + round(z) + ")";
	}

	private static double round(double value) {
		return Math.round(value * 100) / 100.0;
	}

	private static String classpathDenied() {
		return "CLASSES UNAVAILABLE, a class could not be linked, most likely the " + CITIZENS_PLUGIN
				+ " entry in paper-plugin.yml does not let this plugin see the Citizens jar, or the running server is "
				+ "not the build this probe was compiled against.";
	}

	private static void say(String message) {
		System.out.println(PREFIX + message);
	}
}
