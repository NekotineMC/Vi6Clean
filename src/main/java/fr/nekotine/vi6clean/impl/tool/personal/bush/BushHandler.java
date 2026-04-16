package fr.nekotine.vi6clean.impl.tool.personal.bush;

import com.destroystokyo.paper.MaterialSetTag;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.status.effect.StatusEffect;
import fr.nekotine.core.status.effect.StatusEffectModule;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.InventoryUtil;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.status.effect.invisibility.InvisibilityStatusEffectType;
import fr.nekotine.vi6clean.impl.status.effect.invisibility.TrueInvisibilityStatusEffectType;
import fr.nekotine.vi6clean.impl.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

@ToolCode("bush")
public class BushHandler extends ToolHandler<Bush> {

	public BushHandler() {
		super(Bush::new);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
	}

	private final MaterialSetTag bushMaterials = new MaterialSetTag(
			NamespacedKey.fromString("bush_hideout", Ioc.resolve(JavaPlugin.class)), Material.PEONY,
			Material.TALL_GRASS, Material.LARGE_FERN, Material.LILAC, Material.ROSE_BUSH, Material.SMALL_DRIPLEAF,
			Material.KELP);
	
	private final MaterialSetTag tinyBushMaterials = new MaterialSetTag(
			NamespacedKey.fromString("tiny_bush_hideout", Ioc.resolve(JavaPlugin.class)), Stream.concat(Set.of(Material.SHORT_GRASS,
			Material.FERN, Material.BUSH, Material.FIREFLY_BUSH, Material.SUGAR_CANE, Material.TALL_DRY_GRASS,
			Material.DEAD_BUSH, Material.SEAGRASS).stream(), Tag.SAPLINGS.getValues().stream()).collect(Collectors.toList()));

	private final int FADE_OFF_DELAY = getConfiguration().getInt("fadeoff", 30);

	private final double DETECTION_BLOCK_RANGE = getConfiguration().getDouble("reveal_range", 2);

	private final double DETECTION_RANGE_SQUARED = DETECTION_BLOCK_RANGE * DETECTION_BLOCK_RANGE;

	private final StatusEffect unlimitedInvisibility = new StatusEffect(TrueInvisibilityStatusEffectType.get(), -1);

	private final StatusEffect fadeoffInvisibility = new StatusEffect(InvisibilityStatusEffectType.get(),
			FADE_OFF_DELAY);

	@Override
	protected void onAttachedToPlayer(Bush tool) {
		onTickCheckForStatusChange(tool);
	}

