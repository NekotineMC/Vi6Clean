package fr.nekotine.vi6clean.tool.personal.dephaser;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.status.effect.StatusEffect;
import fr.nekotine.core.status.effect.StatusEffectModule;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.InventoryUtil;
import fr.nekotine.core.util.TimeUtil;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.status.effect.invisibility.InvisibilityStatusEffectType;
import fr.nekotine.vi6clean.status.event.EntityEmpEndEvent;
import fr.nekotine.vi6clean.status.event.EntityEmpStartEvent;
import fr.nekotine.vi6clean.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.tool.ToolCode;
import fr.nekotine.vi6clean.tool.ToolHandler;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.util.Tick;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

@ToolCode("dephaser")
public class DephaserHandler extends ToolHandler<Dephaser> {

	private final int DELAY_BETWEEN_INVISIBILITY_TICKS = Tick.tick()
			.fromDuration(TimeUtil.fromSeconds(getConfiguration().getDouble("inv_delay", 20)));

	private final int INVISIBILITY_DURATION_TICKS = Tick.tick()
			.fromDuration(TimeUtil.fromSeconds(getConfiguration().getDouble("inv_duration", 2)));

	private final int DELAY_BETWEEN_WARNING_SOUND = Tick.tick().fromDuration(TimeUtil.fromSeconds(0.5));

	private final StatusEffect effect = new StatusEffect(InvisibilityStatusEffectType.get(),
			INVISIBILITY_DURATION_TICKS);

	public DephaserHandler() {
		super(Dephaser::new);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
	}

	//

	@EventHandler
	public void onTick(TickElapsedEvent evt) {
		var flagModule = Ioc.resolve(StatusFlagModule.class);
		var empFlag = EmpStatusFlag.get();
		for (var tool : getTools()) {
			var player = tool.getOwner();
			if (player == null) {
				continue;
			}
			int cd = player.getCooldown(Material.IRON_NUGGET);
			if (tool.isActive()) {
				if (flagModule.hasAny(player, empFlag)) {
					deactivate(tool, false);
					player.setCooldown(Material.IRON_NUGGET,
							DELAY_BETWEEN_INVISIBILITY_TICKS - INVISIBILITY_DURATION_TICKS);
					continue;
				}
				if (cd == DELAY_BETWEEN_WARNING_SOUND * 2) {
					Vi6Sound.DEPHASER_WARNING_HIGH.play(player);
				} else if (cd == DELAY_BETWEEN_WARNING_SOUND) {
					Vi6Sound.DEPHASER_WARNING_MID.play(player);
				} else if (cd == 0) {
					deactivate(tool, true);
				}
			} else {
				if (cd == DELAY_BETWEEN_WARNING_SOUND * 2) {
					if (!flagModule.hasAny(player, empFlag)) {
						Vi6Sound.DEPHASER_WARNING_LOW.play(player);
					}
				} else if (cd == DELAY_BETWEEN_WARNING_SOUND) {
					if (!flagModule.hasAny(player, empFlag)) {
						Vi6Sound.DEPHASER_WARNING_MID.play(player);
					}
				} else if (cd == 0) {
					activate(tool);
				}
			}
		}
	}

	private void activate(Dephaser tool) {
		var player = tool.getOwner();
		if (Ioc.resolve(StatusFlagModule.class).hasAny(player, EmpStatusFlag.get())) {
			player.setCooldown(Material.IRON_NUGGET, DELAY_BETWEEN_INVISIBILITY_TICKS - INVISIBILITY_DURATION_TICKS);
			return;
		}
		Ioc.resolve(StatusEffectModule.class).addEffect(player, effect);
		Vi6Sound.DEPHASER_ACTIVATE.play(player);
		player.setCooldown(Material.IRON_NUGGET, INVISIBILITY_DURATION_TICKS);
		tool.setActive(true);
	}

	private void deactivate(Dephaser tool, boolean playSoundAndCooldown) {
		var player = tool.getOwner();
		Ioc.resolve(StatusEffectModule.class).removeEffect(player, effect);
		if (playSoundAndCooldown) {
			Vi6Sound.DEPHASER_DEACTIVATE.play(player);
			player.setCooldown(Material.IRON_NUGGET, DELAY_BETWEEN_INVISIBILITY_TICKS - INVISIBILITY_DURATION_TICKS);
		}
		tool.setActive(false);
	}

	@Override
	protected void onAttachedToPlayer(Dephaser tool) {
		if (tool.getOwner().getCooldown(Material.IRON_NUGGET) <= 0) {
			tool.getOwner().setCooldown(Material.IRON_NUGGET,
					DELAY_BETWEEN_INVISIBILITY_TICKS - INVISIBILITY_DURATION_TICKS);
		}
	}

	@Override
	protected void onDetachFromPlayer(Dephaser tool) {
		deactivate(tool, false);
	}

	@Override
	protected void onToolCleanup(Dephaser tool) {
	}

	@EventHandler
	private void onEmpStart(EntityEmpStartEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				item.setData(DataComponentTypes.ITEM_MODEL, Material.GOLD_NUGGET.key());
				item.editMeta(m -> m.displayName(getDisplayName().decorate(TextDecoration.STRIKETHROUGH)
						.append(Component.text(" - ")).append(Component.text("Brouillé", NamedTextColor.RED))));
				var tool = getToolFromItem(item);
				if (tool != null && tool.isActive()) {
					deactivate(tool, false);
					p.setCooldown(Material.IRON_NUGGET, DELAY_BETWEEN_INVISIBILITY_TICKS - INVISIBILITY_DURATION_TICKS);
				}
			});
		}
	}

	@EventHandler
	private void onEmpEnd(EntityEmpEndEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				item.resetData(DataComponentTypes.ITEM_MODEL);
				item.editMeta(m -> m.displayName(getDisplayName()));
			});
		}
	}
}
