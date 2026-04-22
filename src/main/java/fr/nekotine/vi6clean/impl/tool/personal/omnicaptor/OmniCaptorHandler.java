package fr.nekotine.vi6clean.impl.tool.personal.omnicaptor;

import fr.nekotine.core.glow.EntityGlowModule;
import fr.nekotine.core.inventory.ItemStackBuilder;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.status.effect.StatusEffect;
import fr.nekotine.core.status.effect.StatusEffectModule;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.ticking.TickTimeStamp;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EntityUtil;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.util.InventoryUtil;
import fr.nekotine.core.util.SpatialUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6Keys;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.impl.status.effect.OmniCaptedStatusEffectType;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpEndEvent;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpStartEvent;
import fr.nekotine.vi6clean.impl.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.impl.status.flag.OmniCaptedStatusFlag;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.Collection;
import java.util.LinkedList;
import java.util.stream.Collectors;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

@ToolCode("omnicaptor")
public class OmniCaptorHandler extends ToolHandler<OmniCaptorHandler.OmniCaptor> {

	private final double DETECTION_BLOCK_RANGE = getConfiguration().getDouble("range", 4);

	private final int EFFECT_DURATION = (int) (20 * getConfiguration().getDouble("duration", 3));

	private final double DETECTION_RANGE_SQUARED = DETECTION_BLOCK_RANGE * DETECTION_BLOCK_RANGE;

	private StatusEffect temporaryEffect = new StatusEffect(OmniCaptedStatusEffectType.get(), EFFECT_DURATION);

	private StatusEffect unlimitedEffect = new StatusEffect(OmniCaptedStatusEffectType.get(), -1);

	public OmniCaptorHandler() {
		super(OmniCaptor::new);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
	}

	@EventHandler
	private void onPlayerInterract(PlayerInteractEvent evt) {
		if (evt.getHand() != EquipmentSlot.HAND || !EventUtil.isCustomAction(evt, CustomAction.HIT_ANY)) {
			return;
		}
		var tool = getToolFromItem(evt.getItem());
		if (tool == null) {
			return;
		}
		var player = evt.getPlayer();
		// TRY PICKUP
		var ploc = player.getLocation();
		if (tool.placed != null) {
			if (ploc.distanceSquared(tool.placed.getLocation()) <= DETECTION_RANGE_SQUARED) {
				tool.placed.remove();
				tool.placed = null;
				Vi6Sound.OMNICAPTEUR_PICKUP.play(ploc.getWorld(), ploc);
				editItem(tool, item -> {
					item.setData(DataComponentTypes.ITEM_MODEL, Key.key(Vi6Keys.OMNICAPTOR_ITEM_MODEL));
					item.editMeta(m -> m.displayName(getDisplayName().append(Component.text(" - "))
							.append(Component.text("Disponible", NamedTextColor.BLUE))));
				});
				var flagModule = Ioc.resolve(StatusFlagModule.class);
				for (var p : tool.ennemiesInRange) {
					flagModule.removeFlag(p, OmniCaptedStatusFlag.get());
				}
				tool.ennemiesInRange.clear();
				evt.setCancelled(true);
			}
		} else {
			// TRY PLACE
			if (EntityUtil.IsOnGround(player)) {
				tool.placed = (ItemDisplay) ploc.getWorld().spawnEntity(
						ploc.toVector().add(new Vector(0, 0.5, 0))
								.toLocation(ploc.getWorld()) /* On retire la rotation */,
						EntityType.ITEM_DISPLAY, SpawnReason.CUSTOM, e -> {
							if (e instanceof ItemDisplay display) {
								display.setPersistent(false);
								var stack = new ItemStack(Material.REDSTONE_TORCH);
								stack.setData(DataComponentTypes.ITEM_MODEL, Key.key(Vi6Keys.OMNICAPTOR_ITEM_MODEL));
								display.setItemStack(stack);
							}
						});
				Vi6Sound.OMNICAPTEUR_PLACE.play(ploc.getWorld(), ploc);
				editItem(tool, item -> {
					item.setData(DataComponentTypes.ITEM_MODEL, Material.LEVER.key());
					item.editMeta(m -> m.displayName(getDisplayName().append(Component.text(" - "))
							.append(Component.text("Placé", NamedTextColor.GRAY))));
				});
				evt.setCancelled(true);
			}
		}
	}

	@EventHandler
	private void onPlayerToggleSneak(PlayerToggleSneakEvent evt) {
		var player = evt.getPlayer();
		var glowModule = Ioc.resolve(EntityGlowModule.class);
		for (var item : InventoryUtil.taggedItems(player.getInventory(), TOOL_TYPE_KEY, getToolCode())) {
			var tool = getToolFromItem(item);
			if (tool.sneaking != evt.isSneaking()) {
				tool.sneaking = evt.isSneaking();
				if (tool.placed != null) {
					if (tool.sneaking) {
						glowModule.glowEntityFor(tool.placed, tool.getOwner());
					} else {
						glowModule.unglowEntityFor(tool.placed, tool.getOwner());
					}
				}
			}
		}
	}

