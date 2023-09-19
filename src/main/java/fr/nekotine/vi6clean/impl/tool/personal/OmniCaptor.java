package fr.nekotine.vi6clean.impl.tool.personal;

import java.util.Collection;
import java.util.LinkedList;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.NekotineCore;
import fr.nekotine.core.glow.EntityGlowModule;
import fr.nekotine.core.inventory.ItemStackBuilder;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.util.SpatialUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.impl.status.flag.OmniCaptedStatusFlag;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class OmniCaptor extends Tool{

	private final ItemStack DISPONIBLE_ITEM = new ItemStackBuilder(Material.REPEATER)
			.name(Component.text("OmniCapteur - ",NamedTextColor.GOLD).append(Component.text("Disponible", NamedTextColor.BLUE)))
			.lore(OmniCaptorHandler.LORE)
			.unstackable()
			.flags(ItemFlag.values())
			.build();
	
	private final ItemStack PLACED_ITEM = new ItemStackBuilder(Material.LEVER)
			.name(Component.text("OmniCapteur - ",NamedTextColor.GOLD).append(Component.text("Placé", NamedTextColor.GRAY)))
			.lore(OmniCaptorHandler.LORE)
			.unstackable()
			.flags(ItemFlag.values())
			.build();
	
	private final ItemStack TRIGGERED_ITEM = new ItemStackBuilder(Material.REDSTONE_TORCH)
			.name(Component.text("OmniCapteur - ",NamedTextColor.GOLD).append(Component.text("Activé", NamedTextColor.RED)))
			.lore(OmniCaptorHandler.LORE)
			.unstackable()
			.flags(ItemFlag.values())
			.build();
	
	private boolean sneaking;
	
	private ArmorStand placed;
	
	private Collection<Player> ennemiesInRange = new LinkedList<>();
	
	private Supplier<Stream<Player>> enemyTeam;
	
	@Override
	protected ItemStack makeInitialItemStack() {
		return DISPONIBLE_ITEM;
	}

	public boolean isSneaking() {
		return sneaking;
	}

	public void setSneaking(boolean sneaking) {
		if (this.sneaking != sneaking) {
			this.sneaking = sneaking;
			if (placed == null) {
				return;
			}
			var glowModule = NekotineCore.MODULES.get(EntityGlowModule.class);
			if (sneaking) {
				glowModule.glowEntityFor(placed, getOwner());
			}else {
				glowModule.unglowEntityFor(placed, getOwner());
			}
		}
	}
	
	public void lowTick() {
		if (placed == null) {
			var player = getOwner();
			if (player == null || !sneaking) {
				return;
			}
			SpatialUtil.circle2DDensity(player.getLocation(), OmniCaptorHandler.DETECTION_BLOCK_RANGE, 5,
					(x, y, z) -> {
						player.spawnParticle(Particle.FIREWORKS_SPARK, x, y, z, 1, 0, 0, 0, 0, null);
					});
		}
	}
	
	public boolean tryPlace() {
		var player = getOwner();
		var ploc = player.getLocation();
		if (placed == null) {
			if (ploc.subtract(0, 0.1, 0).getBlock().getType().isSolid()) {
				enemyTeam = NekotineCore.MODULES.get(WrappingModule.class).getWrapper(getOwner(), PlayerWrapper.class)::ennemiTeamInMap;
				placed = (ArmorStand) ploc.getWorld().spawnEntity(ploc, EntityType.ARMOR_STAND);
				placed.setArms(false);
				placed.setMarker(true);
				placed.setBasePlate(false);
				placed.setSmall(true);
				placed.getEquipment().setHelmet(new ItemStack(Material.REDSTONE_BLOCK));
				Vi6Sound.OMNICAPTEUR_PLACE.play(ploc.getWorld(),ploc);
				itemUpdate();
			}
		}
		return false;
	}
	
	public boolean tryPickup() {
		var player = getOwner();
		var ploc = player.getLocation();
		if (placed != null) {
			if (ploc.distanceSquared(placed.getLocation()) <= OmniCaptorHandler.DETECTION_RANGE_SQUARED) {
				placed.remove();
				placed = null;
				Vi6Sound.OMNICAPTEUR_PICKUP.play(ploc.getWorld(),ploc);
				itemUpdate();
				var flagModule = NekotineCore.MODULES.get(StatusFlagModule.class);
				for (var p : ennemiesInRange) {
					flagModule.removeFlag(p, OmniCaptedStatusFlag.get());
				}
				ennemiesInRange.clear();
				return true;
			}
		}
		return false;
	}
	
	public void itemUpdate() {
		if (placed != null) {
			if (ennemiesInRange.size() > 0) {
				setItemStack(TRIGGERED_ITEM);
			}else {
				setItemStack(PLACED_ITEM);
			}
		}else {
			setItemStack(DISPONIBLE_ITEM);
		}
	}

	@Override
	protected void cleanup() {
		if (placed != null) {
			placed.remove();
		}
	}

	public ArmorStand getPlaced() {
		return placed;
	}
	
	public Collection<Player> getEnnemiesInRange() {
		return ennemiesInRange;
	}

	public Stream<Player> getEnemyTeam() {
		return enemyTeam.get();
	}
}
