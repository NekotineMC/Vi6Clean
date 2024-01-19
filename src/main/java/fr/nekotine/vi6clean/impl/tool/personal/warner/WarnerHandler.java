package fr.nekotine.vi6clean.impl.tool.personal.warner;

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
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.game.phase.Vi6PhaseInMap;
import fr.nekotine.vi6clean.impl.map.Vi6Map;
import fr.nekotine.vi6clean.impl.map.artefact.Artefact;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

@ToolCode("warner")
public class WarnerHandler extends ToolHandler<Warner>{
	private final ItemStack UNPLACED() {
		return new ItemStackBuilder(Material.ENDER_EYE)
		.name(getDisplayName())
		.lore(getLore())
		.unstackable()
		.flags(ItemFlag.values())
		.build();
	}
	private final int WARN_DELAY_TICK = (int)(20*getConfiguration().getDouble("delay", 2));
	private final double PLACE_RANGE = getConfiguration().getDouble("place_range", 8);
	private final double PLACE_RANGE_SQUARED = PLACE_RANGE * PLACE_RANGE;
	private final float DISPLAY_DISTANCE = 0.8f;
	private final float DISPLAY_SCALE = 1.4f;
	private final String WARN_MESSAGE = "<gold>Avertisseur>></gold> <red>L'artéfact <aqua><name></aqua> à été volé !</red>";
	private Vi6Map map;
	
	//
	
	public WarnerHandler() {
		super(Warner::new);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
	}
	
	//
	
	protected Optional<Artefact> getCloseArtefact(Location pLoc){
		var watchedArticats = getTools().stream().map(w -> w.getWatched());
		return map.getArtefacts().values().stream().filter(art -> 
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
		var iterator = getTools().iterator();
		while(iterator.hasNext()) {
			var tool = iterator.next();
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
	
	//
	
	public Component getWarnMessage(String artefactName) {
		return Ioc.resolve(TextModule.class).message(Leaf.builder()
				.addStyle(Placeholder.unparsed("name", artefactName))
				.addStyle(NekotineStyles.STANDART)
				.addLine(WARN_MESSAGE)
		).buildFirst();
	}
	public int getWarnDelayTick() {
		return WARN_DELAY_TICK;
	}
	public double getPlaceRange() {
		return PLACE_RANGE;
	}
	public float getDisplayDistance() {
		return DISPLAY_DISTANCE;
	}
	public float getDisplayScale() {
		return DISPLAY_SCALE;
	}
	public ItemStack getUnplaced() {
		return UNPLACED();
	}
}
