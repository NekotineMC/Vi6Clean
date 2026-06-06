package fr.nekotine.vi6clean.impl.tool.personal.murmur;

import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.status.effect.StatusEffect;
import fr.nekotine.core.status.effect.StatusEffectModule;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.impl.status.effect.MurmurStatusEffectType;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import net.kyori.adventure.text.minimessage.MiniMessage;

@ToolCode("murmur")
public class MurmurHandler extends ToolHandler<MurmurHandler.Murmur> {

	private final double DURATION = getConfiguration().getDouble("duration", 10.0);
	private final int DURATION_TICKS = (int) (20 * DURATION);
	private final StatusEffect EFFECT = new StatusEffect(MurmurStatusEffectType.get(), DURATION_TICKS);
	private final String MESSAGE = "<red>Vous êtes anormalement <b>essouflé</b><br>"
			+ "Votre <b>stamina</b> et votre <b>air</b> ne peuvent plus remonter pendant <aqua>" + DURATION
			+ "</aqua> secondes</red>";

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
		var tool = getToolFromItem(evt.getItem());
		if (tool == null) {
			return;
		}
		if (EventUtil.isCustomAction(evt, CustomAction.HIT_ANY)) {
			evt.setCancelled(true);
			var optWrapper = Ioc.resolve(WrappingModule.class).getWrapperOptional(evt.getPlayer(), PlayerWrapper.class);
			if (optWrapper.isPresent()) {
				var statusEffectModule = Ioc.resolve(StatusEffectModule.class);
				optWrapper.get().enemyTeamInMap().forEach(p -> {
					p.sendMessage(MiniMessage.miniMessage().deserialize(MESSAGE));
					statusEffectModule.addEffect(p, EFFECT);
				});
			}
			remove(tool);
		}
	}

	public static class Murmur extends Tool {
		public Murmur(ToolHandler<?> handler) {
			super(handler);
		}
	}
}
