package fr.nekotine.vi6clean.impl.game.phase;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import fr.nekotine.core.game.Game;
import fr.nekotine.core.game.GamePhase;
import fr.nekotine.core.game.GameTeam;
import fr.nekotine.core.util.EntityUtil;
import fr.nekotine.vi6clean.impl.game.GD_Vi6;
import fr.nekotine.vi6clean.impl.game.GM_Vi6;

public class PHASE_Vi6_Infiltration extends GamePhase<GD_Vi6, GM_Vi6>{

	private final PotionEffect playerSaturationEffect;
	
	private final PotionEffect thiefNightVisionEffect;
	
	public PHASE_Vi6_Infiltration(GM_Vi6 gamemode) {
		super(gamemode);
		playerSaturationEffect = new PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 0, false, false, false);
		thiefNightVisionEffect = new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false, false);
	}

	@Override
	public void globalBegin(Game<GD_Vi6> game) {
		// Enable objects
	}

	@Override
	public void globalEnd(Game<GD_Vi6> game) {
		// Disable objects
	}

	@Override
	public void playerBegin(Game<GD_Vi6> game, Player player, GameTeam team) {
		player.getInventory().clear();
		EntityUtil.clearPotionEffects(player);
		EntityUtil.defaultAllAttributes(player);
		player.addPotionEffect(playerSaturationEffect);
		// TODO Add player objects
		// TODO Teleport to spawn point
		if (team == game.getGameData().getThiefTeam()){
			// Voleur
			player.addPotionEffect(thiefNightVisionEffect);
		}else {
			// Garde
		}
	}

	@Override
	public void playerEnd(Game<GD_Vi6> game, Player player, GameTeam team) {
	}

}
