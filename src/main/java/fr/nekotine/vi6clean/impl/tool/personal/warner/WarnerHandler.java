package fr.nekotine.vi6clean.impl.tool.personal.warner;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import fr.nekotine.core.inventory.ItemStackBuilder;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.text.TextModule;
import fr.nekotine.core.text.style.NekotineStyles;
import fr.nekotine.core.text.tree.Leaf;
import fr.nekotine.core.ticking.TickTimeStamp;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.util.SpatialUtil;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.map.Vi6Map;
import fr.nekotine.vi6clean.impl.map.artefact.Artefact;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

@ToolCode("warner")
public class WarnerHandler extends ToolHandler<WarnerHandler.Warner> {

	private final int WARN_DELAY_TICK = (int) (20 * getConfiguration().getDouble("delay", 2));

	private final double PLACE_RANGE = getConfiguration().getDouble("place_range", 8);

	private final double PLACE_RANGE_SQUARED = PLACE_RANGE * PLACE_RANGE;

	private final float DISPLAY_DISTANCE = 0.8f;

	private final float DISPLAY_SCALE = 1.4f;

	private final String WARN_MESSAGE = "<gold>Avertisseur>></gold> <red>L'artéfact <aqua><name></aqua> à été volé !</red>";

	private final Set<String> watchedArtefacts = new HashSet<>();

	public WarnerHandler() {
		super(Warner::new);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
	}

	@Override
	protected void onStopHandling() {
		watchedArtefacts.clear();
	}

	@EventHandler
	private void onPlayerInterract(PlayerInteractEvent evt) {
		if (evt.getHand() != EquipmentSlot.HAND && !EventUtil.isCustomAction(evt, CustomAction.HIT_ANY)) {
			return;
		}

		var player = evt.getPlayer();
		var tool = getToolFromItem(evt.getItem());
		if (tool == null) {
			return;
		}
		// place
		if (tool.eye_item1 != null)
			return; // already placed

		var map = Ioc.resolve(Vi6Map.class);

		var world = player.getWorld();
		var nearestAvailableArtefact = map.getArtefacts().values().stream()
				.filter(art -> (!art.isCaptured())
						&& (art.getBlockPosition().toLocation(world)
								.distanceSquared(player.getLocation()) <= PLACE_RANGE_SQUARED)
						&& !watchedArtefacts.contains(art.getName()))
				.findFirst();

		if (nearestAvailableArtefact.isEmpty())
			return;

		tool.watched = nearestAvailableArtefact.get();
		var watchedLocation = tool.watched.getBlockPosition().toLocation(world);
		tool.eye_item1 = (ItemDisplay) world.spawnEntity(watchedLocation.clone().add(0.5, 0.5, 0.5),
				EntityType.ITEM_DISPLAY, SpawnReason.CUSTOM, e -> {
					if (e instanceof ItemDisplay dis) {
						dis.setItemStack(new ItemStack(Material.ENDER_EYE));
						dis.setTransformation(getTransformFromAngleStep(tool.angleStep));
						dis.setPersistent(false);
					}
				});
		tool.eye_item2 = (ItemDisplay) world.spawnEntity(watchedLocation.clone().add(0.5, 0.5, 0.5),
				EntityType.ITEM_DISPLAY, SpawnReason.CUSTOM, e -> {
					if (e instanceof ItemDisplay dis) {
						dis.setItemStack(new ItemStack(Material.ENDER_EYE));
						dis.setTransformation(getTransformFromAngleStep(tool.angleStep + 4));
						dis.setPersistent(false);
					}
				});
		Vi6Sound.WARNER_POSE.play(watchedLocation.getWorld(), watchedLocation);
		detachFromOwner(tool);
		watchedArtefacts.add(tool.watched.getName());
		evt.setCancelled(true);
	}

	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		for (var tool : getTools().stream().collect(Collectors.toList())) {

			if (tool.watched != null && tool.watched.isCaptured() && ++tool.warnDelay >= WARN_DELAY_TICK) {
				var message = Ioc.resolve(TextModule.class)
						.message(Leaf.builder().addStyle(Placeholder.unparsed("name", tool.watched.getName()))
								.addStyle(NekotineStyles.STANDART).addLine(WARN_MESSAGE))
						.buildFirst();
				for (var teammate : Ioc.resolve(Vi6Game.class).getGuards()) {
					Vi6Sound.WARNER_TRIGGER.play(teammate);
					teammate.sendMessage(message);
				}
				tool.watched.setFoundAfterCapture(true);
				remove(tool);
			}

			if (evt.timeStampReached(TickTimeStamp.HalfSecond)) {
				var owner = tool.getOwner();
				// Display
				if (tool.watched != null) {
					tool.angleStep = (byte) ((tool.angleStep + 1) % 8);
					tool.eye_item1.setInterpolationDelay(0);
					tool.eye_item1.setInterpolationDuration(11);
					tool.eye_item1.setTransformation(getTransformFromAngleStep(tool.angleStep));
					tool.eye_item2.setInterpolationDelay(0);
					tool.eye_item2.setInterpolationDuration(11);
					tool.eye_item2.setTransformation(getTransformFromAngleStep(tool.angleStep + 4));
				} else if (owner.isSneaking() && itemMatch(tool, owner.getInventory().getItemInMainHand())) {
					var loc = owner.getLocation();
					var x = loc.getX();
					var y = loc.getY();
					var z = loc.getZ();
					SpatialUtil.circle2DDensity(PLACE_RANGE, 5, 0, (offsetX, offsetZ) -> owner
							.spawnParticle(Particle.FIREWORK, x + offsetX, y, z + offsetZ, 1, 0, 0, 0, 0, null));
				}
			}
		}
	}

	private Transformation getTransformFromAngleStep(int n) {
		float angle = (float) (n * Math.PI / 4);
		return new Transformation(
				new Vector3f((float) Math.sin(angle) * DISPLAY_DISTANCE, 0, (float) Math.cos(angle) * DISPLAY_DISTANCE),
				new AxisAngle4f(angle, new Vector3f(0, 1, 0)),
				new Vector3f(DISPLAY_SCALE, DISPLAY_SCALE, DISPLAY_SCALE), new AxisAngle4f());
	}

	@Override
	protected void onAttachedToPlayer(Warner tool) {
	}

	@Override
	protected void onDetachFromPlayer(Warner tool) {
	}

	@Override
	protected void onToolCleanup(Warner tool) {
		if (tool.eye_item1 != null) {
			tool.eye_item1.remove();
			tool.eye_item1 = null;
		}
		if (tool.eye_item2 != null) {
			tool.eye_item2.remove();
			tool.eye_item2 = null;
		}
		tool.watched = null;
	}

	@Override
	protected ItemStack makeItem(Warner tool) {
		return new ItemStackBuilder(Material.ENDER_EYE).name(getDisplayName()).lore(getLore()).unstackable()
				.flags(ItemFlag.values()).build();
	}

	public static class Warner extends Tool {

		private int warnDelay;

		private byte angleStep;

		private Artefact watched;

		private ItemDisplay eye_item1;

		private ItemDisplay eye_item2;

		public Warner(ToolHandler<?> handler) {
			super(handler);
		}
	}
}
