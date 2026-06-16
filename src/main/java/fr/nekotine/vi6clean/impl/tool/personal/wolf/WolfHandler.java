package fr.nekotine.vi6clean.impl.tool.personal.wolf;

import java.util.Collections;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import com.destroystokyo.paper.entity.ai.VanillaGoal;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.logging.NekotineLogger;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.pathfinding.PathEdge;
import fr.nekotine.core.pathfinding.PathNetwork;
import fr.nekotine.core.pathfinding.PathNode;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.util.MobAiUtil;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

@ToolCode("wolf")
public class WolfHandler extends ToolHandler<WolfHandler.Wolf> {

	private final ComponentLogger logger = NekotineLogger.make();

	private PathNetwork wolfPathNetwork = new PathNetwork();

	private int straight;

	private int diag;

	public WolfHandler() {
		super(Wolf::new);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);

		var minX = 102; // +50
		var maxX = 199;
		var minY = 5;
		var maxY = 17;
		var minZ = 100;
		var maxZ = 197; // -40

		var w = Ioc.resolve(Vi6Game.class).getWorld();

		for (var x = minX; x < maxX; x++) {
			for (var y = minY; y < maxY; y++) {
				for (var z = minZ; z < maxZ; z++) {
					var loc = new Location(w, x + 0.5, y, z + 0.5);
					var underLoc = new Location(w, x + 0.5, y - 1, z + 0.5);

					var locY = loc.getY();

					// Check floor
					var floorY = locY + 1;

					if (loc.getBlock().isPassable()) {
						floorY = locY;
					} else {
						var floorbb = loc.getBlock().getBoundingBox();
						var floorTraceStart = loc.toVector().add(new Vector(0, 0.5, 0));
						if (floorbb.contains(floorTraceStart)) {
							continue; // Floor at 0.5 or above
						}
						var floorLocTrace = floorbb.rayTrace(floorTraceStart, new Vector(0, -1, 0), 0.5);

						if (floorLocTrace != null) {
							floorY = Math.min(floorY, floorLocTrace.getHitPosition().getY());
							if (floorY > locY + 0.2) {
								continue; // Le sol est trop haut
							}
						}
					}

					if (underLoc.getBlock().isPassable()) {
						if (floorY <= locY) {
							continue; // Le sol est trop bas
						}
					} else {
						var floorUnderTrace = underLoc.getBlock().getBoundingBox()
								.rayTrace(loc.toVector().add(new Vector(0, 0.1, 0)), new Vector(0, -1, 0), 0.7);
						if (floorUnderTrace != null) {
							floorY = Math.min(floorY, floorUnderTrace.getHitPosition().getY());
						}else {
							// TODO cas avec le tapis
						}
					}
					if (floorY > locY + 0.2 || floorY < locY - 0.5) {
						continue; // Le sol est trop bas/haut
					}

					// Check ceiling
					var ceilingLocTrace = loc.getBlock().getBoundingBox()
							.rayTrace(loc.toVector().add(new Vector(0, 0.5, 0)), new Vector(0, 1, 0), 1);
					var ceilingY = locY + 1;
					if (ceilingLocTrace != null) {
						ceilingY = Math.min(ceilingY, ceilingLocTrace.getHitPosition().getY());
						if (ceilingY < locY + 0.8) {
							continue; // plafond est trop bas
						}
					}
					wolfPathNetwork.getNodes().add(new PathNode(loc));
				}
			}
		}

		logger.info("Generating wolf pathfinding graph");

		wolfPathNetwork.generateEdges((node, network) -> {
			return network.getNodes().stream().filter(other -> {
				if (node.equals(other)) {
					return false;
				}
				var startLoc = node.getLocation();
				var endLoc = other.getLocation();
				if (startLoc.distance(endLoc) > 1.5) {
					return false;
				}
				var vect = endLoc.toVector().subtract(startLoc.toVector());
				if (vect.length() > 1.4) { // diagonale 2d
					var y = vect.getY();
					if (y == 0) { // diago x/z
						var lbx = startLoc.clone().add(vect.getX(), 0, 0);
						var lbz = startLoc.clone().add(0, 0, vect.getZ());
						if (!lbx.getBlock().isPassable() && !lbz.getBlock().isPassable()) {
							return false; // éviter les diagonales entre les blocs
						}
					} else { // diago y
						var lby = startLoc.clone().add(0, vect.getY(), 0);
						if (!lby.getBlock().isPassable()) {
							return false; // Un bloc block la montée/descente
						}
					}
					diag++;
				} else { // Straight line
					if (startLoc.getBlock().getBlockData() instanceof TrapDoor trapdoor) {
						if (trapdoor.isOpen() && trapdoor.getFacing().getDirection().dot(vect) < -0.9) {
							return false; // La trapdoor bloque le passage
						}
					}
					if (endLoc.getBlock().getBlockData() instanceof TrapDoor trapdoor) {
						if (trapdoor.isOpen() && trapdoor.getFacing().getDirection().dot(vect) > 0.9) {
							return false; // La trapdoor bloque le passage
						}
					}
					if (startLoc.getBlock().getBlockData() instanceof Door door) {
						if (!door.isOpen() && door.getFacing().getDirection().dot(vect) < -0.9) {
							return false; // La trapdoor bloque le passage
						}
					}
					if (endLoc.getBlock().getBlockData() instanceof Door door) {
						if (!door.isOpen() && door.getFacing().getDirection().dot(vect) > 0.9) {
							return false; // La trapdoor bloque le passage
						}
					}
					straight++;
				}
				// var mean = startLoc.clone().add(vect.multiply(0.5));
				// var wolfbb = new BoundingBox(mean.getX() - 0.3, mean.getX() + 0.3,
				// mean.getY(), mean.getY() + 0.85,mean.getZ() - 0.3, mean.getZ() + 0.3);
				return true;
			}).map(other -> new PathEdge(node, other, node.getLocation().distance(other.getLocation())))
					.collect(Collectors.toList());
		});

