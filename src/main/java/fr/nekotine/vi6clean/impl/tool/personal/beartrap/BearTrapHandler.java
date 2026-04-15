package fr.nekotine.vi6clean.impl.tool.personal.beartrap;

import com.destroystokyo.paper.profile.PlayerProfile;
import fr.nekotine.core.inventory.ItemStackBuilder;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.status.effect.StatusEffect;
import fr.nekotine.core.status.effect.StatusEffectModule;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.util.PlayerProfileUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.impl.status.effect.TazedStatusEffectType;
import fr.nekotine.vi6clean.impl.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

@ToolCode("beartrap")
public class BearTrapHandler extends ToolHandler<BearTrap> {

	// https://minecraft-heads.com/custom-heads/head/33884-bear-trap
	private static final String ARMED_SKIN_URL = "http://textures.minecraft.net/texture/d23dd5fc15b2d337347a94146ff20003b2d62f668b4517e1145d3acfcc25587c";

	// https://minecraft-heads.com/custom-heads/head/35953-bear-trap
	private static final String UNARMED_SKIN_URL = "http://textures.minecraft.net/texture/85a8be4b3666eef20199c84d59efc7c771f4e3f290f9688fb12a97f65cdd64c7";

	public static final NamespacedKey FANG_DAMAGE = new NamespacedKey(Ioc.resolve(JavaPlugin.class), "fang_damage");

	private final double DAMAGE = getConfiguration().getDouble("damage", 10);

	private final double SQUARED_TRIGGER_RANGE = Math.pow(getConfiguration().getDouble("trigger_range", 0.85), 2);

	private final double SQUARED_PICKUP_RANGE = Math.pow(getConfiguration().getDouble("pickup_range", 0.85), 2);

	private final double SQUARED_SPACING_DISTANCE = Math.pow(getConfiguration().getDouble("spacing_distance", 0.85), 2);

	private static final int FANG_ANIMATION_DURATION_TICK = 20; // Handcoded value in minecraft

	private static final StatusEffect TRAPPED_EFFECT = new StatusEffect(TazedStatusEffectType.get(),
			FANG_ANIMATION_DURATION_TICK);

	private final PlayerProfile ARMED_PLAYER_PROFILE = PlayerProfileUtil.makeProfileFromSkinUrl(ARMED_SKIN_URL);

	private final PlayerProfile UNARMED_PLAYER_PROFILE = PlayerProfileUtil.makeProfileFromSkinUrl(UNARMED_SKIN_URL);

	private final ItemStack ARMED_HELMET_ITEM = new ItemStackBuilder(Material.PLAYER_HEAD)
			.name(getDisplayName()).lore(getLore()).unstackable().flags(ItemFlag.values()).postApply(i -> i
					.setData(DataComponentTypes.PROFILE, ResolvableProfile.resolvableProfile(ARMED_PLAYER_PROFILE)))
			.build();

	private final ItemStack UNARMED_HELMET_ITEM = new ItemStackBuilder(Material.PLAYER_HEAD)
			.name(getDisplayName()).lore(getLore()).unstackable().flags(ItemFlag.values()).postApply(i -> i
					.setData(DataComponentTypes.PROFILE, ResolvableProfile.resolvableProfile(UNARMED_PLAYER_PROFILE)))
			.build();

	public BearTrapHandler() {
		super(BearTrap::new);
	}

	@EventHandler
	private void onPlayerInterract(PlayerInteractEvent evt) {
		var player = evt.getPlayer();
		if (!EventUtil.isCustomAction(evt, CustomAction.HIT_ANY)) {
			return;
		}
		var tool = getToolFromItem(evt.getItem());
		if (tool == null) {
			return;
		}

		if (tool.isPlaced()) {
			if (player.getLocation().distanceSquared(tool.getLocation()) <= SQUARED_PICKUP_RANGE) {
				tool.setLocation(null);
				onToolCleanup(tool);
				editItem(tool, i -> {
					i.setData(DataComponentTypes.PROFILE, ResolvableProfile.resolvableProfile(ARMED_PLAYER_PROFILE));
				});
			}
		} else {
			var loc = player.getLocation();
			if (!loc.clone().add(0, -0.1, 0).getBlock().isSolid() || getTools().stream().filter(t -> t.isPlaced())
					.anyMatch(t -> loc.distanceSquared(t.getLocation()) <= SQUARED_SPACING_DISTANCE)) {
				return;
			}
			tool.setLocation(loc);
			tool.setTrap((ArmorStand) player.getWorld().spawnEntity(tool.getLocation().clone().subtract(0, 1.95, 0),
					EntityType.ARMOR_STAND, SpawnReason.CUSTOM, e -> {
						if (e instanceof ArmorStand a) {
							a.setInvisible(true);
							a.setMarker(true);
							a.setBasePlate(false);
							a.setGravity(false);
							a.setPersistent(false);
							a.getEquipment().setHelmet(ARMED_HELMET_ITEM);
						}
					}));
			tool.setArmed(true);
			editItem(tool, i -> {
				i.setData(DataComponentTypes.PROFILE, ResolvableProfile.resolvableProfile(UNARMED_PLAYER_PROFILE));
			});
		}
	}

	@EventHandler
	private void onPlayerMove(PlayerMoveEvent evt) {
		var wrappingModule = Ioc.resolve(WrappingModule.class);
		var statusFlagModule = Ioc.resolve(StatusFlagModule.class);
		for (BearTrap tool : getTools()) {
			if (!tool.isArmed() || statusFlagModule.hasAny(tool.getOwner(), EmpStatusFlag.get())) {
				continue;
			}
			var player = evt.getPlayer();
			if (wrappingModule.getWrapper(tool.getOwner(), PlayerWrapper.class).ennemiTeamInMap()
					.noneMatch(player::equals)) {
				continue;
			}
			if (tool.getLocation().distanceSquared(evt.getFrom()) <= SQUARED_TRIGGER_RANGE) {
				tool.setArmed(false);
				tool.getLocation().getWorld().spawnEntity(player.getLocation(), EntityType.EVOKER_FANGS,
						SpawnReason.TRAP, fang -> {
							fang.getPersistentDataContainer().set(FANG_DAMAGE, PersistentDataType.DOUBLE, DAMAGE);
						});

				tool.getTrap().getEquipment().setHelmet(UNARMED_HELMET_ITEM);
				Ioc.resolve(StatusEffectModule.class).addEffect(player, TRAPPED_EFFECT);
				return;
			}
		}
	}

	@EventHandler
	public void onDamage(EntityDamageByEntityEvent evt) {
		if (evt.getDamager() instanceof EvokerFangs fang) {
			var dmg = fang.getPersistentDataContainer().get(FANG_DAMAGE, PersistentDataType.DOUBLE);
			if (dmg != null) {
				evt.setDamage(dmg); // Fang damage is hardcoded in NMS
			}
		}
	}

	@Override
	protected void onAttachedToPlayer(BearTrap tool) {
	}

	@Override
	protected void onDetachFromPlayer(BearTrap tool) {
	}

	@Override
	protected void onToolCleanup(BearTrap tool) {
		if (tool.getTrap() != null) {
			tool.getTrap().remove();
			tool.setTrap(null);
		}
	}

	@Override
	protected ItemStack makeItem(BearTrap tool) {
		return new ItemStackBuilder(Material.PLAYER_HEAD)
				.name(getDisplayName()).lore(getLore()).unstackable().flags(ItemFlag.values()).postApply(i -> i
						.setData(DataComponentTypes.PROFILE, ResolvableProfile.resolvableProfile(ARMED_PLAYER_PROFILE)))
				.build();
	}
}
