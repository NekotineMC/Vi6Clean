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
import fr.nekotine.core.tuple.Triplet;
import fr.nekotine.core.util.EventUtil;

public class AsthmaStatusFlag implements StatusFlag,Listener{
	private static enum MovementMode {
		SPRINTING,
		WALKING,
		IDLE
	}
	private int HALF_DRUMSTICK_CONSUME_TICK = (int)(20*Ioc.resolve(JavaPlugin.class).getConfig().getDouble("half_drumstick_consumption_delay", 1));
	private int HALF_DRUMSTICK_MOVING_REGEN_TICK = (int)(20*Ioc.resolve(JavaPlugin.class).getConfig().getDouble("half_drumsitck_moving_regeneration_delay", 2));
	private int IDLE_REGEN_MULTIPLIER = Ioc.resolve(JavaPlugin.class).getConfig().getInt("idle_regeneration_multiplier", 2);
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
	private HashMap<Player, Triplet<MovementMode,Integer,Integer>> patients = new HashMap<Player, Triplet<MovementMode,Integer,Integer>>();
	
	//
	
	@Override
	public void applyStatus(LivingEntity appliedTo) {
		if(appliedTo instanceof Player player) {
			patients.put(player, Triplet.from(
					MovementMode.IDLE, 
					HALF_DRUMSTICK_MOVING_REGEN_TICK, 
					TICK_BEFORE_CONSIDER_IDLE));
		}
	}
	@Override
	public void removeStatus(LivingEntity appliedTo) {
		patients.remove(appliedTo);
	}
	public void capture(Player player) {
		if(!patients.containsKey(player)) return;
		var level = Math.min(MAX_HALF_DRUMSTICK_AFTER_CAPTURE, player.getFoodLevel());
		player.setFoodLevel(level);
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
		patients.computeIfPresent(player, (p,t) -> Triplet.from(
				//On met à jour le mode de déplacement
				evt.isSprinting() ? MovementMode.SPRINTING : MovementMode.WALKING,
				//On met à jour le délai de récupération de la stamina
				evt.isSprinting() ? HALF_DRUMSTICK_CONSUME_TICK : HALF_DRUMSTICK_MOVING_REGEN_TICK,
				//Inchangé
				TICK_BEFORE_CONSIDER_IDLE));
	}
	@EventHandler
	private void onPlayerMove(PlayerMoveEvent evt) {
		if(!evt.hasChangedPosition()) return;
		patients.computeIfPresent(evt.getPlayer(), (p,t) ->Triplet.from(
				//Si le joueur court déjà, on le laisse, sinon on le met en marche
				t.a() == MovementMode.SPRINTING ? MovementMode.SPRINTING : MovementMode.WALKING, 
				//L'avancement de la régénération est laissée identique
				t.b(), 
				//On s'est déplacé donc on reset le timer idle
				TICK_BEFORE_CONSIDER_IDLE));
	}
	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		var iterator = patients.entrySet().iterator();
		while(iterator.hasNext()) {
			var entry = iterator.next();
			var player = entry.getKey();
			var triplet = entry.getValue();
			var mode = triplet.a();
			var usage_tick = triplet.b();
			var idle_tick = triplet.c();
			
			switch(mode) {
			case SPRINTING:
				if(--usage_tick==0) {
					player.setFoodLevel(player.getFoodLevel() - 1);
					usage_tick = HALF_DRUMSTICK_CONSUME_TICK;
				}
				break;
			case WALKING:
				if(--usage_tick==0) {
					var level = Math.min(20, player.getFoodLevel() + 1);
					player.setFoodLevel(level);
					usage_tick = HALF_DRUMSTICK_MOVING_REGEN_TICK;
				}
				if(--idle_tick==0) {
					mode = MovementMode.IDLE;
				}
				break;
			default:
				usage_tick = usage_tick - IDLE_REGEN_MULTIPLIER;
				if(usage_tick<=0) {
					var level = Math.min(20, player.getFoodLevel() + 1);
					player.setFoodLevel(level);
					usage_tick = HALF_DRUMSTICK_MOVING_REGEN_TICK;
				}
				break;
			}
			
			patients.replace(player, Triplet.from(mode, usage_tick, idle_tick));
		}
	}
}
