package fr.nekotine.vi6clean.impl.map.koth;

import java.util.List;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.status.effect.StatusEffect;
import fr.nekotine.core.status.effect.StatusEffectModule;
import fr.nekotine.core.text.TextModule;
import fr.nekotine.core.text.TextModule.Builder;
import fr.nekotine.core.text.placeholder.TextPlaceholder;
import fr.nekotine.core.text.style.NekotineStyles;
import fr.nekotine.core.text.tree.Leaf;
import fr.nekotine.core.tuple.Pair;
import fr.nekotine.vi6clean.constant.Vi6Team;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.status.effect.EmpStatusEffectType;

public class EmpKothEffect implements KothEffect,TextPlaceholder{
	private static final int AMOUNT_FOR_OTHER_CAPTURE = 200;
	private static final int AMOUNT_FOR_GUARD_CAPTURE = 400;
	private static final StatusEffect effect = new StatusEffect(EmpStatusEffectType.get(), -1);
	
	//
	
	private Koth koth;
	@Override
	public void tick() {
		koth.setText(textDisplay.buildFirst());
	}
	@Override
	public void capture(Vi6Team owning, Vi6Team losing) {
		var statusEffectModule = Ioc.resolve(StatusEffectModule.class);
		var game = Ioc.resolve(Vi6Game.class);
		if(losing==Vi6Team.GUARD) {
			game.getGuards().forEach(
					p -> statusEffectModule.addEffect(p, effect));
			koth.setCaptureAmountNeeded(AMOUNT_FOR_GUARD_CAPTURE);
		}else if(owning==Vi6Team.GUARD) {
			game.getGuards().forEach(
					p -> statusEffectModule.removeEffect(p, effect));
			koth.setCaptureAmountNeeded(AMOUNT_FOR_OTHER_CAPTURE);
		}
	}
	@Override
	public void setup(Koth koth) {
		Ioc.resolve(ModuleManager.class).tryLoad(StatusEffectModule.class);
		this.koth = koth;
		koth.setCaptureAmountNeeded(AMOUNT_FOR_OTHER_CAPTURE);
	}
	@Override
	public void clean() {
		if(koth.getOwningTeam() == Vi6Team.GUARD) 
			return;
		var statusEffectModule = Ioc.resolve(StatusEffectModule.class);
		Ioc.resolve(Vi6Game.class).getGuards().forEach(
				p -> statusEffectModule.removeEffect(p, effect));
	}
	
	//
	
	private final Builder textDisplay = Ioc.resolve(TextModule.class).message(Leaf.builder()
			.addStyle(NekotineStyles.STANDART)
			.addLine("<dark_purple><u>Brouilleur</u></dark_purple>\n"
					+"<aqua><power></aqua> <evolution>\n"
					+"<status>")
			.addPlaceholder(this));
	@Override
	public List<Pair<String, String>> resolve() {
		var owningTeam = koth.getOwningTeam();
		var tickAdvancement = koth.getTickAdvancement();
		var percentage = (int)(((float)koth.getCaptureAdvancement() / koth.getCaptureAmountNeeded()) * 100);
		var status = (owningTeam == Vi6Team.GUARD) ? "<red>Désactivé</red>" : "<green>Actif</green>";
		var power = (owningTeam == Vi6Team.GUARD) ? percentage+"%" : (100-percentage)+"%";
		if(owningTeam == Vi6Team.GUARD)
			tickAdvancement = -tickAdvancement;
		var evolution = tickAdvancement == 0 ? "-" : (tickAdvancement > 0 ? "<red>↓</red>" : "<green>↑</green>");

		return List.of(
				Pair.from("status", status),
				Pair.from("power", power),
				Pair.from("evolution", evolution));
	}
}
