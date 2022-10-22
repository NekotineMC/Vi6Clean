package fr.nekotine.vi6clean.def.game.phase;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import fr.nekotine.core.game.GamePhase;
import fr.nekotine.core.game.GameTeam;
import fr.nekotine.core.util.EntityUtil;
import fr.nekotine.vi6clean.def.game.GM_Vi6;

public class PHASE_Vi6_Preparation extends GamePhase<GM_Vi6>{

	private final PotionEffect playerSaturationEffect;
	
	private final PotionEffect thiefNightVisionEffect;
	
	public PHASE_Vi6_Preparation(GM_Vi6 game) {
		super(game);
		playerSaturationEffect = new PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 0, false, false, false);
		thiefNightVisionEffect = new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false, false);
	}

	@Override
	public void globalBegin() {
	}

	@Override
	public void globalEnd() {
	}

	@Override
	public void playerBegin(Player player, GameTeam team) {
		player.getInventory().clear();
		EntityUtil.clearPotionEffects(player);
		EntityUtil.defaultAllAttributes(player);
		player.addPotionEffect(playerSaturationEffect);
		if (team == getGame().getThiefTeam()){
			player.addPotionEffect(thiefNightVisionEffect);
		}
	}

	@Override
	public void playerEnd(Player player, GameTeam team) {
	}

}
