package fr.nekotine.vi6clean.impl.tool.personal.shadow;

import fr.nekotine.core.inventory.ItemStackBuilder;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.ticking.TickTimeStamp;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EntityUtil;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.util.InventoryUtil;
import fr.nekotine.core.util.PlayerProfileUtil;
import fr.nekotine.core.util.SpatialUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpEndEvent;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpStartEvent;
import fr.nekotine.vi6clean.impl.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.tool.personal.minifier.MinifierHandler;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

@ToolCode("shadow")
public class ShadowHandler extends ToolHandler<ShadowHandler.Shadow> {

	private final double SHADOW_KILL_RANGE_BLOCK = getConfiguration().getDouble("kill_range", 1);

	private static final String SHADOW_PROFILE_URL = "http://textures.minecraft.net/texture/df2ab0ae32dac33950b948523cb1de42be2cd8a01c31fd1a0b9eaf961ce86e25";

	private static final String JAMMED_PROFILE_URL = "http://textures.minecraft.net/texture/5c7772c7cdcddb6b79d5525f9dcebc748aabdae38d9e38eea7fe78a501de6ede";

	private final ResolvableProfile SHADOW_PROFILE = ResolvableProfile
			.resolvableProfile(PlayerProfileUtil.makeProfileFromSkinUrl(SHADOW_PROFILE_URL));

	private final ResolvableProfile JAMMED_PROFILE = ResolvableProfile
			.resolvableProfile(PlayerProfileUtil.makeProfileFromSkinUrl(JAMMED_PROFILE_URL));

	private final WrappingModule wrappingModule;

	public ShadowHandler() {
		super(Shadow::new);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
		wrappingModule = Ioc.resolve(WrappingModule.class);
	}

