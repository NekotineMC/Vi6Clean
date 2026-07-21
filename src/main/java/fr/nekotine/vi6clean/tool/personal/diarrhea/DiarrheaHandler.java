package fr.nekotine.vi6clean.tool.personal.diarrhea;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.status.effect.StatusEffect;
import fr.nekotine.core.status.effect.StatusEffectModule;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.util.InventoryUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.status.effect.DiarrheaStatusEffectType;
import fr.nekotine.vi6clean.status.event.EntityEmpEndEvent;
import fr.nekotine.vi6clean.status.event.EntityEmpStartEvent;
import fr.nekotine.vi6clean.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.tool.Tool;
import fr.nekotine.vi6clean.tool.ToolCode;
import fr.nekotine.vi6clean.tool.ToolHandler;
import fr.nekotine.vi6clean.wrapper.PlayerWrapper;

import org.bukkit.Material;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

@ToolCode("diarrhea")
public class DiarrheaHandler extends ToolHandler<DiarrheaHandler.Diarrhea> {
	private final double DURATION_USE = getConfiguration().getDouble("duration_use", 5);
	private final double DURATION_HIT = getConfiguration().getDouble("duration_hit", 5);
	private final int DURATION_USE_TICKS = (int) (20 * DURATION_USE);
	private final int DURATION_HIT_TICKS = (int) (20 * DURATION_HIT);
	private final int COOLDOWN_TICKS = (int) (20 * getConfiguration().getDouble("cooldown", 120));
	private final StatusEffect EFFECT_USE = new StatusEffect(DiarrheaStatusEffectType.get(), DURATION_USE_TICKS);
	private final StatusEffect EFFECT_HIT = new StatusEffect(DiarrheaStatusEffectType.get(), DURATION_HIT_TICKS);
	private final String MESSAGE = "<red>Le gouvernement contrôle votre corps grâce à la 5G<br>"
			+ "Les ondes déclenchent une <b>diarhée fulgurante</b> pendant <aqua>" + DURATION_USE + "s</aqua>";

	public DiarrheaHandler() {
		super(Diarrhea::new);
	}

	//

	@Override
	protected void onAttachedToPlayer(Diarrhea tool) {
	}

	@Override
	protected void onDetachFromPlayer(Diarrhea tool) {
	}

	@Override
	protected void onToolCleanup(Diarrhea tool) {
	}

	@EventHandler
	private void onDamageEvent(EntityDamageByEntityEvent evt) {
		var statusFlagModule = Ioc.resolve(StatusFlagModule.class);
		for (var tool : getTools()) {
			var owner = tool.getOwner();
			if (!evt.getDamager().equals(owner) || statusFlagModule.hasAny(owner, EmpStatusFlag.get())) {
				continue;
			}
			var wrappingModule = Ioc.resolve(WrappingModule.class);
			var wrapO = wrappingModule.getWrapperOptional(owner, PlayerWrapper.class);
			if (wrapO.isEmpty()) {
				return;
			}
			if (wrapO.get().enemyTeamInMap().anyMatch(p -> p.equals(evt.getEntity()))) {
				var effectModule = Ioc.resolve(StatusEffectModule.class);
				effectModule.addEffect((Player) evt.getEntity(), EFFECT_HIT);
			}
		}
	}

	@EventHandler
	private void onPlayerInteract(PlayerInteractEvent evt) {
		var tool = getToolFromItem(evt.getItem());
		if (tool == null) {
			return;
		}
		var owner = tool.getOwner();
		if (owner.hasCooldown(evt.getItem())
				|| Ioc.resolve(StatusFlagModule.class).hasAny(owner, EmpStatusFlag.get())) {
			return;
		}
		if (EventUtil.isCustomAction(evt, CustomAction.HIT_ANY)) {
			var wrappingModule = Ioc.resolve(WrappingModule.class);
			var wrapO = wrappingModule.getWrapperOptional(owner, PlayerWrapper.class);
			if (wrapO.isEmpty()) {
				return;
			}
			evt.setCancelled(true);
			var effectModule = Ioc.resolve(StatusEffectModule.class);
			wrapO.get().enemyTeamInMap().forEach(p -> {
				effectModule.addEffect(p, EFFECT_USE);
				p.sendMessage(MiniMessage.miniMessage().deserialize(MESSAGE));
			});
			owner.setCooldown(evt.getItem(), COOLDOWN_TICKS);
		}
	}

	@EventHandler
	private void onEmpStart(EntityEmpStartEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				item.setData(DataComponentTypes.ITEM_MODEL, Material.MUSHROOM_STEW.key());
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

	public static class Diarrhea extends Tool {
		public Diarrhea(ToolHandler<?> handler) {
			super(handler);
		}
	}
}
