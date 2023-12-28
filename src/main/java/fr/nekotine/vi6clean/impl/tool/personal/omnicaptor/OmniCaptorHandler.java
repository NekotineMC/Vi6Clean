package fr.nekotine.vi6clean.impl.tool.personal.omnicaptor;

import java.util.Collection;
import java.util.LinkedList;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.inventory.ItemStackBuilder;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.ticking.TickTimeStamp;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

@ToolCode("omnicaptor")
public class OmniCaptorHandler extends ToolHandler<OmniCaptor>{
	private final double DETECTION_BLOCK_RANGE = getConfiguration().getDouble("range",3);
	private final double DETECTION_RANGE_SQUARED = DETECTION_BLOCK_RANGE * DETECTION_BLOCK_RANGE;
	private final ItemStack DISPONIBLE_ITEM () {return new ItemStackBuilder(Material.REPEATER)
			.name(getDisplayName().append(Component.text(" - ")).append(Component.text("Disponible", NamedTextColor.BLUE)))
			.lore(getLore())
			.unstackable()
			.flags(ItemFlag.values())
			.build();
	}
	private final ItemStack PLACED_ITEM () {return new ItemStackBuilder(Material.LEVER)
			.name(getDisplayName().append(Component.text(" - ")).append(Component.text("Placé", NamedTextColor.GRAY)))
			.lore(getLore())
			.unstackable()
			.flags(ItemFlag.values())
			.build();
	}
	private final ItemStack TRIGGERED_ITEM () {return new ItemStackBuilder(Material.REDSTONE_TORCH)
			.name(getDisplayName().append(Component.text(" - ")).append(Component.text("Activé", NamedTextColor.RED)))
			.lore(getLore())
			.unstackable()
			.flags(ItemFlag.values())
			.build();
	}
	
	public OmniCaptorHandler() {
		super(OmniCaptor::new);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
	}

	@Override
	protected void onAttachedToPlayer(OmniCaptor tool, Player player) {
		tool.setSneaking(false);
	}

	@Override
	protected void onDetachFromPlayer(OmniCaptor tool, Player player) {
	}
	
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
		if (EventUtil.isCustomAction(evt, CustomAction.INTERACT_ANY) && optionalTool.get().tryPickup()) {
			evt.setCancelled(true);
		}
		if (EventUtil.isCustomAction(evt, CustomAction.HIT_ANY) && optionalTool.get().tryPlace()) {
			evt.setCancelled(true);
		}
	}
	
	@EventHandler
	private void onPlayerToggleSneak(PlayerToggleSneakEvent evt) {
		var tools = getTools().stream().filter(t -> evt.getPlayer().equals(t.getOwner())).collect(Collectors.toUnmodifiableSet());
		for (var tool : tools) {
			tool.setSneaking(evt.isSneaking());
		}
	}
	
	private Collection<Player> inRange(ArmorStand as, OmniCaptor captor) {
		return captor.getEnemyTeam()
		.filter(ennemi -> ennemi.getLocation().distanceSquared(as.getLocation()) <= DETECTION_RANGE_SQUARED)
		.collect(Collectors.toCollection(LinkedList::new));
	}
	
	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		for (var tool : getTools()) {
			var as = tool.getPlaced();
			if (as == null) {
				if (evt.timeStampReached(TickTimeStamp.QuartSecond)){
					tool.lowTick();
				}
				continue;
			}
			var inRange = inRange(as, tool);
			var oldInRange = tool.getEnnemiesInRange();
			if (inRange.size() <= 0 && oldInRange.size() <= 0) {
				continue;
			}
			var ite = oldInRange.iterator();
			while (ite.hasNext()) {
				var p = ite.next();
				if (inRange.contains(p)) {
					inRange.remove(p);
				}else {
					tool.removeEffect(p);
					ite.remove();
				}
			}
			for (var p : inRange) {
				tool.applyEffect(p);
				oldInRange.add(p);
				Vi6Sound.OMNICAPTEUR_DETECT.play(p);
				var own = tool.getOwner();
				if (own != null) {
					Vi6Sound.OMNICAPTEUR_DETECT.play(own);
				}
			}
			tool.itemUpdate();
		}
	}
	
	public double getDetectionBlockRange() {
		return DETECTION_BLOCK_RANGE;
	}
	public ItemStack getDisponibleItem() {
		return DISPONIBLE_ITEM();
	}
	public ItemStack getPlacedItem() {
		return PLACED_ITEM();
	}
	public ItemStack getTriggeredItem() {
		return TRIGGERED_ITEM();
	}
}
