package fr.nekotine.vi6clean.impl.tool.personal.hot_potato;

import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerDropItemEvent;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.status.effect.StatusEffect;
import fr.nekotine.core.status.effect.StatusEffectModule;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6Team;
import fr.nekotine.vi6clean.impl.status.effect.TazedStatusEffectType;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpEndEvent;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpStartEvent;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import fr.nekotine.core.util.InventoryUtil;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

@ToolCode("hot_potato")
public class HotPotatoHandler extends ToolHandler<HotPotatoHandler.HotPotato> {
	private final double DAMAGE = getConfiguration().getDouble("damage", 4);
	private final double TAZ_DURATION = getConfiguration().getDouble("taz_duration", 4);
	// private final int PICKUP_DELAY_TICKS = (int) (20 *
	// (getConfiguration().getDouble("pickup_delay", 3)));
	private final double VELOCITY_MULTIPLIER = getConfiguration().getDouble("velocity_multiplier", 4);
	private final StatusEffect TAZ_EFFECT = new StatusEffect(TazedStatusEffectType.get(), (int) (20 * TAZ_DURATION));
	private final String MESSAGE_START = "<red>Vous avez récupéré la patate chaude d'un adversaire !";
	private final String MESSAGE_END = "<aqua>Jetez-la pour infliger un sort similaire aux ennemis";
	private final String THIEF_MESSAGE = MESSAGE_START + "<br><gold>La patate vous inflige des dégâts<br>"
			+ MESSAGE_END;
	private final String GUARD_MESSAGE = MESSAGE_START + "<br><gold>La patate vous taze<br>" + MESSAGE_END;

	public HotPotatoHandler() {
		super(HotPotato::new);
	}

	//

	@Override
	protected void onAttachedToPlayer(HotPotato tool) {
		var prev_owner = tool.owner;
		tool.owner = tool.getOwner();

		var wrappingModule = Ioc.resolve(WrappingModule.class);
		var wrapO = wrappingModule.getWrapperOptional(tool.getOwner(), PlayerWrapper.class);
		if (wrapO.isEmpty()) {
			return;
		}
		var wrap = wrapO.get();
		if (prev_owner == null) {
			tool.team = wrap.getTeam();
			return;
		}
		if (wrap.getTeam() != tool.team) {
			tool.team = wrap.getTeam();
			if (tool.team == Vi6Team.THIEF) {
				tool.getOwner().damage(DAMAGE, DamageSource.builder(DamageType.IN_FIRE).withDirectEntity(prev_owner)
						.withCausingEntity(prev_owner).build());
				tool.getOwner().setNoDamageTicks(0);
				tool.getOwner().sendMessage(MiniMessage.miniMessage().deserialize(THIEF_MESSAGE));
			} else if (tool.team == Vi6Team.GUARD) {
				Ioc.resolve(StatusEffectModule.class).addEffect(tool.getOwner(), TAZ_EFFECT);
				tool.getOwner().sendMessage(MiniMessage.miniMessage().deserialize(GUARD_MESSAGE));
			}
		}
	}

	@Override
	protected void onDetachFromPlayer(HotPotato tool) {
	}

	@Override
	protected void onToolCleanup(HotPotato tool) {
	}

	@EventHandler
	private void onItemDrop(PlayerDropItemEvent evt) {
		var tool = getToolFromItem(evt.getItemDrop().getItemStack());
		if (tool != null) {
			evt.getItemDrop().setVelocity(evt.getItemDrop().getVelocity().multiply(VELOCITY_MULTIPLIER));
			evt.getItemDrop().setPickupDelay(20);
			// evt.getItemDrop().setPickupDelay(PICKUP_DELAY_TICKS);
		}
	}

	@EventHandler
	private void onEmpStart(EntityEmpStartEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				item.setData(DataComponentTypes.ITEM_MODEL, Material.POTATO.key());
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

	public static class HotPotato extends Tool {
		private Player owner;
		private Vi6Team team = Vi6Team.SPECTATOR;
		public HotPotato(ToolHandler<?> handler) {
			super(handler);
		}
	}
}
