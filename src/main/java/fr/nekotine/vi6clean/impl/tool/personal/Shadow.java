package fr.nekotine.vi6clean.impl.tool.personal;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import fr.nekotine.core.inventory.ItemStackBuilder;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.core.util.SpatialUtil;
import fr.nekotine.vi6clean.constant.Vi6ToolLoreText;
import fr.nekotine.vi6clean.impl.tool.Tool;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class Shadow extends Tool{

	private final ItemStack DISPONIBLE_ITEM = new ItemStackBuilder(Material.WITHER_SKELETON_SKULL)
			.name(Component.text("Ombre - ",NamedTextColor.GOLD).append(Component.text("Disponible", NamedTextColor.BLUE)))
			.lore(Vi6ToolLoreText.SHADOW.make())
			.unstackable()
			.flags(ItemFlag.values())
			.build();
	
	private final ItemStack PLACED_ITEM = new ItemStackBuilder(Material.SKELETON_SKULL)
			.name(Component.text("Ombre - ",NamedTextColor.GOLD).append(Component.text("Placée", NamedTextColor.GRAY)))
			.lore(Vi6ToolLoreText.SHADOW.make())
			.unstackable()
			.flags(ItemFlag.values())
			.build();
	
	private ArmorStand shadow;
	
	@Override
	protected ItemStack makeInitialItemStack() {
		return DISPONIBLE_ITEM;
	}
	
	public void lowTick() {
		if (shadow != null) {
			var loc = shadow.getLocation();
			var x = loc.getX();
			var y = loc.getY();
			var z = loc.getZ();
			var w = loc.getWorld();
			SpatialUtil.circle2DDensity(1, 3, Math.random(),
					(offsetX, offsetZ) -> {
						w.spawnParticle(Particle.SMOKE_NORMAL, x + offsetX, y, z + offsetZ, 1, 0, 0, 0, 0, null);
					});
		}
	}
	
	public boolean tryPlace() {
		var player = getOwner();
		var ploc = player.getLocation();
		if (shadow == null) {
			if (ploc.clone().subtract(0, 0.1, 0).getBlock().getType().isSolid()) {
				shadow = (ArmorStand) ploc.getWorld().spawnEntity(ploc, EntityType.ARMOR_STAND);
				shadow.setArms(false);
				shadow.setMarker(true);
				shadow.setBasePlate(false);
				var builder = new ItemStackBuilder(Material.LEATHER_CHESTPLATE).unbreakable();
				var equipments = shadow.getEquipment();
				equipments.setHelmet(ItemStackUtil.skull(player.getPlayerProfile()), true);
				// Chestplate
				var armor = builder.build();
				var meta = (LeatherArmorMeta)armor.getItemMeta();
				meta.setColor(Color.BLACK);
				armor.setItemMeta(meta);
				equipments.setChestplate(armor);
				// Leggings
				armor = builder.material(Material.LEATHER_LEGGINGS).build();
				meta = (LeatherArmorMeta)armor.getItemMeta();
				meta.setColor(Color.BLACK);
				armor.setItemMeta(meta);
				equipments.setLeggings(armor);
				// Boots
				armor = builder.material(Material.LEATHER_BOOTS).build();
				meta = (LeatherArmorMeta)armor.getItemMeta();
				meta.setColor(Color.BLACK);
				armor.setItemMeta(meta);
				equipments.setBoots(armor);
				setItemStack(PLACED_ITEM);
			}
		}
		return false;
	}
	
	public boolean tryUse() {
		var player = getOwner();
		if (shadow != null) {
			player.teleport(shadow);
			shadow.remove();
			shadow = null;
			return true;
		}
		return false;
	}

	@Override
	protected void cleanup() {
		if (shadow != null) {
			shadow.remove();
		}
	}

	public ArmorStand getPlaced() {
		return shadow;
	}
}
