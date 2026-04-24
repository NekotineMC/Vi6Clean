package fr.nekotine.vi6clean.impl.tool.personal.cactus;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6Team;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;

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
		var wrappingModule = Ioc.resolve(WrappingModule.class);
		var wrapO = wrappingModule.getWrapperOptional(victim, PlayerWrapper.class);
		if (wrapO.isEmpty()) {
			return; // Victim has no team
		}
		var wrap = wrapO.get();
		for (var tool : getTools()) {
			var owner = tool.getOwner();
			if (!evt.getDamager().equals(owner)) {
				continue;
			}
			if (wrap.getTeam() == Vi6Team.THIEF) {
				for (var thief : Ioc.resolve(Vi6Game.class).getThiefs()) {
					if (!victim.equals(thief) && !owner.equals(thief)) {
						thief.damage(DAMAGE, owner);
					}
				}
			} else if (wrap.getTeam() == Vi6Team.GUARD) {
				// Probablement innutile, mais on sait jamais
				for (var guard : Ioc.resolve(Vi6Game.class).getGuards()) {
					if (!victim.equals(guard) && !owner.equals(guard)) {
						guard.damage(DAMAGE, owner);
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

	public static class Cactus extends Tool {

		public Cactus(ToolHandler<?> handler) {
			super(handler);
		}

	}
}
