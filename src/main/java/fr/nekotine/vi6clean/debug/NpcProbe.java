package fr.nekotine.vi6clean.debug;

import java.util.List;
import java.util.UUID;
import java.util.WeakHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;

/**
 * TEMPORARY diagnostic probe. Delete this file and its single call site in
 * Vi6PhaseLobby once the console output has been read.
 */
public class NpcProbe {

	private static final String PREFIX = "[NpcProbe] ";
	private static final String CITIZENS_PLUGIN = "Citizens";
	private static final String OWN_PLUGIN = "Vi6Clean";
	private static final long DELAY_TICKS = 20L;

	private NpcProbe() {
	}

	public static void run(Location spawnLocation) {
		say("=========================================================");
		say("TEMPORARY PROBE, delete debug/NpcProbe.java and its call site once this output has been read.");
		say("CLAIMED A, from citizens-integration.md \"A bot is a real Player\" and \"Identity gotchas\":");
		say("  NPC#getEntity() returns a CraftPlayer subclass for EntityType.PLAYER NPCs.");
		say("  instanceof Player is True for real players and player NPCs.");
		say("CLAIMED B, from citizens-integration.md \"Bots are absent from Bukkit.getOnlinePlayers()\":");
		say("  NPCs are not counted in Bukkit.getOnlinePlayers().");
		say("=========================================================");
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
		var plugin = Bukkit.getPluginManager().getPlugin(OWN_PLUGIN);
		if (plugin == null) {
			say("ABORTED, the probe cannot reach the Bukkit scheduler without a handle on " + OWN_PLUGIN + ".");
			return;
		}
		say("Citizens " + citizens.getPluginMeta().getVersion() + " is installed and enabled, spawning a probe NPC.");
		NPC npc;
		try {
			npc = spawnNpc(location);
		} catch (Exception e) {
			say("The probe threw while spawning, stack trace follows.");
			e.printStackTrace(System.out);
			return;
		} catch (LinkageError err) {
			say(classpathDenied() + " It happened while spawning, stack trace follows.");
			err.printStackTrace(System.out);
			return;
		}
		if (npc == null) {
			return;
		}
		final Entity entity;
		try {
			entity = npc.getEntity();
		} catch (Exception e) {
			say("The probe threw while reading the spawned entity, stack trace follows.");
			e.printStackTrace(System.out);
			cleanUp(npc, null);
			return;
		} catch (LinkageError err) {
			say(classpathDenied() + " It happened while reading the spawned entity, stack trace follows.");
			err.printStackTrace(System.out);
			cleanUp(npc, null);
			return;
		}
		if (entity == null) {
			say("ABORTED, the NPC yielded no entity, no fact is read for it.");
			cleanUp(npc, null);
			return;
		}
		try {
			Bukkit.getScheduler().runTaskLater(plugin, () -> readAll(npc, entity), DELAY_TICKS);
			say("Spawned, every fact read is deferred by " + DELAY_TICKS
					+ " ticks so the per tick player bookkeeping the claims describe has settled.");
		} catch (Exception e) {
			say("The probe threw while deferring the reads, stack trace follows.");
			e.printStackTrace(System.out);
			cleanUp(npc, entity);
		} catch (LinkageError err) {
			say(classpathDenied() + " It happened while deferring the reads, stack trace follows.");
			err.printStackTrace(System.out);
			cleanUp(npc, entity);
		}
	}

	private static NPC spawnNpc(Location location) {
		var registry = CitizensAPI.getNPCRegistry();
		if (registry == null) {
			say("ABORTED, the NPC registry could not be obtained.");
			return null;
		}
		say("Registry obtained through CitizensAPI.getNPCRegistry().");
		var npcName = "NpcProbeTarget-" + Long.toHexString(System.nanoTime());
		var npc = registry.createNPC(EntityType.PLAYER, npcName);
		if (npc == null) {
			say("ABORTED, registry.createNPC(PLAYER, " + npcName + ") returned null.");
			return null;
		}
		say("createNPC(PLAYER, " + npcName + ") ok, spawning at " + location);
		if (!npc.spawn(location)) {
			say("ABORTED, NPC#spawn returned false, no fact is read for an NPC that never entered the world.");
			cleanUp(npc, null);
			return null;
		}
		say("NPC#spawn returned true, named " + npcName + ".");
		return npc;
	}

