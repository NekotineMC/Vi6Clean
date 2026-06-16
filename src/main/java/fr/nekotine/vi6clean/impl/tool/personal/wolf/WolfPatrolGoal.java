package fr.nekotine.vi6clean.impl.tool.personal.wolf;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.pathfinding.PathEdge;
import fr.nekotine.core.pathfinding.PathNetwork;
import fr.nekotine.core.pathfinding.TraversalAlgorithm;
import fr.nekotine.core.pathfinding.astar.AstarTraversal;
import fr.nekotine.core.pathfinding.astar.heuristic.ReducedDiagonalAstarHeuristic;
import fr.nekotine.vi6clean.impl.map.Vi6Map;
import fr.nekotine.vi6clean.impl.map.artefact.Artefact;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.Sound.Source;

public class WolfPatrolGoal implements Goal<Mob> {

	private final Mob wolf;

	private Artefact target;

	private PathNetwork network;

	private TraversalAlgorithm traversal = new AstarTraversal(new ReducedDiagonalAstarHeuristic());

	private List<PathEdge> path;

	private List<Entity> displays = new LinkedList<>();

	public WolfPatrolGoal(Mob w, PathNetwork network) {
		this.wolf = w;
		this.network = network;
	}

	public static final GoalKey<Mob> KEY = GoalKey.of(Mob.class,
			new NamespacedKey(Ioc.resolve(JavaPlugin.class), "wolf_patrol"));

	@Override
	public void start() {
		var map = Ioc.resolve(Vi6Map.class);
		var artefacts = map.getArtefacts().values().stream().filter(a -> {
			var path = wolf.getPathfinder().findPath(a.getBlockPosition().toLocation(wolf.getWorld()));
			if (path == null) {
				return false;
			}
			return true;// a.getBoundingBox().contains(path.getFinalPoint().toVector());
		}).collect(Collectors.toList());
		if (artefacts.size() <= 0) {
			return;
		}
		Collections.shuffle(artefacts);
		artefacts.sort((a, _) -> a.isFoundAfterCapture() ? 1 : -1);
		target = artefacts.getFirst();
		System.out.println("Target is " + target.getName());
		wolf.getWorld().playSound(Sound.sound(NamespacedKey.minecraft("entity.wolf.pant"), Source.AMBIENT, 1, 0.8f));
		var bb = target.getBoundingBox();
		var wloc = wolf.getLocation().toVector();
		/*-
		var x = Math.clamp(wloc.getX(), bb.getMinX(), bb.getMaxX() - 1);
		var y = Math.clamp(wloc.getY(), bb.getMinY(), bb.getMaxY() - 1);
		var z = Math.clamp(wloc.getZ(), bb.getMinZ(), bb.getMaxZ() - 1);
		*/
		path = traversal.getPath(network, network.nearestNodeFrom(wloc), network.nearestNodeFrom(bb.getCenter()));
		if (path == null) {
			System.out.println("Pas de chemin trouvé");
			target = null;
		} else {
			System.out.println("Voici le chemin:");
			for (var e : path) {
				var loc = e.getTo().getLocation();
				displays.add(loc.getWorld().spawnEntity(loc.clone().add(-0.25, 0, -0.25), EntityType.BLOCK_DISPLAY, SpawnReason.CUSTOM, en -> {
					en.setPersistent(false);
					if (en instanceof BlockDisplay dis) {
						var trans = dis.getTransformation();
						trans.getScale().mul(0.5f);
						dis.setTransformation(trans);
						dis.setBlock(Bukkit.createBlockData(Material.RED_WOOL));
					}
				}));
				System.out.println(e.getTo().getLocation().toVector());
			}
		}
	}

	@Override
	public void tick() {
		if (target == null || path == null) {
			return;
		}
		var bb = target.getBoundingBox();
		var wloc = wolf.getLocation().toVector();

		var wpathfinder = wolf.getPathfinder();
		wpathfinder.stopPathfinding();

		var currentStep = path.getFirst();
		if (currentStep.getTo().getLocation().toVector().distanceSquared(wloc) <= 0.25) {
			path.removeFirst();
			if (path.isEmpty()) {
				target = null;
			} else {
				currentStep = path.getFirst();
			}
		}
		var vect = currentStep.getTo().getLocation().toVector().subtract(wloc);
		var vect2d = vect.clone().multiply(0.2);
		vect2d.setY(wolf.getVelocity().getY());// step height should help us here
		wolf.setVelocity(vect2d);
		var temploc = wolf.getLocation(); // Je sais pas calculer un YAW/PITCH :/
		temploc.setDirection(vect);
		wolf.setRotation(temploc.getYaw(), temploc.getPitch());
		wolf.lookAt(currentStep.getTo().getLocation());

		/*-
		var path = wpathfinder.findPath(currentStep.getTo().getLocation());
		if (path != null) {
			wpathfinder.moveTo(path);
			for (var point : wpathfinder.getCurrentPath().getPoints()) {
				wolf.getWorld().spawnParticle(Particle.COMPOSTER, point, 1);
			}
		} else {
			if (wpathfinder.getCurrentPath() == null) {
				target = null;
			}
		}
		*/

		if (bb.contains(wloc)) {
			target = null;
		}
	}

	@Override
	public boolean shouldStayActive() {
		return target != null && shouldActivate();
	}

	@Override
	public void stop() {
		for (var e : displays) {
			e.remove();
		}
		displays.clear();
	}

	@Override
	public boolean shouldActivate() {
		return wolf.getTarget() == null || !wolf.getTarget().isValid();
	}

	@Override
	public GoalKey<Mob> getKey() {
		return KEY;
	}

	@Override
	public EnumSet<GoalType> getTypes() {
		return EnumSet.of(GoalType.MOVE, GoalType.LOOK);
	}

}