	@EventHandler
	private void onPlayerInterract(PlayerInteractEvent evt) {
		if (evt.getHand() != EquipmentSlot.HAND || !EventUtil.isCustomAction(evt, CustomAction.HIT_ANY)) {
			return;
		}
		var statusModule = Ioc.resolve(StatusFlagModule.class);
		var player = evt.getPlayer();
		var tool = getToolFromItem(evt.getItem());
		if (tool == null || statusModule.hasAny(player, EmpStatusFlag.get())) {
			return;
		}
		if (tool.shadow != null) {
			// USE
			Vi6Sound.SHADOW_TELEPORT.play(player.getWorld(), player.getLocation());
			Vi6Sound.SHADOW_TELEPORT.play(player.getWorld(), tool.shadow.getLocation());
			player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 1, 0, false, false, false));
			player.teleport(tool.shadow, TeleportCause.PLUGIN);
			evt.setCancelled(true);
			remove(tool);
		} else {
			// TRY PLACE
			var ploc = player.getLocation();
			if (!EntityUtil.IsOnGround(player)) {
				return;
			}
			tool.shadow = (Mannequin) ploc.getWorld().spawnEntity(ploc, EntityType.MANNEQUIN, SpawnReason.CUSTOM, e -> {
				if (e instanceof Mannequin man) {
					man.setProfile(SHADOW_PROFILE);
					var helmet = ItemStack.of(Material.PLAYER_HEAD);
					helmet.setData(DataComponentTypes.PROFILE,
							ResolvableProfile.resolvableProfile(player.getPlayerProfile()));
					man.getEquipment().setHelmet(helmet);
					man.setImmovable(true);
					man.setDescription(null);
					man.setGravity(false);
					var ownerScale = (float) player.getAttribute(Attribute.SCALE).getValue();
					if (ownerScale != 1) {
						var scaleAttr = man.getAttribute(Attribute.SCALE);
						scaleAttr.addModifier(new AttributeModifier(MinifierHandler.SCALE_ATTRIBUTE_KEY, ownerScale - 1,
								Operation.MULTIPLY_SCALAR_1));
					}
				}
			});
			editItem(tool, item -> {
				item.setData(DataComponentTypes.ITEM_MODEL, Material.SKELETON_SKULL.key());
				item.editMeta(m -> m.displayName(getDisplayName().append(Component.text(" - "))
						.append(Component.text("Placée", NamedTextColor.GRAY))));
			});
			evt.setCancelled(true);
		}
	}

	private void shadow_found(Shadow tool, Player player) {
		tool.getOwner().damage(1000, player);
		Vi6Sound.SHADOW_KILL.play(player.getWorld());
	}

	@EventHandler
	private void onPlayerMove(PlayerMoveEvent evt) {
		var player = evt.getPlayer();
		var ite = getTools().iterator();
		while (ite.hasNext()) {
			var tool = ite.next();
			var wrap = wrappingModule.getWrapperOptional(tool.getOwner(), PlayerWrapper.class);
			if (tool.shadow == null || wrap.isEmpty()
					|| !wrap.get().ennemiTeamInMap().anyMatch(p -> p.equals(evt.getPlayer()))) {
				continue;
			}
			if (tool.shadow.getLocation().distanceSquared(player.getLocation()) <= SHADOW_KILL_RANGE_BLOCK) {
				shadow_found(tool, player);
				remove(tool);
			}
		}
	}

	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		if (evt.timeStampReached(TickTimeStamp.QuartSecond)) {
			for (var tool : getTools()) {
				if (tool.shadow != null) {
					var wrap = Ioc.resolve(WrappingModule.class).getWrapper(tool.getOwner(), PlayerWrapper.class);
					var loc = tool.shadow.getLocation();
					var x = loc.getX();
					var y = loc.getY();
					var z = loc.getZ();
					SpatialUtil.circle2DDensity(SHADOW_KILL_RANGE_BLOCK, 3, Math.random(), (offsetX, offsetZ) -> {
						wrap.ourTeam().forEach(
								p -> p.spawnParticle(Particle.SMOKE, x + offsetX, y, z + offsetZ, 1, 0, 0, 0, 0, null));
					});
				}
			}
		}
	}

	@EventHandler
	private void onDamageEvent(EntityDamageByEntityEvent evt) {
		var it = getTools().iterator();
		while (it.hasNext()) {
			var tool = it.next();
			if (evt.getEntity().equals(tool.shadow)) {
				var wrap = wrappingModule.getWrapperOptional(tool.getOwner(), PlayerWrapper.class);
				if (wrap.isEmpty()) {
					return;
				}
				if (wrap.get().ennemiTeamInMap().anyMatch(e -> evt.getDamager().equals(e))) {
					shadow_found(tool, (Player) evt.getDamager());
					remove(tool);
				}
				return;
			}
		}
	}

	@EventHandler
	private void onDamageEvent(EntityDamageEvent evt) {
		for (var tool : getTools()) {
			if (evt.getEntity().equals(tool.shadow)) {
				evt.setCancelled(true);
				return;
			}
		}
	}

	@Override
	protected void onAttachedToPlayer(Shadow tool) {
	}

	@Override
	protected void onDetachFromPlayer(Shadow tool) {
		if (tool.shadow != null) {
			tool.shadow.remove();
		}
	}

	@Override
	protected void onToolCleanup(Shadow tool) {
	}

	@Override
	protected ItemStack makeItem(Shadow tool) {
		return new ItemStackBuilder(Material.WITHER_SKELETON_SKULL)
				.name(getDisplayName().append(Component.text(" - "))
						.append(Component.text("Disponible", NamedTextColor.BLUE)))
				.lore(getLore()).unstackable().flags(ItemFlag.values())
				.postApply(item -> item.setData(DataComponentTypes.PROFILE, JAMMED_PROFILE)).build();
	}

	@EventHandler
	private void onEmpStart(EntityEmpStartEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				item.setData(DataComponentTypes.ITEM_MODEL, Material.PLAYER_HEAD.key());
				item.editMeta(m -> m.displayName(getDisplayName().decorate(TextDecoration.STRIKETHROUGH)
						.append(Component.text(" - ")).append(Component.text("Brouillé", NamedTextColor.RED))));
			});
		}
	}

	@EventHandler
	private void onEmpStop(EntityEmpEndEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				var tool = getToolFromItem(item);
				if (tool.shadow == null) {
					item.resetData(DataComponentTypes.ITEM_MODEL); // back to default model
					item.editMeta(m -> m.displayName(getDisplayName().append(Component.text(" - "))
							.append(Component.text("Disponible", NamedTextColor.BLUE))));
				} else {
					item.setData(DataComponentTypes.ITEM_MODEL, Material.SKELETON_SKULL.key());
					item.editMeta(m -> m.displayName(getDisplayName().append(Component.text(" - "))
							.append(Component.text("Placée", NamedTextColor.GRAY))));
				}
			});
		}
	}

	public static class Shadow extends Tool {

		private Mannequin shadow;

		public Shadow(ToolHandler<?> handler) {
			super(handler);
		}
	}
}
