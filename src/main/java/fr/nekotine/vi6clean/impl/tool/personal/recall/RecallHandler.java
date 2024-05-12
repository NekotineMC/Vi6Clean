package fr.nekotine.vi6clean.impl.tool.personal.recall;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.inventory.ItemStackBuilder;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.track.ClientTrackModule;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import net.kyori.adventure.text.format.TextDecoration;

@ToolCode("recall")
public class RecallHandler extends ToolHandler<Recall>{
	private final int TELEPORT_DELAY_TICKS = (int)(20*getConfiguration().getDouble("teleport_delay",6));
	private final int PARTICLE_NUMBER = getConfiguration().getInt("particle_number",2);
	private final int COOLDOWN_TICKS = (int)(20*getConfiguration().getDouble("cooldown",1));
	private final ItemStack UNPLACED = new ItemStackBuilder(
		Material.CHORUS_FRUIT)
		.name(getDisplayName())
		.lore(getLore())
		.unstackable()
		.flags(ItemFlag.values())
		.build();
	private final ItemStack PLACED = new ItemStackBuilder(
		Material.POPPED_CHORUS_FRUIT)
		.name(getDisplayName())
		.lore(getLore())
		.unstackable()
		.flags(ItemFlag.values())
		.build();
	private final ItemStack COOLDOWN = new ItemStackBuilder(
			Material.PURPLE_DYE)
			.name(getDisplayName().decorate(TextDecoration.STRIKETHROUGH))
			.lore(getLore())
			.unstackable()
			.flags(ItemFlag.values())
			.build();

	public RecallHandler() {
		super(Recall::new);
		Ioc.resolve(ModuleManager.class).tryLoad(ClientTrackModule.class);
	}

	@Override
	protected void onAttachedToPlayer(Recall tool, Player player) {
	}
	@Override
	protected void onDetachFromPlayer(Recall tool, Player player) {
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
		
		if(EventUtil.isCustomAction(evt, CustomAction.INTERACT_ANY)) {
			evt.setCancelled(true);
		}
		if (EventUtil.isCustomAction(evt, CustomAction.HIT_ANY) && optionalTool.get().use()) {
			evt.setCancelled(true);
		}
	}
	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		for(var tool : getTools()) {
			tool.tickCooldown();
			tool.tickParticle();
			tool.tickTest();
		}
	}
	
	@EventHandler
	private void onPlayerDeath(PlayerDeathEvent evt) {
		var player = evt.getPlayer();
		if(!getTools().stream().anyMatch(r -> player.equals(r.getOwner()))) return;
		Ioc.resolve(ClientTrackModule.class).track(player);
	}
	
	//
	
	public ItemStack getPlaced() {
		return PLACED;
	}
	public ItemStack getUnplaced() {
		return UNPLACED;
	}
	public ItemStack getCooldown() {
		return COOLDOWN;
	}
	public int getTeleportDelayTicks() {
		return TELEPORT_DELAY_TICKS;
	}
	public int getParticleNumber() {
		return PARTICLE_NUMBER;
	}
	public int getCooldownTick() {
		return COOLDOWN_TICKS;
	}
}
