package fr.nekotine.vi6clean.impl.tool.personal.sonar;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.inventory.ItemStackBuilder;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.InventoryUtil;
import fr.nekotine.core.util.SpatialUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6Sound;
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

@ToolCode("sonar")
public class SonarHandler extends ToolHandler<SonarHandler.Sonar>{

	private final double DETECTION_BLOCK_RANGE = getConfiguration().getDouble("range", 5);
	
	private final double DETECTION_RANGE_SQUARED = DETECTION_BLOCK_RANGE * DETECTION_BLOCK_RANGE;
	
	private final int DELAY_TICK = (int)(20*getConfiguration().getDouble("delay", 3));
	
	public SonarHandler() {
		super(Sonar::new);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
	}
	
	
	private int counter = 0;
	
	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		if(++counter >= DELAY_TICK) {
			counter = 0;
			for (var tool : getTools()) {
				var player = tool.getOwner();
				if (player == null) {
					continue;
				}
				var statusFlagModule = Ioc.resolve(StatusFlagModule.class);
				if(statusFlagModule.hasAny(player, EmpStatusFlag.get())) {
					continue;
				}
				var opt = Ioc.resolve(WrappingModule.class).getWrapperOptional(player, PlayerWrapper.class);
				if (opt.isEmpty()) {
					continue;
				}
				var loc = player.getLocation();
				var x = loc.getX();
				var y = loc.getY() + 0.1;
				var z = loc.getZ();
				if (opt.get().ennemiTeamInMap().anyMatch(e -> player.getLocation().distanceSquared(e.getLocation()) <= DETECTION_RANGE_SQUARED)) {
					Vi6Sound.SONAR_POSITIVE.play(player.getLocation().getWorld(), player.getLocation());
					SpatialUtil.circle2DDensity(DETECTION_BLOCK_RANGE, 2, 0,
							(offsetX, offsetZ) -> {
								player.spawnParticle(Particle.CRIT, x + offsetX, y, z + offsetZ, 1, 0, 0, 0, 0, null);
							});
				}else {
					Vi6Sound.SONAR_NEGATIVE.play(player);
					SpatialUtil.circle2DDensity(DETECTION_BLOCK_RANGE, 5, 0,
							(offsetX, offsetZ) -> {
								player.spawnParticle(Particle.CRIT, x + offsetX, y, z + offsetZ, 1, 0, 0, 0, 0, null);
							});
				}
				editItem(tool, item -> player.setCooldown(item.getType(), DELAY_TICK));
			}
		}
	}

	@Override
	protected void onAttachedToPlayer(Sonar tool) {
	}

	@Override
	protected void onDetachFromPlayer(Sonar tool) {
	}

	@Override
	protected void onToolCleanup(Sonar tool) {
	}

	@Override
	protected ItemStack makeItem(Sonar tool) {
		return new ItemStackBuilder(Material.TARGET)
				.name(getDisplayName())
				.lore(getLore())
				.unstackable()
				.flags(ItemFlag.values())
				.build();
	}
	
	@EventHandler
	private void onEmpStart(EntityEmpStartEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				item.setData(DataComponentTypes.ITEM_MODEL, Material.QUARTZ_PILLAR.key());
				item.editMeta(m -> m.displayName(getDisplayName().decorate(TextDecoration.STRIKETHROUGH).append(Component.text(" - ")).append(Component.text("Brouillé" , NamedTextColor.RED))));
			});
		}
	}
	
	@EventHandler
	private void onEmpStop(EntityEmpEndEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				item.unsetData(DataComponentTypes.ITEM_MODEL); // back to default model
				item.editMeta(m -> m.displayName(getDisplayName()));
			});
		}
	}
	
	public static class Sonar extends Tool{

		public Sonar(ToolHandler<?> handler) {
			super(handler);
		}
		
	}
	
}
