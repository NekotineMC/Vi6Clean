package fr.nekotine.vi6clean.impl.map.koth;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.map.annotation.GenerateCommandFor;
import fr.nekotine.core.map.annotation.GenerateSpecificCommandFor;
import fr.nekotine.core.map.command.generator.LocationCommandGenerator;
import fr.nekotine.core.serialization.configurationserializable.annotation.ComposingConfiguration;
import fr.nekotine.core.serialization.configurationserializable.annotation.MapDictKey;
import fr.nekotine.core.util.SpatialUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6Team;
import fr.nekotine.vi6clean.impl.wrapper.InMapPhasePlayerWrapper;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.BlockVector;
import org.bukkit.util.BoundingBox;

public class Koth {
	@MapDictKey
	private String name = "";
	@GenerateCommandFor
	@ComposingConfiguration
	private BoundingBox boundingBox = new BoundingBox();

	@GenerateSpecificCommandFor(LocationCommandGenerator.class)
	@ComposingConfiguration
	private BlockVector displayLocation = new BlockVector();

	private Set<Player> inside = new HashSet<>(8);

	private boolean isEnabled = false;
	private int captureAmountNeeded = 200;
	private Vi6Team owningTeam = Vi6Team.GUARD;
	private Component text = Component.text("");
	private int tickAdvancement;
	private TextDisplay display;
	private int captureAdvancement;
	private AbstractKothEffect effect;
	private Collection<BlockDisplay> rectangleEdges;

	//

	public BoundingBox getBoundingBox() {
		return boundingBox;
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

	public Set<Player> getInsideCaptureZone() {
		return inside;
	}

	public int getTickAdvancement() {
		return tickAdvancement;
	}

	public void setText(Component text) {
		this.text = text;
	}

	public void setBlockDisplayData(BlockData data) {
		if (rectangleEdges != null) {
			for (var edge : rectangleEdges) {
				edge.setBlock(data);
			}
			rectangleEdges.clear();
		}
	}

	public String getName() {
		return name;
	}

	public BlockVector getDisplayLocation() {
		return displayLocation;
	}

	//

	public void setup(AbstractKothEffect effect, World world) {
		this.effect = effect;
		display = (TextDisplay) world.spawnEntity(displayLocation.toLocation(world), EntityType.TEXT_DISPLAY,
				CreatureSpawnEvent.SpawnReason.CUSTOM, display -> {
					if (display instanceof TextDisplay d) {
						d.setBillboard(Billboard.CENTER);
						d.setShadowed(true);
						d.setPersistent(false);
					}
				});
		rectangleEdges = SpatialUtil.boundingBoxEdgeAsDisplayBlocks(world, getBoundingBox(),
				Material.BARRIER.createBlockData(), 0.06f);
		isEnabled = true;
		effect.setKoth(this);
		effect.setup();
	}

	public void clean() {
		if (rectangleEdges != null) {
			for (var edge : rectangleEdges) {
				edge.remove();
			}
			rectangleEdges.clear();
		}
		if (!isEnabled) {
			return;
		}
		effect.clean();
		captureAdvancement = 0;
		display.remove();
	}

	public void tick() {
		if (!isEnabled)
			return;

		// capture
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
			} else {
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
			var newOwning = Ioc.resolve(WrappingModule.class).getWrapperOptional(firstEnemy, PlayerWrapper.class).get()
					.getTeam();
			effect.capture(newOwning, owningTeam);
			owningTeam = newOwning;
			captureAdvancement = 0;
			tickAdvancement = 0;
		}

		// effect & display
		effect.tick();
		display.text(text);
	}
}
