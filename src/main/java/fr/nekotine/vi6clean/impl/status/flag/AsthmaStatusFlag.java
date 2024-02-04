package fr.nekotine.vi6clean.impl.status.flag;

import java.util.HashMap;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.plugin.java.JavaPlugin;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.status.flag.StatusFlag;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.impl.wrapper.InMapPhasePlayerWrapper;

public class AsthmaStatusFlag implements StatusFlag,Listener{
	private class AsthmaInfo {
		private MovementMode mode;
		private double consume_tick_count;
		private int idle_tick_count;
		private boolean cancelledFlying;
		public AsthmaInfo(MovementMode mode, int consume_tick_count, int idle_tick_count) {
			this.setMode(mode);
			this.setConsumeTickCount(consume_tick_count);
			this.setIdleTickCount(idle_tick_count);
			this.setCancelledFlying(false);
		}
		public MovementMode getMode() {
			return mode;
		}
		public void setMode(MovementMode mode) {
			this.mode = mode;
		}
		public double getConsumeTickCount() {
			return consume_tick_count;
		}
		public void setConsumeTickCount(double consume_tick_count) {
			this.consume_tick_count = consume_tick_count;
		}
		public int getIdleTickCount() {
			return idle_tick_count;
		}
		public void setIdleTickCount(int idle_tick_count) {
			this.idle_tick_count = idle_tick_count;
		}
		public boolean isCancelledFlying() {
			return cancelledFlying;
		}
		public void setCancelledFlying(boolean cancelledFlying) {
			this.cancelledFlying = cancelledFlying;
		}
	}
	public static enum MovementMode {
		SPRINTING,
		WALKING,
		IDLE
	}
	private int HALF_DRUMSTICK_CONSUME_TICK = (int)(20*Ioc.resolve(JavaPlugin.class).getConfig().getDouble("half_drumstick_consumption_delay", 1));
	private int HALF_DRUMSTICK_MOVING_REGEN_TICK = (int)(20*Ioc.resolve(JavaPlugin.class).getConfig().getDouble("half_drumsitck_moving_regeneration_delay", 2));
	private double IDLE_REGEN_MULTIPLIER = Ioc.resolve(JavaPlugin.class).getConfig().getDouble("idle_regeneration_multiplier", 2);
	private int TICK_BEFORE_CONSIDER_IDLE = (int)(20*Ioc.resolve(JavaPlugin.class).getConfig().getDouble("delay_before_considering_idle", 0.25));
	private int MAX_HALF_DRUMSTICK_AFTER_CAPTURE = Ioc.resolve(JavaPlugin.class).getConfig().getInt("max_half_drumstick_after_capture", 10);
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
	
	//mode, tick_count_for_consume/regeneration, tick_count_for_consider_idle
	private HashMap<Player, AsthmaInfo> patients = new HashMap<Player, AsthmaInfo>();
	
	//
	
	@Override
	public void applyStatus(LivingEntity appliedTo) {
		if(appliedTo instanceof Player player) {
			var info = new AsthmaInfo(
				player.isSprinting() ? MovementMode.SPRINTING : MovementMode.WALKING, 
				HALF_DRUMSTICK_MOVING_REGEN_TICK, 
				TICK_BEFORE_CONSIDER_IDLE);
					
			patients.put(player, info);
			updateActionBarMode(player, info.getMode());
		}
	}
	@Override
	public void removeStatus(LivingEntity appliedTo) {
		patients.remove(appliedTo);
	}
	public void capture(Player player) {
		if(!patients.containsKey(player)) return;
		var info = patients.get(player);
		var level = Math.min(MAX_HALF_DRUMSTICK_AFTER_CAPTURE, player.getFoodLevel());
		player.setFoodLevel(level);
		if(info.getMode() != MovementMode.SPRINTING) {
			info.setConsumeTickCount(HALF_DRUMSTICK_MOVING_REGEN_TICK);
		}
		
	}
	
	//

	private boolean updateFlying(Player player, boolean cancelledFlying) {
		if(cancelledFlying && player.getFoodLevel() > 6) {
			player.setAllowFlight(true);
			return false;
		}
		if(player.getFoodLevel() <= 6 && player.getAllowFlight()) {
			player.setAllowFlight(false);
			return true;
		}
		return cancelledFlying;
	}
	private void updateActionBarMode(Player player, MovementMode mode) {
		InMapPhasePlayerWrapper wrapper = Ioc.resolve(WrappingModule.class).getWrapper(player, InMapPhasePlayerWrapper.class);
		if(wrapper!=null) {
			wrapper.updateStaminaComponent(mode);
		}
	}
	
	//

	@EventHandler
	private void onFoodChange(FoodLevelChangeEvent evt) {
		if(patients.containsKey(evt.getEntity())) {
			evt.setCancelled(true);
		}
	}
	@EventHandler
	private void onPlayerToggleSprint(PlayerToggleSprintEvent evt) {
		var player = evt.getPlayer();
		if(!patients.containsKey(player)) return;
		var info = patients.get(player);
		
		info.setMode(evt.isSprinting() ? MovementMode.SPRINTING : MovementMode.WALKING);
		info.setConsumeTickCount(evt.isSprinting() ? HALF_DRUMSTICK_CONSUME_TICK : HALF_DRUMSTICK_MOVING_REGEN_TICK);
		info.setIdleTickCount(TICK_BEFORE_CONSIDER_IDLE);
		updateActionBarMode(player, info.getMode());
	}
	@EventHandler
	private void onPlayerMove(PlayerMoveEvent evt) {
		if(!evt.hasExplicitlyChangedPosition()) return;
		var player = evt.getPlayer();
		if(!patients.containsKey(player)) return;
		var info = patients.get(player);
		
		if(info.getMode() == MovementMode.IDLE) {
			info.setMode(MovementMode.WALKING);
			updateActionBarMode(player, info.getMode());
		}
		
		info.setIdleTickCount(TICK_BEFORE_CONSIDER_IDLE);
	}
	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		for(var entry : patients.entrySet()) {
			var player = entry.getKey();	
			var info = entry.getValue();
			var mode = info.getMode();
			var usage_tick = info.getConsumeTickCount();
			var idle_tick = info.getIdleTickCount();
			
			switch(mode) {
			case SPRINTING:
				if(--usage_tick==0) {
					player.setFoodLevel(player.getFoodLevel() - 1);
					usage_tick = HALF_DRUMSTICK_CONSUME_TICK;
				}
				break;
			case WALKING:
				if(--usage_tick==0) {
					player.setFoodLevel(Math.min(20, player.getFoodLevel() + 1));
					usage_tick = HALF_DRUMSTICK_MOVING_REGEN_TICK;
				}
				if(--idle_tick==0) {
					mode = MovementMode.IDLE;
					updateActionBarMode(player, mode);
				}
				break;
			default:
				usage_tick = usage_tick - IDLE_REGEN_MULTIPLIER;
				if(usage_tick<=0) {
					player.setFoodLevel(Math.min(20, player.getFoodLevel() + 1));
					usage_tick = HALF_DRUMSTICK_MOVING_REGEN_TICK;
				}
				break;
			}
			
			var cancelledFlying = updateFlying(player, info.isCancelledFlying());
			info.setIdleTickCount(idle_tick);
			info.setConsumeTickCount(usage_tick);
			info.setMode(mode);
			info.setCancelledFlying(cancelledFlying);
		}
	}
}
