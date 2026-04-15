package fr.nekotine.vi6clean.impl.tool.personal.invisneak;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.status.effect.StatusEffect;
import fr.nekotine.core.status.effect.StatusEffectModule;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.ticking.TickTimeStamp;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.InventoryUtil;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.core.util.SpatialUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.status.effect.invisibility.TrueInvisibilityStatusEffectType;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpEndEvent;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpStartEvent;
import fr.nekotine.vi6clean.impl.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;

@ToolCode("invisneak")
public class InviSneakHandler extends ToolHandler<InviSneak> {

	private final StatusEffect invisibleEffect = new StatusEffect(TrueInvisibilityStatusEffectType.get(), -1);

	private final double DETECTION_BLOCK_RANGE = getConfiguration().getDouble("range", 3);

	private final double DETECTION_RANGE_SQUARED = DETECTION_BLOCK_RANGE * DETECTION_BLOCK_RANGE;

	public InviSneakHandler() {
		super(InviSneak::new);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
	}

	private void statusUpdate(InviSneak tool) {
		var statusEffectModule = Ioc.resolve(StatusEffectModule.class);
		if (tool.isSneaking()) {
			if (tool.isRevealed()) {
				editItem(tool, item -> {
					item.setData(DataComponentTypes.ITEM_MODEL, Material.RED_STAINED_GLASS_PANE.key());
					item.editMeta(m -> m.displayName(getDisplayName().append(Component.text(" - "))
							.append(Component.text("Découvert", NamedTextColor.RED))));
				});
				statusEffectModule.removeEffect(tool.getOwner(), invisibleEffect);
			} else {
				editItem(tool, item -> {
					item.resetData(DataComponentTypes.ITEM_MODEL);
					item.editMeta(m -> m.displayName(getDisplayName().append(Component.text(" - "))
							.append(Component.text("Invisible", NamedTextColor.GRAY))));
				});
				statusEffectModule.addEffect(tool.getOwner(), invisibleEffect);
			}
		} else {
			editItem(tool, item -> {
				item.setData(DataComponentTypes.ITEM_MODEL, Material.WHITE_STAINED_GLASS_PANE.key());
				item.editMeta(m -> m.displayName(getDisplayName().append(Component.text(" - "))
						.append(Component.text("Visible", NamedTextColor.WHITE))));
			});
			statusEffectModule.removeEffect(tool.getOwner(), invisibleEffect);
		}
	}

	@EventHandler
	private void onPlayerToggleSneak(PlayerToggleSneakEvent evt) {
		for (var item : InventoryUtil.taggedItems(evt.getPlayer().getInventory(), TOOL_TYPE_KEY, getToolCode())) {
			var tool = getToolFromItem(item);
			if (tool.isSneaking() != evt.isSneaking()
					&& !Ioc.resolve(StatusFlagModule.class).hasAny(evt.getPlayer(), EmpStatusFlag.get())) {
				tool.setSneaking(evt.isSneaking());
				statusUpdate(tool);
			}
		}
	}

	private boolean isEnnemiNear(PlayerWrapper wrap) {
		var player = wrap.GetWrapped();
		return wrap.ennemiTeamInMap().anyMatch(
				ennemi -> player.getLocation().distanceSquared(ennemi.getLocation()) <= DETECTION_RANGE_SQUARED);
	}

	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		var wrappingModule = Ioc.resolve(WrappingModule.class);
		for (var tool : getTools()) {
			var wrap = wrappingModule.getWrapperOptional(tool.getOwner(), PlayerWrapper.class);
			if (wrap.isEmpty()) {
				continue;
			}
			var revealed = isEnnemiNear(wrap.get());
			if (revealed != tool.isRevealed()) {
				tool.setRevealed(revealed);
				statusUpdate(tool);
			}
			if (evt.timeStampReached(TickTimeStamp.QuartSecond)) {
				// LOW TICK
				if (!tool.isSneaking() || tool.getOwner() == null) {
					return;
				}
				var player = tool.getOwner();
				var loc = player.getLocation();
				var y = loc.getZ();
				var x = loc.getX();
				var z = loc.getZ();
				if (revealed) {
					var world = Ioc.resolve(Vi6Game.class).getWorld();
					Vi6Sound.INVISNEAK_REVEALED.play(world, loc);
					SpatialUtil.circle2DDensity(DETECTION_BLOCK_RANGE, 5, 0, (offsetX, offsetZ) -> {
						player.spawnParticle(Particle.FALLING_DUST, x + offsetX, y, z + offsetZ, 1, 0, 0, 0, 0,
								Bukkit.createBlockData(Material.REDSTONE_BLOCK));
					});
				} else {
					SpatialUtil.circle2DDensity(DETECTION_BLOCK_RANGE, 5, 0, (offsetX, offsetZ) -> {
						player.spawnParticle(Particle.SMOKE, x + offsetX, y, z + offsetZ, 1, 0, 0, 0, 0, null);
					});
				}
			}
		}
	}

	@Override
	protected void onAttachedToPlayer(InviSneak tool) {
		tool.setSneaking(tool.getOwner().isSneaking());
		statusUpdate(tool);
	}

	@Override
	protected void onDetachFromPlayer(InviSneak tool) {
		Ioc.resolve(StatusEffectModule.class).removeEffect(tool.getOwner(), invisibleEffect);
	}

	@Override
	protected void onToolCleanup(InviSneak tool) {
	}

	@Override
	protected ItemStack makeItem(InviSneak tool) {
		return ItemStackUtil.make(Material.GLASS_PANE,
				getDisplayName().append(Component.text(" - ")).append(Component.text("Visible", NamedTextColor.WHITE)),
				getLore());
	}

	@EventHandler
	private void onEmpStart(EntityEmpStartEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				getToolFromItem(item).setSneaking(false);
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
				item.editMeta(m -> m.displayName(getDisplayName().append(Component.text(" - "))
						.append(Component.text("Découvert", NamedTextColor.RED))));
				if (tool.isSneaking() != p.isSneaking()) {
					tool.setSneaking(p.isSneaking());
					statusUpdate(tool);
				}
			});
		}
	}
}
