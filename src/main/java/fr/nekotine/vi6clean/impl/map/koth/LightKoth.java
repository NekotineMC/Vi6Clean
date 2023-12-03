package fr.nekotine.vi6clean.impl.map.koth;

import java.util.ArrayList;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.text.TextModule;
import fr.nekotine.core.text.TextModule.Builder;
import fr.nekotine.core.text.placeholder.TextPlaceholder;
import fr.nekotine.core.text.style.NekotineStyles;
import fr.nekotine.core.text.tree.Leaf;
import fr.nekotine.core.tuple.Pair;
import fr.nekotine.vi6clean.constant.Vi6Team;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;

public class LightKoth extends Koth implements TextPlaceholder{
	private final Builder activeDisplay = Ioc.resolve(TextModule.class).message(Leaf.builder()
			.addStyle(NekotineStyles.STANDART)
			.addLine("<yellow><u>Générateur</u></yellow>\n"
					+"<green>Actif</green>\n"
					+"<yellow><i>Puissance</i>: <aqua><inv_power></aqua>")
			.addPlaceholder(this));
	private final Builder inactiveDisplay = Ioc.resolve(TextModule.class).message(Leaf.builder()
			.addStyle(NekotineStyles.STANDART)
			.addLine("<yellow><u>Générateur</u></yellow>\n"
					+"<red>Désactivé</red>\n"
					+"<yellow><i>Puissance</i>: <aqua><power></aqua>")
			.addPlaceholder(this));

	@Override
	public void capture(Vi6Team winningTeam, Vi6Team losingTeam) {
		System.out.println("CAPTURE");
	}

	@Override
	public Component display() {
		return getOwningTeam()==Vi6Team.GUARD ? activeDisplay.buildFirst() : inactiveDisplay.buildFirst();
	}
	
	//
	
	@Override
	public ArrayList<Pair<String, ComponentLike>> resolve() {
		var list = new ArrayList<Pair<String,ComponentLike>>();
		var percentage = (int)((getCaptureAdvancement() / getCaptureAmountNeeded()) * 100);
		var inv_percentage = 100 - percentage;
		list.add(Pair.from("power", Component.text(percentage+"%")));
		list.add(Pair.from("inv_power", Component.text(inv_percentage+"%")));
		return list;
	}
}
