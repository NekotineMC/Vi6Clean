package fr.nekotine.vi6clean.impl.tool.personal.diarrhea;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.status.effect.StatusEffect;
import fr.nekotine.core.status.effect.StatusEffectModule;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.impl.status.effect.DiarrheaStatusEffectType;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import net.kyori.adventure.text.minimessage.MiniMessage;

@ToolCode("diarrhea")
public class DiarrheaHandler extends ToolHandler<DiarrheaHandler.Diarrhea> {
	private final double DURATION = getConfiguration().getDouble("duration", 10);
	private final int DURATION_TICKS = (int) (20 * DURATION);
	private final int COOLDOWN_TICKS = (int) (20 * getConfiguration().getDouble("cooldown", 10));
	private final StatusEffect EFFECT = new StatusEffect(DiarrheaStatusEffectType.get(), DURATION_TICKS);
	private final String MESSAGE = "<red>Le gouvernement contrôle votre corps grâce à la 5G<br>"
			+ "Les ondes déclenchent une <b>diarhée fulgurante</b> pendant <aqua>" + DURATION + "s</aqua>";

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
		for (var tool : getTools()) {
			if (!evt.getDamager().equals(tool.getOwner())) {
				continue;
			}
			var wrappingModule = Ioc.resolve(WrappingModule.class);
			var wrapO = wrappingModule.getWrapperOptional(tool.getOwner(), PlayerWrapper.class);
			if (wrapO.isEmpty()) {
				return;
			}
			if (wrapO.get().enemyTeamInMap().anyMatch(p -> p.equals(evt.getEntity()))) {
				var effectModule = Ioc.resolve(StatusEffectModule.class);
				effectModule.addEffect((Player) evt.getEntity(), EFFECT);
			}
		}
	}

	@EventHandler
	private void onPlayerInteract(PlayerInteractEvent evt) {
		var tool = getToolFromItem(evt.getItem());
		if (tool == null) {
			return;
		}
		if (tool.getOwner().hasCooldown(evt.getItem())) {
			return;
		}
		if (EventUtil.isCustomAction(evt, CustomAction.HIT_ANY)) {
			var wrappingModule = Ioc.resolve(WrappingModule.class);
			var wrapO = wrappingModule.getWrapperOptional(tool.getOwner(), PlayerWrapper.class);
			if (wrapO.isEmpty()) {
				return;
			}
			evt.setCancelled(true);
			var effectModule = Ioc.resolve(StatusEffectModule.class);
			wrapO.get().enemyTeamInMap().forEach(p -> {
				effectModule.addEffect(p, EFFECT);
				p.sendMessage(MiniMessage.miniMessage().deserialize(MESSAGE));
			});
			tool.getOwner().setCooldown(evt.getItem(), COOLDOWN_TICKS);
		}
	}

	public static class Diarrhea extends Tool {
		public Diarrhea(ToolHandler<?> handler) {
			super(handler);
		}
	}
}