	private Collection<Player> inRange(Entity as, OmniCaptor captor) {
		var wrappingModule = Ioc.resolve(WrappingModule.class);
		var wrapper = wrappingModule.getWrapper(captor.getOwner(), PlayerWrapper.class);
		return wrapper.ennemiTeamInMap()
				.filter(ennemi -> ennemi.getLocation().distanceSquared(as.getLocation()) <= DETECTION_RANGE_SQUARED)
				.collect(Collectors.toCollection(LinkedList::new));
	}

	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		var effectModule = Ioc.resolve(StatusEffectModule.class);
		var flagModule = Ioc.resolve(StatusFlagModule.class);
		for (var tool : getTools()) {
			var owner = tool.getOwner();
			if (owner == null) {
				continue;
			}
			if (tool.placed == null) {
				if (evt.timeStampReached(TickTimeStamp.QuartSecond) && tool.sneaking
						&& itemMatch(tool, owner.getInventory().getItemInMainHand())) {
					// Low tick
					var loc = owner.getLocation();
					var x = loc.getX();
					var y = loc.getY();
					var z = loc.getZ();
					SpatialUtil.circle2DDensity(DETECTION_BLOCK_RANGE, 5, 0, (offsetX, offsetZ) -> {
						owner.spawnParticle(Particle.FIREWORK, x + offsetX, y, z + offsetZ, 1, 0, 0, 0, 0, null);
					});
				}
				continue;
			}
			var inRange = inRange(tool.placed, tool);
			if (inRange.size() <= 0 && tool.ennemiesInRange.size() <= 0) {
				continue;
			}
			var ite = tool.ennemiesInRange.iterator();
			while (ite.hasNext()) {
				var p = ite.next();
				if (inRange.contains(p)) {
					inRange.remove(p);
				} else {
					effectModule.addEffect(p, temporaryEffect);
					effectModule.removeEffect(p, unlimitedEffect);
					ite.remove();
				}
			}
			if (flagModule.hasAny(owner, EmpStatusFlag.get())) {
				return;
			}
			for (var p : inRange) {
				effectModule.addEffect(p, unlimitedEffect);
				tool.ennemiesInRange.add(p);
				Vi6Sound.OMNICAPTEUR_DETECT.play(p);
				Vi6Sound.OMNICAPTEUR_DETECT.play(owner);
			}

			if (tool.ennemiesInRange.size() > 0) {
				editItem(tool, item -> {
					item.setData(DataComponentTypes.ITEM_MODEL, Material.REDSTONE_TORCH.key());
					item.editMeta(m -> m.displayName(getDisplayName().append(Component.text(" - "))
							.append(Component.text("Activé", NamedTextColor.RED))));
				});
			} else {
				editItem(tool, item -> {
					item.setData(DataComponentTypes.ITEM_MODEL, Material.LEVER.key());
					item.editMeta(m -> m.displayName(getDisplayName().append(Component.text(" - "))
							.append(Component.text("Placé", NamedTextColor.GRAY))));
				});
			}
		}
	}

	@Override
	protected void onAttachedToPlayer(OmniCaptor tool) {
		if (tool.placed == null) {
			editItem(tool, item -> {
				item.setData(DataComponentTypes.ITEM_MODEL, Key.key(Vi6Keys.OMNICAPTOR_ITEM_MODEL));
				item.editMeta(m -> m.displayName(getDisplayName().append(Component.text(" - "))
						.append(Component.text("Disponible", NamedTextColor.BLUE))));
			});
		} else {
			editItem(tool, item -> {
				item.setData(DataComponentTypes.ITEM_MODEL, Material.LEVER.key());
				item.editMeta(m -> m.displayName(getDisplayName().append(Component.text(" - "))
						.append(Component.text("Placé", NamedTextColor.GRAY))));
			});
		}
	}

	@Override
	protected void onDetachFromPlayer(OmniCaptor tool) {
	}

	@Override
	protected void onToolCleanup(OmniCaptor tool) {
		if (tool.placed != null) {
			tool.placed.remove();
			tool.placed = null;
		}
	}

	@Override
	protected ItemStack makeBaseItem() {
		return new ItemStackBuilder(Material.REPEATER).name(getDisplayName()).lore(getLore()).unstackable()
				.flags(ItemFlag.values()).postApply(item -> {
					item.setData(DataComponentTypes.ITEM_MODEL, Key.key(Vi6Keys.OMNICAPTOR_ITEM_MODEL));
				}).build();
	}

	@EventHandler
	private void onEmpStart(EntityEmpStartEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			var effectModule = Ioc.resolve(StatusEffectModule.class);
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				item.editMeta(m -> m.displayName(getDisplayName().decorate(TextDecoration.STRIKETHROUGH)
						.append(Component.text(" - ")).append(Component.text("Brouillé", NamedTextColor.RED))));
				var tool = getToolFromItem(item);
				var ite = tool.ennemiesInRange.iterator();
				while (ite.hasNext()) {
					var target = ite.next();
					effectModule.removeEffect(target, temporaryEffect);
					effectModule.removeEffect(target, unlimitedEffect);
					ite.remove();
				}
			});
		}
	}

	@EventHandler
	private void onEmpStop(EntityEmpEndEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				var tool = getToolFromItem(item);
				if (tool.placed != null) {
					item.setData(DataComponentTypes.ITEM_MODEL, Material.LEVER.key());
					item.editMeta(m -> m.displayName(getDisplayName().append(Component.text(" - "))
							.append(Component.text("Placé", NamedTextColor.GRAY))));
				} else {
					item.setData(DataComponentTypes.ITEM_MODEL, Key.key(Vi6Keys.OMNICAPTOR_ITEM_MODEL));
					item.editMeta(m -> m.displayName(getDisplayName().append(Component.text(" - "))
							.append(Component.text("Disponible", NamedTextColor.BLUE))));
				}
			});
		}
	}

	public static class OmniCaptor extends Tool {

		public OmniCaptor(ToolHandler<?> handler) {
			super(handler);
		}

		private boolean sneaking;

		private ItemDisplay placed;

		private Collection<Player> ennemiesInRange = new LinkedList<>();
	}
}