		logger.info("Infos for the wolf pathfinding network:");
		logger.info("Number of nodes: " + wolfPathNetwork.getNodes().size());
		logger.info("Number of edges between nodes: " + wolfPathNetwork.getEdges().size());
		logger.info("Number of diagonal edges: " + diag);
		logger.info("Number of straight edges: " + straight);

	}

	@Override
	protected void onStopHandling() {
	}

	@EventHandler
	private void onPlayerInterract(PlayerInteractEvent evt) {
		if (evt.getHand() != EquipmentSlot.HAND || !EventUtil.isCustomAction(evt, CustomAction.HIT_ANY)) {
			return;
		}

		var player = evt.getPlayer();
		var tool = getToolFromItem(evt.getItem());
		if (tool == null) {
			return;
		}

		// place

		for (var n : wolfPathNetwork.getNodes()) {
			n.getLocation().getWorld().spawnEntity(n.getLocation().clone().add(-0.25, 0, -0.25),
					EntityType.BLOCK_DISPLAY, SpawnReason.CUSTOM, e -> {
						e.setPersistent(false);
						if (e instanceof BlockDisplay dis) {
							var trans = dis.getTransformation();
							trans.getScale().mul(0.5f);
							dis.setTransformation(trans);
							dis.setBlock(Bukkit.createBlockData(Material.SPONGE));
						}
					});
		}

		if (tool.wolf != null)
			return; // already placed

		var variants = RegistryAccess.registryAccess().getRegistry(RegistryKey.WOLF_VARIANT).stream()
				.collect(Collectors.toList());
		Collections.shuffle(variants);
		var selectedVariant = variants.getFirst();

		tool.wolf = (Mob) player.getWorld().spawnEntity(player.getLocation(), EntityType.WOLF, SpawnReason.CUSTOM,
				e -> {
					if (e instanceof org.bukkit.entity.Wolf w) {
						w.setPersistent(false);
						w.getAttribute(Attribute.STEP_HEIGHT).setBaseValue(1); // Help navigation
						w.setVariant(selectedVariant);
						var goals = Bukkit.getServer().getMobGoals();
						goals.removeGoal(w, VanillaGoal.SIT_WHEN_ORDERED_TO); // Merci la
						// décompilation pour savoir les
						// Goals des loups
						goals.removeGoal(w, VanillaGoal.TAMABLE_ANIMAL_PANIC);
						goals.removeGoal(w, VanillaGoal.WOLF_AVOID_ENTITY);
						goals.removeGoal(w, VanillaGoal.FOLLOW_OWNER);
						goals.removeGoal(w, VanillaGoal.BREED);
						goals.removeGoal(w, VanillaGoal.WATER_AVOIDING_RANDOM_STROLL);
						goals.removeGoal(w, VanillaGoal.WOLF_BEG);
						goals.removeGoal(w, VanillaGoal.RESET_UNIVERSAL_ANGER);
						goals.removeGoal(w, VanillaGoal.NEAREST_ATTACKABLE);
						goals.removeGoal(w, VanillaGoal.NON_TAME_RANDOM);
						MobAiUtil.clearBrain(w);
						goals.addGoal(w, 8, new WolfPatrolGoal(w, wolfPathNetwork));// Priority 8 is the same as the
																					// vanilla random
						// stroll goal
						w.setOwner(player);
						w.setInvulnerable(true);
						w.setSilent(true);
					}
				});

		detachFromOwner(tool);
		evt.setCancelled(true);
	}

	public boolean boundingBoxContainsAWolf(BoundingBox box) {
		return getTools().stream().map(t -> t.wolf).filter(w -> w != null)
				.anyMatch(w -> box.contains(w.getLocation().toVector()));
	}

	@Override
	protected void onAttachedToPlayer(Wolf tool) {
	}

	@Override
	protected void onDetachFromPlayer(Wolf tool) {
	}

	@Override
	protected void onToolCleanup(Wolf tool) {
		if (tool.wolf != null) {
			tool.wolf.remove();
			tool.wolf = null;
		}
	}

	public static class Wolf extends Tool {

		public Wolf(ToolHandler<?> handler) {
			super(handler);
		}

		private Mob wolf;
	}
}