	@Override
	protected void onDetachFromPlayer(Bush tool) {
		onToolCleanup(tool); // applies same as when item deleted
	}

	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		for (Player p : Ioc.resolve(Vi6Game.class).getPlayerList()) {
			var toolsItems = InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode());
			for (var toolItem : toolsItems) {
				var tool = getToolFromItem(toolItem);
				onTickCheckForStatusChange(tool);
			}
		}
	}

	private void onTickCheckForStatusChange(Bush tool) {
		var owner = tool.getOwner();
		if (owner == null) {
			return;
		}
		var scale = owner.getAttribute(Attribute.SCALE).getValue();
		var inBush = bushMaterials.isTagged(owner.getLocation().getBlock()) || (scale <= 0.5 && owner.isSneaking() && tinyBushMaterials.isTagged(owner.getLocation().getBlock()));
		var revealed = false;
		var wrap = Ioc.resolve(WrappingModule.class).getWrapperOptional(owner, PlayerWrapper.class);
		if (wrap.isPresent()) {
			revealed = wrap.get().ennemiTeamInMap()
					.anyMatch(e -> e.getLocation().distanceSquared(owner.getLocation()) <= DETECTION_RANGE_SQUARED)
					|| Ioc.resolve(StatusFlagModule.class).hasAny(owner, EmpStatusFlag.get());
		}
		if (inBush != tool.isInBush() || revealed != tool.isRevealed()) {
			// APPLY THE MATHCING STATEs
			var statusEffectModule = Ioc.resolve(StatusEffectModule.class);
			if (inBush) {
				if (tool.getFadeOffTask() != null) {
					tool.getFadeOffTask().cancel();
					tool.setFadeOffTask(null);
				}
				if (revealed) {
					editItem(tool, item -> {
						item.setData(DataComponentTypes.ITEM_MODEL, Material.CHERRY_LEAVES.key());
						item.editMeta(m -> m.displayName(getDisplayName().append(Component.text(" - "))
								.append(Component.text("Découvert", NamedTextColor.RED))));
					});

					statusEffectModule.removeEffect(owner, unlimitedInvisibility);
					statusEffectModule.removeEffect(owner, fadeoffInvisibility);
					owner.setCooldown(Material.TALL_GRASS, 0);
				} else {
					editItem(tool, item -> {
						item.setData(DataComponentTypes.ITEM_MODEL, Material.AZALEA_LEAVES.key());
						item.editMeta(m -> m.displayName(getDisplayName().append(Component.text(" - "))
								.append(Component.text("Invisible", NamedTextColor.GRAY))));
					});
					owner.setCooldown(Material.TALL_GRASS, 0);
					statusEffectModule.removeEffect(owner, fadeoffInvisibility);
					statusEffectModule.addEffect(owner, unlimitedInvisibility);
				}
			} else {
				editItem(tool, item -> {
					item.setData(DataComponentTypes.ITEM_MODEL, Material.FLOWERING_AZALEA_LEAVES.key());
					item.editMeta(m -> m.displayName(getDisplayName().append(Component.text(" - "))
							.append(Component.text("Visible", NamedTextColor.WHITE))));
				});
				statusEffectModule.removeEffect(owner, unlimitedInvisibility);
				statusEffectModule.addEffect(owner, fadeoffInvisibility);
				if (tool.isInBush()) {
					// if we where previously in bush, apply fadeoff invisibility
					if (tool.getFadeOffTask() != null) {
						tool.getFadeOffTask().cancel();
					}
					tool.setFadeOffTask(new BukkitRunnable() {

						@Override
						public void run() {
							onTickCheckForStatusChange(tool);
						}
					}.runTaskLater(Ioc.resolve(JavaPlugin.class), FADE_OFF_DELAY));
					owner.setCooldown(Material.TALL_GRASS, FADE_OFF_DELAY);
					statusEffectModule.removeEffect(owner, unlimitedInvisibility);
					statusEffectModule.addEffect(owner, fadeoffInvisibility);
				} else {
					editItem(tool, item -> {
						item.setData(DataComponentTypes.ITEM_MODEL, Material.FLOWERING_AZALEA_LEAVES.key());
						item.editMeta(m -> m.displayName(getDisplayName().append(Component.text(" - "))
								.append(Component.text("Visible", NamedTextColor.WHITE))));
					});
					statusEffectModule.removeEffect(owner, unlimitedInvisibility);
					statusEffectModule.removeEffect(owner, fadeoffInvisibility);
					if (tool.getFadeOffTask() != null) {
						tool.getFadeOffTask().cancel();
						tool.setFadeOffTask(null);
					}
				}
			}
		}
		tool.setInBush(inBush);
		tool.setRevealed(revealed);
	}

	@Override
	protected void onToolCleanup(Bush tool) {
		if (tool.getFadeOffTask() != null) {
			tool.getFadeOffTask().cancel();
			tool.setFadeOffTask(null);
		}
		var owner = tool.getOwner();
		if (owner == null) {
			return;
		}
		var statusEffectModule = Ioc.resolve(StatusEffectModule.class);
		statusEffectModule.removeEffect(owner, unlimitedInvisibility);
		statusEffectModule.removeEffect(owner, fadeoffInvisibility);
	}

	@Override
	protected ItemStack makeItem(Bush tool) {
		return ItemStackUtil.make(Material.TALL_GRASS, getDisplayName(), // .append(Component.text(" -
				// ")).append(Component.text("Visible",
				// NamedTextColor.WHITE)),
				getLore());
	}
}
