package fr.nekotine.vi6clean.impl.map.koth;

import java.util.List;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.text.TextModule;
import fr.nekotine.core.text.TextModule.Builder;
import fr.nekotine.core.text.placeholder.TextPlaceholder;
import fr.nekotine.core.text.style.NekotineStyles;
import fr.nekotine.core.text.tree.Leaf;
import fr.nekotine.core.tuple.Pair;
import fr.nekotine.vi6clean.constant.Vi6Team;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.status.flag.DarkenedStatusFlag;

public class LightKothEffect implements KothEffect, TextPlaceholder{
	private static final int AMOUNT_FOR_OTHER_CAPTURE = 200;
	private static final int AMOUNT_FOR_GUARD_CAPTURE = 400;
	private Koth koth;
	
	//
	
	@Override
	public void tick() {
		koth.setText(textDisplay.buildFirst());
	}
	@Override
	public void capture(Vi6Team owning, Vi6Team losing) {
		if(losing==Vi6Team.GUARD) {
			var flagModule = Ioc.resolve(StatusFlagModule.class);
			Ioc.resolve(Vi6Game.class).getGuards().forEach(p -> flagModule.addFlag(p, DarkenedStatusFlag.get()));
			koth.setCaptureAmountNeeded(AMOUNT_FOR_GUARD_CAPTURE);
		}else if(owning==Vi6Team.GUARD) {
			var flagModule = Ioc.resolve(StatusFlagModule.class);
			Ioc.resolve(Vi6Game.class).getGuards().forEach(p -> flagModule.removeFlag(p, DarkenedStatusFlag.get()));
			koth.setCaptureAmountNeeded(AMOUNT_FOR_OTHER_CAPTURE);
		}
	}
	@Override
	public void setup(Koth koth) {
		Ioc.resolve(ModuleManager.class).tryLoad(StatusFlagModule.class);
		this.koth = koth;
		koth.setCaptureAmountNeeded(AMOUNT_FOR_OTHER_CAPTURE);
	}
	@Override
	public void clean() {
		if(koth.getOwningTeam() == Vi6Team.GUARD) 
			return;
		var flagModule = Ioc.resolve(StatusFlagModule.class);
		Ioc.resolve(Vi6Game.class).getGuards().forEach(p -> flagModule.removeFlag(p, DarkenedStatusFlag.get()));
	}

	//
	
	private final Builder textDisplay = Ioc.resolve(TextModule.class).message(Leaf.builder()
			.addStyle(NekotineStyles.STANDART)
			.addLine("<yellow><u>Générateur</u></yellow>\n"
					+"<aqua><power></aqua> <evolution>\n"
					+"<status>")
			.addPlaceholder(this));
	@Override
	public List<Pair<String, String>> resolve() {
		var owningTeam = koth.getOwningTeam();
		var tickAdvancement = koth.getTickAdvancement();
		var percentage = (int)(((float)koth.getCaptureAdvancement() / koth.getCaptureAmountNeeded()) * 100);
		var status = owningTeam == Vi6Team.GUARD ? "<green>Actif</green>" : "<red>Désactivé</red>";
		var power = owningTeam == Vi6Team.GUARD ? (100 - percentage)+"%" : percentage+"%";
		if(owningTeam == Vi6Team.GUARD)
			tickAdvancement = -tickAdvancement;
		var evolution = tickAdvancement == 0 ? "-" : (tickAdvancement > 0 ? "<green>↑</green>" : "<red>↓</red>");

		return List.of(
				Pair.from("status", status),
				Pair.from("power", power),
				Pair.from("evolution", evolution));
	}
}
