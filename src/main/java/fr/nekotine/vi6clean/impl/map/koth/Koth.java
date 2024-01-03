package fr.nekotine.vi6clean.impl.map.koth;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.BoundingBox;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.map.annotation.ComposingMap;
import fr.nekotine.core.map.annotation.MapDictKey;
import fr.nekotine.core.map.element.MapBoundingBoxElement;
import fr.nekotine.core.map.element.MapLocationElement;
import fr.nekotine.core.util.SpatialUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6Team;
import fr.nekotine.vi6clean.impl.wrapper.InMapPhasePlayerWrapper;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import net.kyori.adventure.text.Component;

public class Koth{
	@MapDictKey
	@ComposingMap
	private String name = "";
	@ComposingMap
	private MapBoundingBoxElement boundingBox = new MapBoundingBoxElement();
	@ComposingMap
	private MapLocationElement displayLocation = new MapLocationElement();
	private Set<Player> inside = new HashSet<>(8);
	
	private boolean isEnabled = false;
	private int captureAmountNeeded = 200;
	private static final int PARTICLE_DENSITY = 1;
	private Vi6Team owningTeam = Vi6Team.GUARD;
	private Component text = Component.text("");
	private int tickAdvancement;
	private TextDisplay display;
	private int captureAdvancement;
	private KothEffect effect;
	private LinkedList<Location> rectangle = new LinkedList<Location>();
	
	
	//
	
	public BoundingBox getBoundingBox() {
		return boundingBox.get();
	}
	public Vi6Team getOwningTeam() {
		return owningTeam;
	}
	public void setOwningTeam(Vi6Team team) {
		owningTeam = team;
	}
	public int getCaptureAdvancement() {
		return captureAdvancement;
	}
	public void setCaptureAdvancement(int advancement) {
		captureAdvancement = advancement;
	}
	public int getCaptureAmountNeeded() {
		return captureAmountNeeded;
	}
	public void setCaptureAmountNeeded(int needed) {
		captureAmountNeeded = needed;
	}
	public Set<Player> getInsideCaptureZone(){
		return inside;
	}
	public int getTickAdvancement() {
		return tickAdvancement;
	}
	public void setEnabled(boolean isEnabled) {
		this.isEnabled = isEnabled;
	}
	public void setText(Component text) {
		this.text = text;
	}
	
	//
	
	public void setup(KothEffect effect, World world) {
		this.effect = effect;
	
		display = (TextDisplay)world.spawnEntity(displayLocation.toLocation(world), EntityType.TEXT_DISPLAY);
		display.setBillboard(Billboard.CENTER);
		display.setShadowed(true);
		SpatialUtil.rectangle3DFromPoints(
				boundingBox.get(),
				PARTICLE_DENSITY, 
				v -> rectangle.add(new Location(world, v.getX(), v.getY(), v.getZ())));
		effect.setup(this);
		isEnabled = true;
	}
	public void clean() {
		effect.clean();
		captureAdvancement = 0;
		display.remove();
	}
	public void tick() {
		if(!isEnabled)
			return;
		
		//particle
		var particleData = effect.getParticle(owningTeam);
		rectangle.forEach(l -> l.getWorld().spawnParticle(
				particleData.a(), l, 1, particleData.b()));
		
		//capture
		var wrapping = Ioc.resolve(WrappingModule.class);
		tickAdvancement = 0;
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
		captureAdvancement += tickAdvancement;
		if (captureAdvancement < 0) {
			captureAdvancement = 0;
			tickAdvancement = 0;
		}
		if (captureAdvancement >= captureAmountNeeded) {
			var newOwning = Ioc.resolve(WrappingModule.class).
					getWrapperOptional(firstEnemy, PlayerWrapper.class).get().getTeam();
			effect.capture(newOwning, owningTeam);
			owningTeam = newOwning;
			captureAdvancement = 0;
			tickAdvancement = 0;
		}
		
		//effect & display
		effect.tick();
		display.text(text);
	}
}
