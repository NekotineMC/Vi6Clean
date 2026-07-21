package fr.nekotine.vi6clean.tool.personal.cactus;

import org.bukkit.Material;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.util.InventoryUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6Team;
import fr.nekotine.vi6clean.game.Vi6Game;
import fr.nekotine.vi6clean.status.event.EntityEmpEndEvent;
import fr.nekotine.vi6clean.status.event.EntityEmpStartEvent;
import fr.nekotine.vi6clean.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.tool.Tool;
import fr.nekotine.vi6clean.tool.ToolCode;
import fr.nekotine.vi6clean.tool.ToolHandler;
import fr.nekotine.vi6clean.wrapper.PlayerWrapper;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

@ToolCode("cactus")
public class CactusHandler extends ToolHandler<CactusHandler.Cactus> {

	private final double DAMAGE = getConfiguration().getDouble("damage", 2);

	public CactusHandler() {
		super(Cactus::new);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
	}

	@EventHandler
	private void onDamage(EntityDamageByEntityEvent evt) {
		var victimE = evt.getEntity();
		if (!(victimE instanceof Player victim)) {
			return;
		}
		if (evt.getDamageSource().getDamageType() == DamageType.THORNS) {
			return;
		}
		var wrappingModule = Ioc.resolve(WrappingModule.class);
		var statusFlagModule = Ioc.resolve(StatusFlagModule.class);
		var wrapO = wrappingModule.getWrapperOptional(victim, PlayerWrapper.class);
		if (wrapO.isEmpty()) {
			return; // Victim has no team
		}
		var wrap = wrapO.get();
		for (var tool : getTools()) {
			var owner = tool.getOwner();
			if (!evt.getDamager().equals(owner) || statusFlagModule.hasAny(owner, EmpStatusFlag.get())) {
				continue;
			}
			if (wrap.getTeam() == Vi6Team.THIEF) {
				for (var thief : Ioc.resolve(Vi6Game.class).getThiefs()) {
					if (!victim.equals(thief) && !owner.equals(thief)) {
						thief.damage(DAMAGE, DamageSource.builder(DamageType.THORNS).withDirectEntity(owner).build());
						// thief.damage(DAMAGE, owner);
					}
				}
			} else if (wrap.getTeam() == Vi6Team.GUARD) {
				// Probablement innutile, mais on sait jamais
				for (var guard : Ioc.resolve(Vi6Game.class).getGuards()) {
					if (!victim.equals(guard) && !owner.equals(guard)) {
						guard.damage(DAMAGE, DamageSource.builder(DamageType.THORNS).withDirectEntity(owner).build());
					}
				}
			}
		}
	}

	@Override
	protected void onAttachedToPlayer(Cactus tool) {
	}

	@Override
	protected void onDetachFromPlayer(Cactus tool) {
	}

	@Override
	protected void onToolCleanup(Cactus tool) {
	}

	@EventHandler
	private void onEmpStart(EntityEmpStartEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				item.setData(DataComponentTypes.ITEM_MODEL, Material.SUGAR_CANE.key());
				item.editMeta(m -> m.displayName(getDisplayName().decorate(TextDecoration.STRIKETHROUGH)
						.append(Component.text(" - ")).append(Component.text("Brouillé", NamedTextColor.RED))));
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

	public static class Cactus extends Tool {

		public Cactus(ToolHandler<?> handler) {
			super(handler);
		}

	}
}
