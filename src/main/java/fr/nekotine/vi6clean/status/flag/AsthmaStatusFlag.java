package fr.nekotine.vi6clean.status.flag;

import java.util.HashMap;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.status.flag.StatusFlag;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.configuration.ConfigManager;
import fr.nekotine.vi6clean.status.event.EntityMurmurEndEvent;
import fr.nekotine.vi6clean.status.event.EntityMurmurStartEvent;
import fr.nekotine.vi6clean.wrapper.InMapPhasePlayerWrapper;
import io.papermc.paper.util.Tick;

public class AsthmaStatusFlag implements StatusFlag, Listener {
	private class AsthmaInfo {
		private MovementMode mode;
		private int consume_tick_count = 0;
		private int idle_tick_count;
		private boolean isMurmuring;

		public AsthmaInfo(MovementMode mode, int idle_tick_count, boolean isMurmuring) {
			this.mode = mode;
			this.idle_tick_count = idle_tick_count;
			this.isMurmuring = isMurmuring;
		}
	}

	public static enum MovementMode {
		SPRINTING, WALKING, IDLE
	}

	private ConfigManager configuration = Ioc.resolve(ConfigManager.class);

	private static AsthmaStatusFlag instance;

	public static final AsthmaStatusFlag get() {
		if (instance == null) {
			instance = new AsthmaStatusFlag();
		}
		return instance;
	}

	private AsthmaStatusFlag() {
		EventUtil.register(this);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
	}

	// mode, tick_count_for_consume/regeneration, tick_count_for_consider_idle
	private HashMap<Player, AsthmaInfo> patients = new HashMap<Player, AsthmaInfo>();

	//

	@Override
	public void applyStatus(LivingEntity appliedTo) {
		if (appliedTo instanceof Player player) {
			var info = new AsthmaInfo(player.isSprinting() ? MovementMode.SPRINTING : MovementMode.WALKING,
					Tick.tick().fromDuration(configuration.getConfig().game().mechanics().asthma().idleDelay()),
					Ioc.resolve(StatusFlagModule.class).hasAny(appliedTo, MurmurStatusFlag.get()));

			patients.put(player, info);
			updateActionBarMode(player);
		}
	}

	@Override
	public void removeStatus(LivingEntity appliedTo) {
		patients.remove(appliedTo);
	}

	public void capture(Player player) {
		var level = Math.min(configuration.getConfig().game().mechanics().asthma().maxFoodAfterCapture(),
				player.getFoodLevel());
		player.setFoodLevel(level);
	}

	//

	private void updateActionBarMode(Player player) {
		if (!patients.containsKey(player)) {
			return;
		}
		var wrapper = Ioc.resolve(WrappingModule.class).getWrapperOptional(player, InMapPhasePlayerWrapper.class);
		if (wrapper.isEmpty()) {
			return;
		}
		var info = patients.get(player);
		wrapper.get().updateStaminaComponent(info.mode, info.isMurmuring);;
	}

	//

	@EventHandler
	private void onFoodChange(FoodLevelChangeEvent evt) {
		if (patients.containsKey(evt.getEntity())) {
			evt.setCancelled(true);
		}
	}

	@EventHandler
	private void onPlayerToggleSprint(PlayerToggleSprintEvent evt) {
		var player = evt.getPlayer();
		if (!patients.containsKey(player))
			return;
		var info = patients.get(player);

		info.mode = evt.isSprinting() ? MovementMode.SPRINTING : MovementMode.WALKING;
		info.idle_tick_count = 0;
		updateActionBarMode(player);
	}

	@EventHandler
	private void onPlayerMove(PlayerMoveEvent evt) {
		if (!evt.hasExplicitlyChangedPosition())
			return;
		var player = evt.getPlayer();
		if (!patients.containsKey(player))
			return;
		var info = patients.get(player);

		if (info.mode == MovementMode.IDLE) {
			info.mode = MovementMode.WALKING;
			updateActionBarMode(player);
		}

		info.idle_tick_count = 0;
	}

	@EventHandler
	private void onMurmurStart(EntityMurmurStartEvent evt) {
		var ent = evt.getEntity();
		if (!(ent instanceof Player player)) {
			return;
		}
		if (patients.containsKey(player)) {
			var info = patients.get(player);
			info.isMurmuring = true;
			updateActionBarMode(player);
		}
	}

	@EventHandler
	private void onMurmurEnd(EntityMurmurEndEvent evt) {
		var ent = evt.getEntity();
		if (!(ent instanceof Player player)) {
			return;
		}
		if (patients.containsKey(player)) {
			var info = patients.get(player);
			info.isMurmuring = false;
			updateActionBarMode(player);
		}
	}

	@EventHandler
	private void onTick(TickElapsedEvent evt) {

		// Récupération des configs ici pour pouvoir les changer en runtime
		var asthmaConfig = configuration.getConfig().game().mechanics().asthma();
		var sprintUsageTick = Tick.tick().fromDuration(asthmaConfig.sprintConsumptionDelay());
		var walkUsageTick = Tick.tick().fromDuration(asthmaConfig.walkRegenerationDelay());
		var idleUsageTick = Tick.tick().fromDuration(asthmaConfig.idleRegenerationDelay());
		var idleDelayTick = Tick.tick().fromDuration(asthmaConfig.idleDelay());

		for (var entry : patients.entrySet()) {
			var player = entry.getKey();
			var info = entry.getValue();

			if (info.mode != MovementMode.IDLE && ++info.idle_tick_count >= idleDelayTick) {
				info.mode = MovementMode.IDLE;
				updateActionBarMode(player);
			}

			info.consume_tick_count++;

			switch (info.mode) {
				case SPRINTING :
					if (info.consume_tick_count >= sprintUsageTick) {
						player.setFoodLevel(player.getFoodLevel() - 1);
						info.consume_tick_count = 0;
					}
					break;
				case WALKING :
					if (info.consume_tick_count >= walkUsageTick) {
						if (!info.isMurmuring) {
							player.setFoodLevel(Math.min(20, player.getFoodLevel() + 1));
						}
						info.consume_tick_count = 0;
					}
					break;
				default :
					if (info.consume_tick_count >= idleUsageTick) {
						if (!info.isMurmuring) {
							player.setFoodLevel(Math.min(20, player.getFoodLevel() + 1));
						}
						info.consume_tick_count = 0;
					}
					break;
			}
		}
	}
}
