package fr.nekotine.vi6clean.impl.tool.personal.jawtrap;

import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.inventory.ItemStackBuilder;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;

@ToolCode("beartrap")
public class BearTrapHandler extends ToolHandler<BearTrap>{
	private final double DAMAGE = 2*getConfiguration().getDouble("damage", 5);
	private final double SQUARED_TRIGGER_RANGE = Math.pow(getConfiguration().getDouble("trigger_range", 0.85), 2);
	private final double SQUARED_PICKUP_RANGE = Math.pow(getConfiguration().getDouble("pickup_range", 0.85), 2);
	private final double SQUARED_SPACING_DISTANCE = Math.pow(getConfiguration().getDouble("spacing_distance", 0.85), 2);
	private final ItemStack ITEM ()  {return new ItemStackBuilder(Material.PLAYER_HEAD)
			.name(getDisplayName())
			.lore(getLore())
			.unstackable()
			.skull("d23dd5fc15b2d337347a94146ff20003b2d62f668b4517e1145d3acfcc25587c").build();
	}
	private final ItemStack TRIGGERED_ITEM = new ItemStackBuilder(Material.PLAYER_HEAD)
			.name(getDisplayName())
			.lore(getLore())
			.unstackable()
			.skull("85a8be4b3666eef20199c84d59efc7c771f4e3f290f9688fb12a97f65cdd64c7").build();

	public BearTrapHandler() {
		super(BearTrap::new);
	}
	@Override
	protected void onAttachedToPlayer(BearTrap tool, Player player) {
	}
	@Override
	protected void onDetachFromPlayer(BearTrap tool, Player player) {
	}
	@EventHandler
	private void onPlayerInterract(PlayerInteractEvent evt) {
		var evtP = evt.getPlayer();
		//Pickup
		if (EventUtil.isCustomAction(evt, CustomAction.INTERACT_ANY)){
			for(BearTrap tool : getTools()) {
				if(tool.tryPickup(evtP)) {
					evt.setCancelled(true);
					return;
				}
			}
			return;
		}
		
		//Place
		if (evt.getHand() != EquipmentSlot.HAND) {
			return;
		}
		if(getTools().stream().anyMatch(t -> t.isPlaced() && t.getLocation().distanceSquared(evtP.getLocation()) <= SQUARED_SPACING_DISTANCE)) {
			return;
		}
		var optionalTool = getTools().stream().filter(
				t -> evtP.equals(t.getOwner()) 
				&& t.getItemStack().isSimilar(evt.getItem())).findFirst();
		if (optionalTool.isEmpty()) {
			return;
		}
		if (EventUtil.isCustomAction(evt, CustomAction.HIT_ANY) && optionalTool.get().tryPlace()) {
			evt.setCancelled(true);
		}
	}
	@EventHandler
	private void onPlayerMove(PlayerMoveEvent evt) {
		for(BearTrap tool : getTools()) {
			if(!tool.isPlaced()) {
				continue;
			}
			if(tool.getEnemyTeam().noneMatch(p -> p.equals(evt.getPlayer()))) {
				continue;
			}
			if(tool.getLocation().distanceSquared(evt.getFrom()) <= SQUARED_TRIGGER_RANGE) {
				tool.trigger(evt.getPlayer());
				return;
			}
		}
	}
	@EventHandler
	public void onDamage(EntityDamageByEntityEvent evt) {
		if(!(evt.getEntity() instanceof LivingEntity hitEntity)) {
			return;
		}
		if(evt.getCause()!=DamageCause.MAGIC) {
			return;
		}
		var optionalTool = getTools().stream().filter(t -> 
				t.isTriggered() 
				&& t.getHit().equals(hitEntity) 
				&& t.getFang().equals(evt.getDamager())).findFirst();
		if (optionalTool.isEmpty()) {
			return;
		}
		hitEntity.damage(DAMAGE, optionalTool.get().getOwner());
		evt.setCancelled(true);
	}
	
	public double getDamage() {
		return DAMAGE;
	}
	public ItemStack getItem() {
		return ITEM();
	}
	public ItemStack getTriggeredItem() {
		return TRIGGERED_ITEM;
	}
	public double getSquaredPickupRange() {
		return SQUARED_PICKUP_RANGE;
	}
}
