package fr.nekotine.vi6clean.map.koth;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.map.annotation.GenerateCommandFor;
import fr.nekotine.core.serialization.configurationserializable.annotation.ComposingConfiguration;
import fr.nekotine.core.serialization.configurationserializable.annotation.MapDictKey;
import fr.nekotine.core.util.BukkitUtil;
import fr.nekotine.core.util.SpatialUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6Team;
import fr.nekotine.vi6clean.wrapper.InMapPhasePlayerWrapper;
import fr.nekotine.vi6clean.wrapper.PlayerWrapper;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;

public class Koth {
	@MapDictKey
	private String name = "";
	@GenerateCommandFor
	@ComposingConfiguration
	private BoundingBox boundingBox = new BoundingBox();

	@GenerateCommandFor
	@ComposingConfiguration
	private Location displayLocation = BukkitUtil.defaultLocation();

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
	private ItemDisplay model;

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
		}
	}

	public String getName() {
		return name;
	}

	public Location getDisplayLocation() {
		return displayLocation;
	}

	//

	public void setup(AbstractKothEffect effect, World world) {
		this.effect = effect;

		var displayloc = displayLocation.toLocation(world);
		rectangleEdges = SpatialUtil.boundingBoxEdgeAsDisplayBlocks(world, getBoundingBox(),
				Material.BARRIER.createBlockData(), 0.06f);
		isEnabled = true;
		effect.setKoth(this);
		effect.setup();

		var modelKey = effect.getModelKey();
		if (modelKey != null) {
			var traceResult = world.rayTraceBlocks(displayloc, new Vector(0, -1, 0), 3, FluidCollisionMode.NEVER, true);
			if (traceResult != null) {
				// on déplace le text pour éviter qu'il rentre dans le model
				displayloc = traceResult.getHitPosition().toLocation(world).add(new Vector(0, 2, 0));
				// Ajouter 0.5 pour pas que le modèle rentre dans le sol
				var modelloc = traceResult.getHitPosition().toLocation(world).add(new Vector(0, 0.5, 0));
				modelloc.setYaw(displayLocation.getYaw());
				model = (ItemDisplay) world.spawnEntity(modelloc, EntityType.ITEM_DISPLAY, SpawnReason.CUSTOM, e -> {
					if (e instanceof ItemDisplay display) {
						display.setPersistent(false);
						var stack = new ItemStack(Material.IRON_BLOCK);
						stack.setData(DataComponentTypes.ITEM_MODEL, modelKey);
						display.setItemStack(stack);
					}
				});
			}
		}

		display = (TextDisplay) world.spawnEntity(displayloc, EntityType.TEXT_DISPLAY,
				CreatureSpawnEvent.SpawnReason.CUSTOM, display -> {
					if (display instanceof TextDisplay d) {
						d.setBillboard(Billboard.CENTER);
						d.setShadowed(true);
						d.setPersistent(false);
					}
				});

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
		if (model != null) {
			model.remove();
		}
	}

	public void tick() {
		if (!isEnabled)
			return;

		// capture
		var wrapping = Ioc.resolve(WrappingModule.class);
		tickAdvancement = 0;
		boolean owningTeamCancelling = false;
		Player firstEnemy = null;
		var ite = inside.iterator();
		while (ite.hasNext()) {
			var player = ite.next();
			var optWrapper = wrapping.getWrapperOptional(player, InMapPhasePlayerWrapper.class);
			if (optWrapper.isEmpty())
				continue;

			if (!optWrapper.get().isInside()) {
				ite.remove();
				continue;
			}
			if (optWrapper.get().getParentWrapper().getTeam() == owningTeam) {
				owningTeamCancelling = true;
			} else {
				firstEnemy = player;
				tickAdvancement++;
			}
		}
		if (owningTeamCancelling && tickAdvancement > 0)
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
