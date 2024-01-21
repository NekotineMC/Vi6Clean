package fr.nekotine.vi6clean.impl.status.flag;

import java.util.HashMap;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;

import fr.nekotine.core.status.flag.StatusFlag;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.tuple.Triplet;

public class AsthmaStatusFlag implements StatusFlag,Listener{
	private static enum MovementMode {
		SPRINTING,
		WALKING,
		IDLE
	}
	private static int HALF_DRUMSTICK_CONSUME_TICK = 1 * 20;
	private static int HALF_DRUMSTICK_MOVING_REGEN_TICK = 2 * HALF_DRUMSTICK_CONSUME_TICK;
	private static int IDLE_REGEN_MULTIPLIER = 2;
	private static int MAX_HALF_DRUMSTICK_AFTER_CAPTURE = 10;
	private static int TICK_BEFORE_CONSIDER_IDLE = 5;
	private static AsthmaStatusFlag instance;
	public static final AsthmaStatusFlag get() {
		if (instance == null) {
			instance = new AsthmaStatusFlag();
		}
		return instance;
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
	
	//
	
	
	
	//
	
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