	private static void readAll(NPC npc, Entity entity) {
		try {
			say("--- deferred read, " + DELAY_TICKS + " ticks after the spawn ---");
			say("Baseline, Bukkit.getOnlinePlayers() = " + describeRoster());
			say("Citizens setting npc.player.remove-from-list, NPC#shouldRemoveFromPlayerList() = "
					+ readShouldRemoveFromPlayerList(npc));
			for (var verdict : describePlayerNpc(entity)) {
				say(verdict);
			}
		} catch (Exception e) {
			say("The probe threw during the deferred read, stack trace follows.");
			e.printStackTrace(System.out);
			say("CLAIM A: unobserved, the deferred read threw -> UNKNOWN");
			say("CLAIM B: unobserved, the deferred read threw -> UNKNOWN");
		} catch (LinkageError err) {
			say(classpathDenied() + " The deferred read could not link, stack trace follows.");
			err.printStackTrace(System.out);
			say("CLAIM A: unobserved, the deferred read could not link -> UNKNOWN");
			say("CLAIM B: unobserved, the deferred read could not link -> UNKNOWN");
		} finally {
			cleanUp(npc, entity);
		}
	}

	private static String readShouldRemoveFromPlayerList(NPC npc) {
		try {
			return String.valueOf(npc.shouldRemoveFromPlayerList());
		} catch (Exception e) {
			say("NPC#shouldRemoveFromPlayerList() threw, stack trace follows.");
			e.printStackTrace(System.out);
			return "unavailable, the call threw";
		} catch (LinkageError err) {
			say(classpathDenied() + " NPC#shouldRemoveFromPlayerList() could not link, stack trace follows.");
			err.printStackTrace(System.out);
			return "unavailable, the call could not link";
		}
	}

	private static String[] describePlayerNpc(Entity entity) {
		say("--- NPC facts, every one of them read through the Bukkit Player interface ---");
		var isPlayer = entity instanceof Player;
		say("concrete class name    : " + entity.getClass().getName());
		say("superclass chain       : " + describeHierarchy(entity.getClass()));
		say("instanceof Player      : " + isPlayer);
		say("getType()              : " + entity.getType());
		if (!isPlayer) {
			say("The spawned entity is not a Player, the Player only reads are skipped.");
			return new String[]{"CLAIM A: instanceof Player == false -> FALSE",
					"CLAIM B: unobserved, the entity is not a Player -> UNKNOWN"};
		}
		var npcPlayer = (Player) entity;
		var npcUuid = npcPlayer.getUniqueId();

		var online = Bukkit.getOnlinePlayers();
		var onlineByUuid = containsUuid(online, npcUuid);
		var onlineByRef = containsRef(online, npcPlayer);
		say("in getOnlinePlayers() =" + online.size() + " : by uuid = " + onlineByUuid + ", by reference = "
				+ onlineByRef);

		var world = npcPlayer.getWorld();
		var worldPlayers = world == null ? List.<Player>of() : world.getPlayers();
		say("in world.getPlayers()  : by uuid = " + containsUuid(worldPlayers, npcUuid) + ", by reference = "
				+ containsRef(worldPlayers, npcPlayer) + ", world = " + (world == null ? "null" : world.getName()));

		var onlineValue = npcPlayer.isOnline();
		say("isOnline()             : " + onlineValue);

		say("getUniqueId()          : " + describeUuid(npcUuid));

		var realPlayer = online.stream().filter(p -> !p.getUniqueId().equals(npcUuid)).findFirst().orElse(null);
		if (realPlayer == null) {
			say("hashCode()             : " + npcPlayer.hashCode() + ", no real player online, comparison skipped");
		} else {
			say("hashCode()             : npc = " + npcPlayer.hashCode() + ", real player " + realPlayer.getName()
					+ " = " + realPlayer.hashCode());
			say("equals                 : npc.equals(real) = " + npcPlayer.equals(realPlayer) + ", real.equals(npc) = "
					+ realPlayer.equals(npcPlayer));
			var weak = new WeakHashMap<Player, String>();
			weak.put(npcPlayer, "npc");
			say("WeakHashMap keying     : containsKey(npc) = " + weak.containsKey(npcPlayer) + ", get(npc) = "
					+ weak.get(npcPlayer) + ", containsKey(real) = " + weak.containsKey(realPlayer));
		}

		var inventory = npcPlayer.getInventory();
		var probeItem = new ItemStack(Material.DIAMOND, 3);
		inventory.setItemInMainHand(probeItem);
		var readBack = inventory.getItemInMainHand();
		var roundTrip = readBack != null && probeItem.isSimilar(readBack);
		say("main hand round trip   : setItemInMainHand(DIAMOND x3) then getItemInMainHand() = "
				+ (readBack == null ? "null" : readBack.getType() + " x" + readBack.getAmount()) + ", similar = "
				+ roundTrip);

		return new String[]{"CLAIM A: instanceof Player == " + isPlayer + " -> " + isPlayer,
				"CLAIM B: in getOnlinePlayers() by uuid = " + onlineByUuid + ", by reference = " + onlineByRef + " -> "
						+ (!onlineByUuid && !onlineByRef),
				"VERDICT isOnline(): observed " + onlineValue
						+ " -> citizens-integration.md \"Identity gotchas\" calls "
						+ "this unknown and do not use, on this Paper build it reads " + onlineValue,
				"VERDICT main hand round trip: observed similar = " + roundTrip
						+ " -> the unverified note in citizens-integration.md \"Inventories and held items work\" is "
						+ (roundTrip ? "CONFIRMED" : "REFUTED") + " on this Paper build"};
	}

