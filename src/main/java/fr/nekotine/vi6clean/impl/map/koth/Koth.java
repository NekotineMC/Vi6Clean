package fr.nekotine.vi6clean.impl.map.koth;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.BoundingBox;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.map.annotation.ComposingMap;
import fr.nekotine.core.map.annotation.MapDictKey;
import fr.nekotine.core.map.element.MapBoundingBoxElement;
import fr.nekotine.core.text.TextModule;
import fr.nekotine.core.text.TextModule.Builder;
import fr.nekotine.core.text.placeholder.TextPlaceholder;
import fr.nekotine.core.text.style.NekotineStyles;
import fr.nekotine.core.text.tree.Leaf;
import fr.nekotine.core.tuple.Pair;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6Team;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.wrapper.InMapPhasePlayerWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;

public abstract class Koth implements TextPlaceholder{
	private static final int CAPTURE_AMOUNT_NEEDED = 200;
	private int capture_advancement;
	@MapDictKey
	@ComposingMap
	private String name = "";
	private Set<Player> inside = new HashSet<>(8);
	private Vi6Team owningTeam = Vi6Team.GUARD;
	@ComposingMap
	private MapBoundingBoxElement boundingBox = new MapBoundingBoxElement();
	private TextDisplay display;
	//Summon le display à la création de la game
	
	//
	
	private boolean isInOwningTeam(Player player) {
		var game = Ioc.resolve(Vi6Game.class);
		return owningTeam == Vi6Team.GUARD ? 
				game.getGuards().contains(player) :
				game.getThiefs().contains(player);
	}
	private boolean isInEnemyTeam(Player player) {
		var game = Ioc.resolve(Vi6Game.class);
		return owningTeam == Vi6Team.GUARD ? 
				game.getThiefs().contains(player) :
				game.getGuards().contains(player);
	}
	private Vi6Team getEnemyTeam(Vi6Team team) {
		switch(team) {
		case GUARD: return Vi6Team.THIEF;
		case THIEF: return Vi6Team.GUARD;
		default: return team;
		}
	}
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
	
	//
	
	public BoundingBox getBoundingBox() {
		return boundingBox.get();
	}
	public String getName() {
		return name;
	}
	public void clean() {
		capture_advancement = 0;
		display.remove();
	}
	public Vi6Team getOwningTeam() {
		return owningTeam;
	}
	public Set<Player> getInsideCaptureZone(){
		return inside;
	}
	public void tick() {
		var wrapping = Ioc.resolve(WrappingModule.class);
		int tickAdvancement = 0;
		boolean owningTeamCancelling = false;
		for (var player : inside) {
			if (isInOwningTeam(player)) {
				owningTeamCancelling = true;
			}else if(isInEnemyTeam(player)) {
				var optWrapper = wrapping.getWrapperOptional(player, InMapPhasePlayerWrapper.class);
				if (optWrapper.isEmpty())
					continue;
				tickAdvancement++;
			}
		}
		if (owningTeamCancelling) 
			return;
		if (tickAdvancement == 0)
			tickAdvancement--;
		capture_advancement += tickAdvancement;
		if (capture_advancement < 0) 
			capture_advancement = 0;
		if (capture_advancement >= CAPTURE_AMOUNT_NEEDED) {
			owningTeam = getEnemyTeam(owningTeam);
			capture_advancement = 0;
			//do smth
		}
		var text = owningTeam==Vi6Team.GUARD ? activeDisplay.buildFirst() : inactiveDisplay.buildFirst();
		display.text(text);
	}
	
	//
	
	public abstract void capture(Vi6Team owningTeam);
	
	//
	
	@Override
	public ArrayList<Pair<String, ComponentLike>> resolve() {
		var list = new ArrayList<Pair<String,ComponentLike>>();
		var percentage = (int)((capture_advancement / CAPTURE_AMOUNT_NEEDED) * 100);
		var inv_percentage = 100 - percentage;
		list.add(Pair.from("power", Component.text(percentage+"%")));
		list.add(Pair.from("inv_power", Component.text(inv_percentage+"%")));
		return list;
	}
}
