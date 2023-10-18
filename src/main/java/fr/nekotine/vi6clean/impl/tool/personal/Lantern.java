package fr.nekotine.vi6clean.impl.tool.personal;

import java.util.LinkedList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.core.util.SpatialUtil;
import fr.nekotine.vi6clean.constant.Vi6ToolLoreText;
import fr.nekotine.vi6clean.impl.tool.Tool;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class Lantern extends Tool{
	
	private ArmorStand fallingArmorStand;
	
	private List<BlockDisplay> displayedLanterns = new LinkedList<>();
	
	@Override
	protected ItemStack makeInitialItemStack() {
		return ItemStackUtil.make(Material.LANTERN, LanternHandler.MAX_LANTERN-displayedLanterns.size(), Component.text("Lantern", NamedTextColor.GOLD), Vi6ToolLoreText.LANTERN.make());
	}
	
	@Override
	protected void cleanup() {
		for(var lantern : displayedLanterns) {
			lantern.remove();
		}
		if (fallingArmorStand != null) {
			fallingArmorStand.remove();
		}
	}
	
	public boolean tryPlace() {
		var owner = getOwner();
		if (displayedLanterns.size() >= LanternHandler.MAX_LANTERN || fallingArmorStand != null || owner == null) {
			return false;
		}
		if (fallingArmorStand != null) {
			fallingArmorStand.remove();
		}
		var loc = owner.getLocation();
		loc.setYaw(0);
		loc.setPitch(0);
		var lantern = (BlockDisplay)owner.getWorld().spawnEntity(loc, EntityType.BLOCK_DISPLAY, SpawnReason.CUSTOM);
		var transf = new Transformation(
				new Vector3f(-0.5f, -0.7405f, -0.5f),
				new AxisAngle4f(),
				new Vector3f(1, 1, 1),
				new AxisAngle4f());
		lantern.setTransformation(transf);
		lantern.setBlock(Bukkit.createBlockData(Material.LANTERN));
		fallingArmorStand = (ArmorStand)owner.getWorld().spawnEntity(owner.getLocation(), EntityType.ARMOR_STAND, SpawnReason.CUSTOM);
		fallingArmorStand.addPassenger(lantern);
		fallingArmorStand.setInvisible(true);
		fallingArmorStand.setSilent(true);
		fallingArmorStand.setInvulnerable(true);
		fallingArmorStand.setSmall(true);
		displayedLanterns.add(lantern);
		getItemStack().setAmount(LanternHandler.MAX_LANTERN-displayedLanterns.size());
		return true;
	}
	
	public boolean tryPickup(Player picking) {
		return false;
	}
	
	public void tryRemoveFallingArmorStand() {
		if (fallingArmorStand != null && fallingArmorStand.isOnGround()) {
			fallingArmorStand.remove();
			fallingArmorStand = null;
		}
	}
	
	public List<BlockDisplay> getDisplayedLanternList(){
		return displayedLanterns;
	}
	
	public void lanternSmokes() {
		var owner = getOwner();
		if (owner == null) {
			return;
		}
		var w = owner.getWorld();
		var dist = Math.random();
		for (var lantern : displayedLanterns) {
			var loc = lantern.getLocation();
			var x = loc.getX();
			var y = loc.getY() - 0.74f;
			var z = loc.getZ();
			SpatialUtil.circle2DDensity(1.5, 0.5, Math.random() * 6, (offsetX, offsetZ) -> {
				var scalledOffsetX = offsetX * dist;
				var scalledOffsetZ = offsetZ * dist;
				w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, x+scalledOffsetX, y, z+scalledOffsetZ, 0, 
						-offsetX, 0, -offsetZ, 0.01 * dist);
			});
			SpatialUtil.circle2DDensity(1.5, 3, Math.random() * 6, (offsetX, offsetZ) -> {
				w.spawnParticle(Particle.ASH, x+offsetX, y, z+offsetZ, 0, 
						-offsetX, 0, -offsetZ, 0.01 * dist);
			});
		}
	}

}
