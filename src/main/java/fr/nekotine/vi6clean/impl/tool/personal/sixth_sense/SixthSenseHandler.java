package fr.nekotine.vi6clean.impl.tool.personal.sixth_sense;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;

import fr.nekotine.core.glow.EntityGlowModule;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.InventoryUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpEndEvent;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpStartEvent;
import fr.nekotine.vi6clean.impl.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

@ToolCode("sixth_sense")
public class SixthSenseHandler extends ToolHandler<SixthSenseHandler.SixthSense> {

	private final double DETECTION_BLOCK_RANGE = getConfiguration().getDouble("range", 6);

	private final double DETECTION_RANGE_SQUARED = DETECTION_BLOCK_RANGE * DETECTION_BLOCK_RANGE;

	public SixthSenseHandler() {
		super(SixthSense::new);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
	}

	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		var glowModule = Ioc.resolve(EntityGlowModule.class);
		var wrappingModule = Ioc.resolve(WrappingModule.class);
		var statusFlagModule = Ioc.resolve(StatusFlagModule.class);
		for (var tool : getTools()) {
			var owner = tool.getOwner();
			if (owner == null) {
				continue;
			}
			var wrap = wrappingModule.getWrapper(owner, PlayerWrapper.class);
			var ownerloc = owner.getLocation();
			var isEmped = statusFlagModule.hasAny(owner, EmpStatusFlag.get());
			wrap.enemyTeamInMap().forEach(en -> {
				if (!isEmped && ownerloc.getWorld().equals(en.getWorld()) && ownerloc.distanceSquared(en.getLocation()) <= DETECTION_RANGE_SQUARED) {
					glowModule.glowEntityFor(en, owner);
				} else {
					glowModule.unglowEntityFor(en, owner);
				}
			});
		}
	}

	@Override
	protected void onAttachedToPlayer(SixthSense tool) {
	}

	@Override
	protected void onDetachFromPlayer(SixthSense tool) {
	}

	@Override
	protected void onToolCleanup(SixthSense tool) {
	}

	@EventHandler
	private void onEmpStart(EntityEmpStartEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				item.setData(DataComponentTypes.ITEM_MODEL, Material.NAUTILUS_SHELL.key());
				item.editMeta(m -> m.displayName(getDisplayName().decorate(TextDecoration.STRIKETHROUGH)
						.append(Component.text(" - ")).append(Component.text("Brouillé", NamedTextColor.RED))));
			});
		}
	}

	@EventHandler
	private void onEmpStop(EntityEmpEndEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				item.resetData(DataComponentTypes.ITEM_MODEL);
				item.editMeta(m -> m.displayName(getDisplayName()));
			});
		}
	}

	public static class SixthSense extends Tool {

		public SixthSense(ToolHandler<?> handler) {
			super(handler);
		}

	}
}
