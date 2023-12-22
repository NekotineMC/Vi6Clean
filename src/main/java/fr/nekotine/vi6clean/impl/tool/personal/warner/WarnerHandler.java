package fr.nekotine.vi6clean.impl.tool.personal.warner;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.inventory.ItemStackBuilder;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.text.TextModule;
import fr.nekotine.core.text.style.NekotineStyles;
import fr.nekotine.core.text.tree.Leaf;
import fr.nekotine.core.ticking.TickTimeStamp;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.vi6clean.constant.Vi6ToolLoreText;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.game.phase.Vi6PhaseInMap;
import fr.nekotine.vi6clean.impl.map.Vi6Map;
import fr.nekotine.vi6clean.impl.map.artefact.Artefact;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

@ToolCode("warner")
public class WarnerHandler extends ToolHandler<Warner>{
	protected static final ItemStack UNPLACED() {
		return new ItemStackBuilder(Material.ENDER_EYE)
		.name(Component.text("Avertisseur",NamedTextColor.GOLD))
		.lore(WarnerHandler.LORE)
		.unstackable()
		.flags(ItemFlag.values())
		.build();
	}
	protected static Component BUILD_WARN_MESSAGE(String artefactName) {
		return Ioc.resolve(TextModule.class).message(Leaf.builder()
				.addStyle(Placeholder.unparsed("name", artefactName))
				.addStyle(NekotineStyles.STANDART)
				.addLine(WARN_MESSAGE)
		).buildFirst();
	}
	protected static final int WARN_DELAY_SECOND = 2;
	protected static final int PLACE_RANGE = 8;
	protected static final int PLACE_RANGE_SQUARED = PLACE_RANGE * PLACE_RANGE;
	protected static final int DISPLAY_TURN_DURATION_TICKS = 10;
	protected static final float DISPLAY_DISTANCE = 0.8f;
	protected static final float DISPLAY_SCALE = 1.4f;
	protected static final String WARN_MESSAGE = "<gold>Avertisseur>></gold> <red>L'artéfact <aqua><name></aqua> à été volé !</red>";
	protected Vi6Map map;
	
	//
	
	public static final List<Component> LORE = Vi6ToolLoreText.WARNER.make(
			Placeholder.parsed("delay", WARN_DELAY_SECOND+" secondes")
	);
	
	//
	
	public WarnerHandler() {
		super(Warner::new);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
	}
	
	//
	
	protected Optional<Artefact> getCloseArtefact(Location pLoc){
		var watchedArticats = getTools().stream().map(w -> w.getWatched());
		return map.getArtefacts().backingMap().values().stream().filter(art -> 
			 (!art.isCaptured()) && 
			 (art.getBlockPosition().toLocation(pLoc.getWorld()).distanceSquared(pLoc) <= PLACE_RANGE_SQUARED) && 
			 (watchedArticats.noneMatch(watched -> watched!=null && watched.equals(art)))
		).findFirst();
	}
	
	@Override
	protected void onStartHandling() {
		map = Ioc.resolve(Vi6Game.class).getPhaseMachine().getPhase(Vi6PhaseInMap.class).getMap();
	}
	
	//

	@Override
	protected void onAttachedToPlayer(Warner tool, Player player) {
	}

	@Override
	protected void onDetachFromPlayer(Warner tool, Player player) {
	}
	
	//
	
	@EventHandler
	private void onPlayerInterract(PlayerInteractEvent evt) {
		if (evt.getHand() != EquipmentSlot.HAND) {
			return;
		}
		var evtP = evt.getPlayer();
		var optionalTool = getTools().stream().filter(t -> evtP.equals(t.getOwner()) && t.getItemStack().isSimilar(evt.getItem())).findFirst();
		if (optionalTool.isEmpty()) {
			return;
		}
		
		if(EventUtil.isCustomAction(evt, CustomAction.INTERACT_ANY)) {
			evt.setCancelled(true);
		}
		if (EventUtil.isCustomAction(evt, CustomAction.HIT_ANY) && optionalTool.get().place(this)) {
			evt.setCancelled(true);
		}
	}
	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		for(var tool : getTools()) {
			tool.tickWarning();
			if(evt.timeStampReached(TickTimeStamp.HalfSecond)) {
				tool.tickDisplay();
				tool.tickParticles();
			}
		}
	}
	@EventHandler
	private void onHandChange(PlayerItemHeldEvent evt) {
		var evtP = evt.getPlayer();
		ItemStack newHeld = evtP.getInventory().getItem(evt.getNewSlot());
		getTools().stream().filter(t -> evtP.equals(t.getOwner())).forEach(
				t -> t.setInHand(t.getItemStack().isSimilar(newHeld)));
	}
	@EventHandler
	private void onPlayerToggleSneak(PlayerToggleSneakEvent evt) {
		var tools = getTools().stream().filter(t -> evt.getPlayer().equals(t.getOwner())).collect(Collectors.toUnmodifiableSet());
		for (var tool : tools) {
			tool.setSneaking(evt.isSneaking());
		}
	}

}
