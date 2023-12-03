package fr.nekotine.vi6clean.impl.map;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.BoundingBox;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.map.annotation.ComposingMap;
import fr.nekotine.core.map.annotation.MapDictKey;
import fr.nekotine.core.map.element.MapBoundingBoxElement;
import fr.nekotine.core.map.element.MapLocationElement;
import fr.nekotine.core.text.TextModule;
import fr.nekotine.core.text.TextModule.Builder;
import fr.nekotine.core.text.placeholder.TextPlaceholder;
import fr.nekotine.core.text.style.NekotineStyles;
import fr.nekotine.core.text.tree.Leaf;
import fr.nekotine.core.tuple.Pair;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6Team;
import fr.nekotine.vi6clean.impl.wrapper.InMapPhasePlayerWrapper;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;

public class LightKoth implements TextPlaceholder{
	@MapDictKey
	@ComposingMap
	private String name = "";
	@ComposingMap
	private MapBoundingBoxElement boundingBox = new MapBoundingBoxElement();
	@ComposingMap
	private MapLocationElement displayLocation = new MapLocationElement();
	private Set<Player> inside = new HashSet<>(8);
	
	private static final int CAPTURE_AMOUNT_NEEDED = 200;
	private int capture_advancement;
	private Vi6Team owningTeam = Vi6Team.GUARD;
	private TextDisplay display;

	//
	
	public BoundingBox getBoundingBox() {
		return boundingBox.get();
	}
	public Vi6Team getOwningTeam() {
		return owningTeam;
	}
	public int getCaptureAdvancement() {
		return capture_advancement;
	}
	public int getCaptureAmountNeeded() {
		return CAPTURE_AMOUNT_NEEDED;
	}
	public Set<Player> getInsideCaptureZone(){
		return inside;
	}
	
	//
	
	public void clean() {
		capture_advancement = 0;
		display.remove();
	}
	public void setup(World world) {
		display = (TextDisplay)world.spawnEntity(displayLocation.toLocation(world), EntityType.TEXT_DISPLAY);
	}
	public void tick() {
		var wrapping = Ioc.resolve(WrappingModule.class);
		int tickAdvancement = 0;
		boolean owningTeamCancelling = false;
		Player firstEnemy = null;
		for (var player : inside) {
			var optWrapper = wrapping.getWrapperOptional(player, InMapPhasePlayerWrapper.class);
			if (optWrapper.isEmpty())
				continue;
	
			if (optWrapper.get().getParentWrapper().getTeam() == owningTeam) {
				owningTeamCancelling = true;
			}else {
				firstEnemy = player;
				tickAdvancement++;
			}
		}
		if (owningTeamCancelling) 
			return;
		if (tickAdvancement == 0)
			tickAdvancement--;
		capture_advancement += tickAdvancement;
		if (capture_advancement < 0) {
			capture_advancement = 0;
			return;
		}
		if (capture_advancement >= CAPTURE_AMOUNT_NEEDED) {
			var newOwning = Ioc.resolve(WrappingModule.class).
					getWrapperOptional(firstEnemy, PlayerWrapper.class).get().getTeam();
			capture(newOwning, owningTeam);
			owningTeam = newOwning;
			capture_advancement = 0;
		}
		display.text(display());
	}
	
	//
	
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
	public void capture(Vi6Team winningTeam, Vi6Team losingTeam) {
		System.out.println("CAPTURE");
	}
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
