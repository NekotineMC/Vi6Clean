package fr.nekotine.vi6clean.impl.map.artefact;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import fr.nekotine.core.NekotineCore;
import fr.nekotine.core.block.BlockPatch;
import fr.nekotine.core.map.annotation.ComposingMap;
import fr.nekotine.core.map.element.MapBlockLocationElement;
import fr.nekotine.core.map.element.MapBoundingBoxElement;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.Vi6Main;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.wrapper.InMapPhasePlayerWrapper;
import fr.nekotine.vi6clean.impl.wrapper.InfiltrationPhasePlayerWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class Artefact{
	
	private static final int CAPTURE_AMOUNT_NEEDED = 200;
	
	private int capture_advancement;
	
	private String name;
	
	private Set<Player> inside = new HashSet<>(8);
	
	private boolean isCaptured;
	
	private final BlockPatch blockPatch = new BlockPatch(s -> s.setType(Material.AIR)); // For now
	
	@ComposingMap
	private MapBlockLocationElement blockPosition = new MapBlockLocationElement();
	
	@ComposingMap
	private MapBoundingBoxElement boundingBox = new MapBoundingBoxElement();

	public BoundingBox getBoundingBox() {
		return boundingBox.get();
	}
	
	public MapBlockLocationElement getBlockPosition() {
		return blockPosition;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
	
	public void capture() {
		blockPatch.patch(Vi6Main.IOC.resolve(Vi6Game.class).getWorld().getBlockAt(
						blockPosition.getX(),
						blockPosition.getY(),
						blockPosition.getZ())
				);
		isCaptured = true;
	}
	
	public void clean() {
		blockPatch.unpatchAll();
		isCaptured = false;
		capture_advancement = 0;
	}
	
	public boolean isCaptured() {
		return isCaptured;
	}
	
	public Set<Player> getInsideCaptureZone(){
		return inside;
	}
	
	public void tick() {
		var game = Vi6Main.IOC.resolve(Vi6Game.class);
		var wrapping = NekotineCore.MODULES.get(WrappingModule.class);
		if(isCaptured) {
			game.getWorld().spawnParticle(Particle.SPELL_WITCH, blockPosition.getX(), blockPosition.getY(), blockPosition.getZ(), 1, 0.5, 0.5, 0.5, 0);
		}else {
			game.getWorld().spawnParticle(Particle.COMPOSTER, blockPosition.getX(), blockPosition.getY(), blockPosition.getZ(), 2, 0.5, 0.5, 0.5);
			int tickAdvancement = 0;
			boolean guardCanceling = false;
			Player firstThief = null;
			var guardMsg = Component.text("Artefact: ", NamedTextColor.AQUA)
					.append(Component.text(name, NamedTextColor.AQUA))
					.append(Component.text(" Status: ", NamedTextColor.WHITE))
					.append(isCaptured ?
							Component.text("Volé", NamedTextColor.RED):
								Component.text("Sécurisé", NamedTextColor.GREEN));
			var thiefMsg = Component.text("Vole ", NamedTextColor.GOLD)
					.append(Component.text(name, NamedTextColor.AQUA))
					.append(Component.text(" - ", NamedTextColor.GOLD))
					.append(Component.text(capture_advancement*100/CAPTURE_AMOUNT_NEEDED, NamedTextColor.AQUA))
					.append(Component.text('%', NamedTextColor.GOLD));
			for (var player : inside) {
				if (game.getGuards().contains(player)) {
					guardCanceling = true;
					player.sendActionBar(guardMsg);
				}
				if (game.getThiefs().contains(player)) {
					var optWrapper = wrapping.getWrapperOptional(player, InMapPhasePlayerWrapper.class);
					if (optWrapper.isEmpty() || !optWrapper.get().canCaptureArtefact()) {
						continue;
					}
					tickAdvancement++;
					firstThief = player;
					player.sendActionBar(thiefMsg);
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
				NekotineCore.MODULES.get(WrappingModule.class).getWrapper(firstThief, InfiltrationPhasePlayerWrapper.class).capture(this);
			}
		}
	}
}
