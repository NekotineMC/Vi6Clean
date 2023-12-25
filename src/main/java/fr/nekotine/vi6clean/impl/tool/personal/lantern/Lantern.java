package fr.nekotine.vi6clean.impl.tool.personal.lantern;

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

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.constant.Vi6ToolLoreText;
import fr.nekotine.vi6clean.impl.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.impl.tool.Tool;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class Lantern extends Tool{
	
	private ArmorStand fallingArmorStand;
	
	private List<BlockDisplay> displayedLanterns = new LinkedList<>();
	
	
	@Override
	protected ItemStack makeInitialItemStack() {
		return ItemStackUtil.make(
				Material.LANTERN, 
				Ioc.resolve(LanternHandler.class).getMaxLantern()-displayedLanterns.size(), 
				Component.text("Lantern", NamedTextColor.GOLD), 
				Vi6ToolLoreText.LANTERN.make());
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
	
	public void updateItemStack() {
		var handler = Ioc.resolve(LanternHandler.class);
		var amount = handler.getMaxLantern()-displayedLanterns.size();
		if (amount <= 0) {
			setItemStack(handler.getNoLanternItemstack());
		}else {
			setItemStack(
					ItemStackUtil.make(Material.LANTERN, handler.getMaxLantern()-displayedLanterns.size(),
							Component.text("Lantern", NamedTextColor.GOLD), Vi6ToolLoreText.LANTERN.make()));
		}
	}
	
	public boolean tryPlace() {
		var owner = getOwner();
		if (displayedLanterns.size() >= Ioc.resolve(LanternHandler.class).getMaxLantern() || fallingArmorStand != null || owner == null) {
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
		updateItemStack();
		return true;
	}
	
	public void allyTryPickup(Player picking) {
		var owner = getOwner();
		if (owner == null) {
			return;
		}
		var flagModule = Ioc.resolve(StatusFlagModule.class);
		if(flagModule.hasAny(owner, EmpStatusFlag.get())) {
			return;
		}
		var ite = displayedLanterns.iterator();
		var range = Ioc.resolve(LanternHandler.class).getSquaredPickupBlockRange();
		while (ite.hasNext()) {
			var lantern = ite.next();
			var lanternLoc = lantern.getLocation();
			if (picking.getLocation().distanceSquared(lanternLoc) <= range) {
				var w = lanternLoc.getWorld();
				Vi6Sound.LANTERNE_PRE_TELEPORT.play(w, lanternLoc);
				if (!owner.equals(picking)) {
					var ownerLoc = owner.getLocation();
					w.spawnParticle(Particle.EXPLOSION_LARGE, lanternLoc, 1);
					picking.teleport(ownerLoc);
					Vi6Sound.LANTERNE_POST_TELEPORT.play(w, ownerLoc);
				}
				lantern.remove();
				ite.remove();
				break;
			}
		}
		updateItemStack();
	}
	
	public void tryRemoveFallingArmorStand() {
		if (fallingArmorStand != null && fallingArmorStand.isOnGround()) {
			Vi6Sound.LANTERNE_POSE.play(fallingArmorStand.getWorld(), fallingArmorStand.getLocation());
			fallingArmorStand.remove();
			fallingArmorStand = null;
		}
	}
	
	public List<BlockDisplay> getDisplayedLanternList(){
		return displayedLanterns;
	}
	
	public void lanternSmokes(double offsetX, double offsetZ) {
		var owner = getOwner();
		if (owner == null) {
			return;
		}
		var w = owner.getWorld();
		for (var lantern : displayedLanterns) {
			var loc = lantern.getLocation();
			var x = loc.getX();
			var y = loc.getY() - 0.74f;
			var z = loc.getZ();
			w.spawnParticle(Particle.FIREWORKS_SPARK, x+offsetX, y, z+offsetZ, 0, 
					0, 0, 0, 0f);
		}
	}

	//

	@Override
	protected void onEmpStart() {
	}
	@Override
	protected void onEmpEnd() {
	}
}
