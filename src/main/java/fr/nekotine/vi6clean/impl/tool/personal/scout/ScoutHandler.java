package fr.nekotine.vi6clean.impl.tool.personal.scout;

import java.time.Duration;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.inventory.ItemStackBuilder;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.status.effect.StatusEffect;
import fr.nekotine.core.status.effect.StatusEffectModule;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.ticking.TickTimeStamp;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.InventoryUtil;
import fr.nekotine.core.util.SpatialUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6Keys;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.status.effect.invisibility.TrueInvisibilityStatusEffectType;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpEndEvent;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpStartEvent;
import fr.nekotine.vi6clean.impl.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.util.Tick;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

@ToolCode("scout")
public class ScoutHandler extends ToolHandler<ScoutHandler.Scout> {

	private final StatusEffect invisibleEffect = new StatusEffect(TrueInvisibilityStatusEffectType.get(), -1);

	private final double DETECTION_BLOCK_RANGE = getConfiguration().getDouble("range", 3);

	private final double DETECTION_RANGE_SQUARED = DETECTION_BLOCK_RANGE * DETECTION_BLOCK_RANGE;

	private final int STILL_TICK_NEEDED = Tick.tick().fromDuration(Duration.ofSeconds(4));

	public ScoutHandler() {
		super(Scout::new);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
	}

	private void statusUpdate(Scout tool) {
		var statusEffectModule = Ioc.resolve(StatusEffectModule.class);
		var owner = tool.getOwner();
		if (!owner.hasCooldown(Material.BUSH)) {
			if (tool.revealed) {
				editItem(tool, item -> {
					item.editMeta(m -> m.displayName(getDisplayName().append(Component.text(" - "))
							.append(Component.text("Découvert", NamedTextColor.RED))));
				});
				statusEffectModule.removeEffect(tool.getOwner(), invisibleEffect);
				tool.invisible = false;
			} else {
				editItem(tool, item -> {
					item.editMeta(m -> m.displayName(getDisplayName().append(Component.text(" - "))
							.append(Component.text("Invisible", NamedTextColor.GRAY))));
				});
				statusEffectModule.addEffect(tool.getOwner(), invisibleEffect);
				tool.invisible = true;
			}
		} else {
			editItem(tool, item -> {
				item.editMeta(m -> m.displayName(getDisplayName().append(Component.text(" - "))
						.append(Component.text("Visible", NamedTextColor.WHITE))));
			});
			statusEffectModule.removeEffect(tool.getOwner(), invisibleEffect);
			tool.invisible = false;
		}
	}

	private boolean isEnnemiNear(Player player) {
		var wrappingModule = Ioc.resolve(WrappingModule.class);
		return wrappingModule.getWrapper(player, PlayerWrapper.class).ennemiTeamInMap().anyMatch(
				ennemi -> player.getLocation().distanceSquared(ennemi.getLocation()) <= DETECTION_RANGE_SQUARED);
	}

	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		for (var tool : getTools()) {
			var owner = tool.getOwner();
			if (owner == null) {
				continue;
			}
			var revealed = isEnnemiNear(owner)
					|| Ioc.resolve(StatusFlagModule.class).hasAny(owner, EmpStatusFlag.get());
			if (revealed != tool.revealed) {
				tool.revealed = revealed;
				statusUpdate(tool);
			} else if (!owner.hasCooldown(Material.BUSH) && !tool.invisible) {
				statusUpdate(tool);
			}
			if (evt.timeStampReached(TickTimeStamp.QuartSecond)) {
				// LOW TICK
				if (!tool.invisible || tool.getOwner() == null) {
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

	@EventHandler
	public void onMove(PlayerMoveEvent e) {
		for (var tool : getTools()) {
			var owner = tool.getOwner();
			if (e.getPlayer().equals(owner) && e.hasExplicitlyChangedPosition()
					&& e.getFrom().toVector().distanceSquared(e.getTo().toVector()) > 0.001) {
				owner.setCooldown(Material.BUSH, STILL_TICK_NEEDED);
				if (tool.invisible) {
					statusUpdate(tool);
				}
			}
		}
	}

	@Override
	protected void onAttachedToPlayer(Scout tool) {
		statusUpdate(tool);
	}

	@Override
	protected void onDetachFromPlayer(Scout tool) {
		Ioc.resolve(StatusEffectModule.class).removeEffect(tool.getOwner(), invisibleEffect);
	}

	@Override
	protected void onToolCleanup(Scout tool) {
	}

	@Override
	protected ItemStack makeBaseItem() {
		return new ItemStackBuilder(Material.BUSH).name(getDisplayName()).lore(getLore()).unstackable()
				.flags(ItemFlag.values())
				.postApply(item -> item.setData(DataComponentTypes.ITEM_MODEL, Key.key(Vi6Keys.SCOUT_ITEM_MODEL)))
				.build();
	}

	@EventHandler
	private void onEmpStart(EntityEmpStartEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				// item.setData(DataComponentTypes.ITEM_MODEL, Material.QUARTZ_PILLAR.key());
				item.editMeta(m -> m.displayName(getDisplayName().decorate(TextDecoration.STRIKETHROUGH)
						.append(Component.text(" - ")).append(Component.text("Brouillé", NamedTextColor.RED))));
			});
		}
	}

	@EventHandler
	private void onEmpStop(EntityEmpEndEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				// item.resetData(DataComponentTypes.ITEM_MODEL); // back to default model
				item.editMeta(m -> m.displayName(getDisplayName()));
			});
		}
	}

	public static class Scout extends Tool {

		private boolean revealed;

		private boolean invisible;

		public Scout(ToolHandler<?> handler) {
			super(handler);
		}

	}
}
