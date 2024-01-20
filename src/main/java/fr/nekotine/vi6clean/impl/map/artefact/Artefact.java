package fr.nekotine.vi6clean.impl.map.artefact;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import fr.nekotine.core.block.BlockPatch;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.map.annotation.ComposingMap;
import fr.nekotine.core.map.annotation.MapDictKey;
import fr.nekotine.core.map.element.MapBlockLocationElement;
import fr.nekotine.core.map.element.MapBoundingBoxElement;
import fr.nekotine.core.util.SpatialUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6Team;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.game.phase.Vi6PhaseInMap;
import fr.nekotine.vi6clean.impl.wrapper.InMapPhasePlayerWrapper;
import fr.nekotine.vi6clean.impl.wrapper.InfiltrationPhasePlayerWrapper;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class Artefact{
	
	private static final int CAPTURE_AMOUNT_NEEDED = 200;
	
	private int capture_advancement;
	
	@MapDictKey
	@ComposingMap
	private String name = "";
	
	private Set<Player> inside = new HashSet<>(8);
	
	private boolean isCaptured;
	
	private final BlockPatch blockPatch = new BlockPatch(s -> s.setType(Material.AIR)); // For now
	
	@ComposingMap
	private MapBlockLocationElement blockPosition = new MapBlockLocationElement();
	
	@ComposingMap
	private MapBoundingBoxElement boundingBox = new MapBoundingBoxElement();

	private BlockDisplay boxDisplay;
	
	private boolean foundAfterCapture;
	
	public BoundingBox getBoundingBox() {
		return boundingBox.get();
	}
	
	public MapBlockLocationElement getBlockPosition() {
		return blockPosition;
	}

	public String getName() {
		return name;
	}
	
	public void capture() {
		blockPatch.patch(Ioc.resolve(Vi6Game.class).getWorld().getBlockAt(
						blockPosition.getX(),
						blockPosition.getY(),
						blockPosition.getZ())
				);
		isCaptured = true;
	}
	
	public void setup(World world) {
		boxDisplay = SpatialUtil.fillBoundingBox(world, getBoundingBox(), Material.ORANGE_STAINED_GLASS.createBlockData());
	}
	
	public void clean() {
		blockPatch.unpatchAll();
		isCaptured = false;
		capture_advancement = 0;
		boxDisplay.remove();
	}
	
	public boolean isCaptured() {
		return isCaptured;
	}
	
	public Set<Player> getInsideCaptureZone(){
		return inside;
	}

	public void tick() {
		var game = Ioc.resolve(Vi6Game.class);
		var wrapping = Ioc.resolve(WrappingModule.class);
		var phaseInMap = game.getPhaseMachine().getPhase(Vi6PhaseInMap.class);
		if(isCaptured) {
			
			if(!foundAfterCapture) {
				setFoundAfterCapture(inside.stream().anyMatch(p -> wrapping.getWrapper(p, PlayerWrapper.class).getTeam()==Vi6Team.GUARD));
			}
			
			game.getWorld().spawnParticle(Particle.SPELL_WITCH, blockPosition.getX()+0.5d, blockPosition.getY()+0.5d, blockPosition.getZ()+0.5d, 1, 0.5, 0.5, 0.5, 0);
			var stolenMessage = Component.text(name, NamedTextColor.GOLD)
					.append(Component.text(" >> ", NamedTextColor.WHITE))
					.append(Component.text("Volé", NamedTextColor.RED));
			inside.forEach(p -> wrapping.getWrapper(p, InMapPhasePlayerWrapper.class).getArtefactComponent().setText(stolenMessage));
		}else {
			game.getWorld().spawnParticle(Particle.COMPOSTER, blockPosition.getX()+0.5d, blockPosition.getY()+0.5d, blockPosition.getZ()+0.5d, 2, 0.5, 0.5, 0.5);
			int tickAdvancement = 0;
			boolean guardCanceling = false;
			Player firstThief = null;
			var guardMsg = Component.text(name, NamedTextColor.GOLD)
					.append(Component.text(" >> ", NamedTextColor.WHITE))
					.append(Component.text("Sécurisé", NamedTextColor.GREEN));
			var thiefMsg = Component.text(name, NamedTextColor.GOLD)
					.append(Component.text(" >> ", NamedTextColor.WHITE))
					.append(Component.text("Vole", NamedTextColor.GREEN))
					.append(Component.text(" (", NamedTextColor.WHITE))
					.append(Component.text(capture_advancement*100/CAPTURE_AMOUNT_NEEDED, NamedTextColor.AQUA))
					.append(Component.text("%", NamedTextColor.GOLD))
					.append(Component.text(")", NamedTextColor.WHITE));
			for (var player : inside) {
				if (game.getGuards().contains(player)) {
					guardCanceling = true;
					wrapping.getWrapper(player, InMapPhasePlayerWrapper.class).getArtefactComponent().setText(guardMsg);
					phaseInMap.objectiveSafe(this);
				}
				if (game.getThiefs().contains(player)) {
					var optWrapper = wrapping.getWrapperOptional(player, InMapPhasePlayerWrapper.class);
					if (optWrapper.isEmpty() || !optWrapper.get().canCaptureArtefact()) {
						continue;
					}
					tickAdvancement++;
					firstThief = player;
					wrapping.getWrapper(player, InMapPhasePlayerWrapper.class).getArtefactComponent().setText(thiefMsg);
				}
			}
			if (guardCanceling) {
				return;
			}
			if (tickAdvancement == 0) {
				tickAdvancement--;
			}
			capture_advancement += tickAdvancement;
			if (capture_advancement < 0) {
				capture_advancement = 0;
			}
			if (capture_advancement >= CAPTURE_AMOUNT_NEEDED) {
				Ioc.resolve(WrappingModule.class).getWrapper(firstThief, InfiltrationPhasePlayerWrapper.class).capture(this);
				phaseInMap.safeToUnknown();
				Bukkit.getPluginManager().callEvent(new ArtefactStealEvent(this, firstThief));
			}
		}
	}
	
	public void setFoundAfterCapture(boolean foundAfterCapture) {
		this.foundAfterCapture = foundAfterCapture;
		if(foundAfterCapture) {
			var game = Ioc.resolve(Vi6Game.class);
			var phaseInMap = game.getPhaseMachine().getPhase(Vi6PhaseInMap.class);
			phaseInMap.objectiveStolen(this);
		}
	}
}
