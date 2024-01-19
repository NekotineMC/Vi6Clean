package fr.nekotine.vi6clean.impl.tool.personal.shadow;

import java.util.Collection;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import com.google.common.base.Supplier;

import fr.nekotine.core.inventory.ItemStackBuilder;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.core.util.SpatialUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.impl.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;

public class Shadow extends Tool{
	private ArmorStand shadow;
	private Supplier<Collection<Player>> ourTeam;
	
	@Override
	protected ItemStack makeInitialItemStack() {
		return Ioc.resolve(ShadowHandler.class).getDisponibleItem();
	}
	
	public void lowTick() {
		if (shadow != null) {
			var loc = shadow.getLocation();
			var x = loc.getX();
			var y = loc.getY();
			var z = loc.getZ();
			SpatialUtil.circle2DDensity(Ioc.resolve(ShadowHandler.class).getShadowKillRangeBlock(), 3, Math.random(),
					(offsetX, offsetZ) -> {
						ourTeam.get().forEach(p -> p.spawnParticle(Particle.SMOKE_NORMAL, x + offsetX, y, z + offsetZ, 1, 0, 0, 0, 0, null));
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
				setItemStack(Ioc.resolve(ShadowHandler.class).getPlacedItem());
				
				ourTeam = Ioc.resolve(WrappingModule.class).getWrapper(player, PlayerWrapper.class)::ourTeam;	
			}
		}
		return false;
	}
	
	public boolean tryUse() {
		var player = getOwner();
		if(shadow == null) {
			return false;
		}
		var statusFlagModule = Ioc.resolve(StatusFlagModule.class);
		if(statusFlagModule.hasAny(getOwner(), EmpStatusFlag.get())) {
			return false;
		}
		Vi6Sound.SHADOW_TELEPORT.play(player.getWorld(), player.getLocation());
		Vi6Sound.SHADOW_TELEPORT.play(player.getWorld(), shadow.getLocation());
		player.teleport(shadow);
		shadow.remove();
		shadow = null;
		return true;
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

	//

	@Override
	protected void onEmpStart() {
		if(shadow != null) {
			setItemStack(Ioc.resolve(ShadowHandler.class).getEmpItem());
		}
	}
	@Override
	protected void onEmpEnd() {
		if(shadow != null) {
			setItemStack(Ioc.resolve(ShadowHandler.class).getPlacedItem());
		}
	}
}