	private static boolean containsUuid(Iterable<? extends Player> players, UUID uuid) {
		for (var p : players) {
			if (p.getUniqueId().equals(uuid)) {
				return true;
			}
		}
		return false;
	}

	private static boolean containsRef(Iterable<? extends Player> players, Player target) {
		for (var p : players) {
			if (p == target) {
				return true;
			}
		}
		return false;
	}

	private static void cleanUp(NPC npc, Entity entity) {
		try {
			npc.destroy();
			say("Cleanup: NPC#destroy() ran.");
		} catch (Exception e) {
			say("Cleanup: NPC#destroy() threw, falling back to Entity#remove(), stack trace follows.");
			e.printStackTrace(System.out);
			removeEntity(npc, entity);
		} catch (LinkageError err) {
			say(classpathDenied() + " Cleanup: NPC#destroy() could not link, falling back to Entity#remove(), "
					+ "stack trace follows.");
			err.printStackTrace(System.out);
			removeEntity(npc, entity);
		}
	}

	private static void removeEntity(NPC npc, Entity entity) {
		try {
			var toRemove = entity != null ? entity : npc.getEntity();
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

	private static String describeRoster() {
		var online = Bukkit.getOnlinePlayers();
		if (online.isEmpty()) {
			return "empty, no real player connected";
		}
		return online.stream().map(p -> p.getName() + " " + describeUuid(p.getUniqueId())).toList().toString();
	}

	private static String describeHierarchy(Class<?> type) {
		var builder = new StringBuilder();
		for (var current = type; current != null; current = current.getSuperclass()) {
			if (builder.length() > 0) {
				builder.append(" -> ");
			}
			builder.append(current.getName());
		}
		return builder.toString();
	}

	private static String describeUuid(UUID uuid) {
		var versionNibble = (uuid.getMostSignificantBits() >> 12) & 0xF;
		return uuid + " (version nibble = " + versionNibble
				+ ", real players read 3 with online-mode=false and 4 with online-mode=true, a 2 would show the "
				+ "4 to 2 flip described in citizens-integration.md \"Identity gotchas\")";
	}

	private static String classpathDenied() {
		return "CLASSES UNAVAILABLE, a Citizens class could not be linked, most likely the " + CITIZENS_PLUGIN
				+ " entry in paper-plugin.yml does not let this plugin see the Citizens jar.";
	}

	private static void say(String message) {
		System.out.println(PREFIX + message);
	}
}
