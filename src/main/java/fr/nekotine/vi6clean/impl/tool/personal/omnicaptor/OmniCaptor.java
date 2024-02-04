package fr.nekotine.vi6clean.impl.tool.personal.omnicaptor;

import java.util.Collection;
import java.util.LinkedList;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.glow.EntityGlowModule;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.status.effect.StatusEffect;
import fr.nekotine.core.status.effect.StatusEffectModule;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.util.SpatialUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.impl.status.effect.OmniCaptedStatusEffectType;
import fr.nekotine.vi6clean.impl.status.flag.OmniCaptedStatusFlag;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;

public class OmniCaptor extends Tool{
	private boolean emp;
	
	private StatusEffect temporaryEffect = new StatusEffect(OmniCaptedStatusEffectType.get(), Ioc.resolve(OmniCaptorHandler.class).getEffectDuration());
	private StatusEffect unlimitedEffect = new StatusEffect(OmniCaptedStatusEffectType.get(), -1);
	
	private boolean sneaking;
	
	private ArmorStand placed;
	
	private Collection<Player> ennemiesInRange = new LinkedList<>();
	
	private Supplier<Stream<Player>> enemyTeam;
	
	@Override
	protected ItemStack makeInitialItemStack() {
		return Ioc.resolve(OmniCaptorHandler.class).getDisponibleItem();
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
			var glowModule = Ioc.resolve(EntityGlowModule.class);
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
			if (player == null || !sneaking || !player.getInventory().getItemInMainHand().isSimilar(getItemStack())) {
				return;
			}
			var loc = player.getLocation();
			var x = loc.getX();
			var y = loc.getY();
			var z = loc.getZ();
			SpatialUtil.circle2DDensity(Ioc.resolve(OmniCaptorHandler.class).getDetectionBlockRange(), 5, 0,
					(offsetX, offsetZ) -> {
						player.spawnParticle(Particle.FIREWORKS_SPARK, x + offsetX, y, z + offsetZ, 1, 0, 0, 0, 0, null);
					});
		}
	}
	
	public boolean tryPlace() {
		var player = getOwner();
		var ploc = player.getLocation();
		if (placed == null) {
			if (ploc.subtract(0, 0.1, 0).getBlock().getType().isSolid()) {
				enemyTeam = Ioc.resolve(WrappingModule.class).getWrapper(getOwner(), PlayerWrapper.class)::ennemiTeamInMap;
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
			if (ploc.distanceSquared(placed.getLocation()) <= Ioc.resolve(OmniCaptorHandler.class).getDetectionBlockRange()) {
				placed.remove();
				placed = null;
				Vi6Sound.OMNICAPTEUR_PICKUP.play(ploc.getWorld(),ploc);
				itemUpdate();
				var flagModule = Ioc.resolve(StatusFlagModule.class);
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
		var handler = Ioc.resolve(OmniCaptorHandler.class);
		if (placed != null) {
			if (ennemiesInRange.size() > 0) {
				setItemStack(handler.getTriggeredItem());
			}else {
				setItemStack(handler.getPlacedItem());
			}
		}else {
			setItemStack(handler.getDisponibleItem());
		}
	}
	
	public void applyEffect(Player player) {
		if (emp) {
			return;
		}
		var effectModule = Ioc.resolve(StatusEffectModule.class);
		effectModule.addEffect(player, unlimitedEffect);
	}
	
	public void removeEffect(Player player) {
		var effectModule = Ioc.resolve(StatusEffectModule.class);
		effectModule.addEffect(player, temporaryEffect);
		effectModule.removeEffect(player, unlimitedEffect);
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

	//

	@Override
	protected void onEmpStart() {
		for (var i : ennemiesInRange) {
			removeEffect(i);
		}
		emp = true;
	}
	@Override
	protected void onEmpEnd() {
		emp = true;
	}
}
