package fr.nekotine.vi6clean.impl.tool.personal.hogrider;

import fr.nekotine.core.ticking.TickTimeStamp;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.Sound.Source;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Arrays;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;

@ToolCode("hogrider")
public class HogRiderHandler extends ToolHandler<HogRiderHandler.HogRider> {
	private final Material MATERIAL = Material.SADDLE;
	public HogRiderHandler() {
		super(HogRider::new);
	}

	@Override
	protected void onAttachedToPlayer(HogRider tool) {
	}

	@Override
	protected void onDetachFromPlayer(HogRider tool) {
		if (tool.riding) {
			unride(tool);
		}
	}

	@Override
	protected void onToolCleanup(HogRider tool) {
	}

	@Override
	protected ItemStack makeItem(HogRider tool) {
		var item = ItemStackUtil.make(MATERIAL, Component.empty(), Component.empty());
		editRideItem(tool.mode, item);
		return item;
	}

	private void ride(HogRider tool) {
		var ploc = tool.getOwner().getLocation();
		var mg = Bukkit.getMobGoals();
		tool.riding = true;
		tool.ridingMode = tool.mode;
		tool.getOwner().setCooldown(MATERIAL, tool.mode.getDuration());
		tool.steed = (Mob) ploc.getWorld().spawnEntity(ploc, tool.mode.getEntityType(), SpawnReason.CUSTOM, e -> {
			if (e instanceof Mob mob) {
				mg.removeAllGoals(mob);
				mob.setInvulnerable(true);
				mob.addPassenger(tool.getOwner());
				mob.getAttribute(Attribute.STEP_HEIGHT).setBaseValue(1.1);
				mob.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(tool.mode.getSpeed());
			}
		});
	}

	private void unride(HogRider tool) {
		tool.riding = false;
		tool.getOwner().setCooldown(MATERIAL, tool.ridingMode.getCooldown());
		tool.steed.remove();
	}

	private void editRideItem(SteedMode mode, ItemStack item) {
		var im = item.getItemMeta();
		if (mode == SteedMode.NORMAL) {
			im.setItemModel(Material.PORKCHOP.getKey());
			im.displayName(Component.text("Fidèle Cochon", NamedTextColor.LIGHT_PURPLE));
			im.lore(Arrays.asList(Component.text("Chevauchez un cochon lent mais endurant", NamedTextColor.AQUA)));
			im.setEnchantmentGlintOverride(false);
		} else if (mode == SteedMode.TURBO) {
			im.setItemModel(Material.INK_SAC.getKey());
			im.displayName(Component.text("Fidèle Poulpe", NamedTextColor.GRAY));
			im.lore(Arrays.asList(Component.text("Chevauchez rapidement un poule rapide", NamedTextColor.AQUA)));
			im.setEnchantmentGlintOverride(true);
		}
		item.setItemMeta(im);
	}

	@EventHandler
	private void onPlayerInterract(PlayerInteractEvent evt) {
		var tool = getToolFromItem(evt.getItem());
		if (tool == null) {
			return;
		}
		evt.setCancelled(true);
		if (EventUtil.isCustomAction(evt, CustomAction.INTERACT_ANY)) {
			switch (tool.mode) {
				case NORMAL :
					tool.mode = SteedMode.TURBO;
					break;
				case TURBO :
					tool.mode = SteedMode.NORMAL;
			}
			editItem(tool, item -> {
				editRideItem(tool.mode, item);
			});
			return;
		}

		if (EventUtil.isCustomAction(evt, CustomAction.HIT_ANY)) {
			if (tool.getOwner().hasCooldown(evt.getItem())) {
				if (tool.riding) {
					unride(tool);
				}
				return;
			}
			if (tool.riding) {
				unride(tool);
			} else {
				ride(tool);
			}
		}
	}

	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		for (var tool : getTools()) {
			if (!tool.riding)
				continue;

			tool.getOwner().getWorld().spawnParticle(tool.ridingMode.getParticle(),
					tool.getOwner().getLocation().add(0, .1, 0), 5, .25, 0, .25, 0);
			if (evt.timeStampReached(TickTimeStamp.QuartSecond)) {
				tool.getOwner().getWorld().playSound(
						Sound.sound(Registry.SOUNDS.getKey(tool.ridingMode.getSound()), Source.BLOCK, 1, 0),
						Sound.Emitter.self());

			}

			var steed = tool.steed;
			if (!steed.isValid() || !steed.getPassengers().contains(tool.getOwner())
					|| !tool.getOwner().hasCooldown(MATERIAL)) {
				unride(tool);
				continue;
			}
			var direction = tool.getOwner().getLocation().getDirection().setY(0);
			if (direction.lengthSquared() > 0) {
				direction.normalize();
			}
			var velocity = direction.multiply(steed.getAttribute(Attribute.MOVEMENT_SPEED).getBaseValue())
					.setY(steed.getVelocity().getY());
			steed.setVelocity(velocity);
			steed.setRotation(tool.getOwner().getLocation().getYaw(), tool.getOwner().getLocation().getPitch());
		}
	}

	public static class HogRider extends Tool {
		private boolean riding = false;
		private SteedMode mode = SteedMode.NORMAL;
		private SteedMode ridingMode = SteedMode.NORMAL;
		private Mob steed;
		public HogRider(ToolHandler<?> handler) {
			super(handler);
		}
	}

	public enum SteedMode {
		NORMAL(EntityType.PIG, 0.5, 150, 100, Particle.HAPPY_VILLAGER, org.bukkit.Sound.BLOCK_GRASS_STEP), TURBO(
				EntityType.SQUID, 1, 250, 50, Particle.GLOW, org.bukkit.Sound.BLOCK_WET_GRASS_STEP);

		private final EntityType entityType;
		private final double speed;
		private final int cooldown;
		private final int duration;
		private final Particle particle;
		private final org.bukkit.Sound sound;

		SteedMode(EntityType entityType, double speed, int cooldown, int duration, Particle particle,
				org.bukkit.Sound sound) {
			this.entityType = entityType;
			this.speed = speed;
			this.cooldown = cooldown;
			this.duration = duration;
			this.particle = particle;
			this.sound = sound;
		}

		public EntityType getEntityType() {
			return entityType;
		}
		public double getSpeed() {
			return speed;
		}
		public int getCooldown() {
			return cooldown;
		}
		public int getDuration() {
			return duration;
		}
		public Particle getParticle() {
			return particle;
		}
		public org.bukkit.Sound getSound() {
			return sound;
		}
	}
}
