package fr.nekotine.vi6clean.tool.personal.murmur;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.status.effect.StatusEffect;
import fr.nekotine.core.status.effect.StatusEffectModule;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.util.InventoryUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.status.effect.MurmurStatusEffectType;
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
import net.kyori.adventure.text.minimessage.MiniMessage;

@ToolCode("murmur")
public class MurmurHandler extends ToolHandler<MurmurHandler.Murmur> {

	private final double DURATION = getConfiguration().getDouble("duration", 10.0);
	private final int DURATION_TICKS = (int) (20 * DURATION);
	private final StatusEffect EFFECT = new StatusEffect(MurmurStatusEffectType.get(), DURATION_TICKS);
	private final String MESSAGE = "<red>Vous êtes anormalement <b>essouflé</b><br>"
			+ "Votre <b>stamina</b> et votre <b>air</b> ne peuvent plus remonter pendant <aqua>" + DURATION
			+ "</aqua> secondes</red>";
	private final int COOLDOWN_TICK = (int) (20 * getConfiguration().getDouble("cooldown", 60));

	public MurmurHandler() {
		super(Murmur::new);
	}

	@Override
	protected void onAttachedToPlayer(Murmur tool) {
	}

	@Override
	protected void onDetachFromPlayer(Murmur tool) {
	}

	@Override
	protected void onToolCleanup(Murmur tool) {
	}

	@EventHandler
	private void onPlayerInteract(PlayerInteractEvent evt) {
		if (evt.getHand() != EquipmentSlot.HAND && !EventUtil.isCustomAction(evt, CustomAction.HIT_ANY)) {
			return;
		}
		var statusModule = Ioc.resolve(StatusFlagModule.class);
		var player = evt.getPlayer();
		var item = evt.getItem();
		var tool = getToolFromItem(item);
		if (tool == null || statusModule.hasAny(player, EmpStatusFlag.get()) || player.hasCooldown(item)) {
			return;
		}

		evt.setCancelled(true);
		var optWrapper = Ioc.resolve(WrappingModule.class).getWrapperOptional(player, PlayerWrapper.class);
		if (optWrapper.isPresent()) {
			var statusEffectModule = Ioc.resolve(StatusEffectModule.class);
			optWrapper.get().enemyTeamInMap().forEach(p -> {
				p.sendMessage(MiniMessage.miniMessage().deserialize(MESSAGE));
				statusEffectModule.addEffect(p, EFFECT);
			});
		}
		// remove(tool);
		player.setCooldown(item, COOLDOWN_TICK);
	}

	@EventHandler
	private void onEmpStart(EntityEmpStartEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				item.setData(DataComponentTypes.ITEM_MODEL, Material.HEART_POTTERY_SHERD.key());
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

	public static class Murmur extends Tool {
		public Murmur(ToolHandler<?> handler) {
			super(handler);
		}
	}
}
