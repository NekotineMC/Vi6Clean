package fr.nekotine.vi6clean.impl.tool.personal.portable_teleporter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.inventory.ItemStackBuilder;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.tuple.Pair;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.util.SpatialUtil;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import net.kyori.adventure.text.format.TextDecoration;

@ToolCode("portable_teleporter")
public class PortableTeleporterHandler extends ToolHandler<PortableTeleporter>{
	private final int CHARGES = getConfiguration().getInt("teleportation_charges",3);
	private final int DELAY_TICK = (int)(20 * getConfiguration().getDouble("teleportation_delay",4));
	private final int COOLDOWN_TICK = (int)(20 * getConfiguration().getDouble("teleportation_cooldown",10));
	private final int VFX_DELAY_TICK = getConfiguration().getInt("vfx_delay",5);
	private final ItemStack EMP_ITEM = new ItemStackBuilder(
			Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE)
			.lore(getLore())
			.name(getDisplayName().decorate(TextDecoration.STRIKETHROUGH))
			.flags(ItemFlag.values())
			.build();
	private final List<Consumer<Location>> PLAYER_VFX_1 = new ArrayList<>();
	private final List<Consumer<Location>> PLAYER_VFX_2 = new ArrayList<>();
	private final List<Consumer<Location>> PAD_VFX_1 = new ArrayList<>();
	private final List<Consumer<Location>> PAD_VFX_2 = new ArrayList<>();
	
	public PortableTeleporterHandler() {
		super(PortableTeleporter::new);
		SpatialUtil.helix(3, 0.75, 3, 0, 0.25, v -> 
		PLAYER_VFX_1.add(l -> l.getWorld().spawnParticle(Particle.GLOW, l.clone().add(v), 1,0,0,0,0,null)));
		SpatialUtil.helix(3, 0.75, 3, Math.PI, 0.25, v -> 
		PLAYER_VFX_2.add(l -> l.getWorld().spawnParticle(Particle.GLOW, l.clone().add(v), 1,0,0,0,0,null)));
		SpatialUtil.helix(3, 0.75, 3,0, 0.25, v -> 
		PAD_VFX_1.add(l -> l.getWorld().spawnParticle(Particle.GLOW, l.clone().add(v), 1,0,0,0,0,null)));
		SpatialUtil.helix(3, 0.75, 3, Math.PI, 0.25, v -> 
		PAD_VFX_2.add(l -> l.getWorld().spawnParticle(Particle.GLOW, l.clone().add(v), 1,0,0,0,0,null)));
	}

	//
	
	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		getTools().forEach(PortableTeleporter::tick);
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
		
		var tool = optionalTool.get();
		if (EventUtil.isCustomAction(evt, CustomAction.HIT_ANY)){

			if(evtP.isSneaking()) {
				evt.setCancelled(tool.tryPlace());
			}else if(!tool.getOwner().hasCooldown(tool.getItemStack().getType())){
				if(tool.tryTeleport()) {
					tool.getOwner().setCooldown(tool.getItemStack().getType(), DELAY_TICK);
					evt.setCancelled(true);
				}
			}
		}
	}
	
	//
	
	@Override
	protected void onAttachedToPlayer(PortableTeleporter tool, Player player) {
	}
	@Override
	protected void onDetachFromPlayer(PortableTeleporter tool, Player player) {
	}

	//
	
	protected ItemStack getItem(int amount) {
		var amountcpnt =
				Component.text(" [", NamedTextColor.WHITE)
				.append(Component.text(amount, NamedTextColor.GREEN))
				.append(Component.text("/", NamedTextColor.WHITE))
				.append(Component.text(CHARGES,NamedTextColor.GREEN))
				.append(Component.text("]", NamedTextColor.WHITE));
		return new ItemStackBuilder(
				Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE)
				.lore(getLore())
				.name(getDisplayName().append(amountcpnt))
				.flags(ItemFlag.values())
				.build();
	}
	protected ItemStack getEmpItem() {
		return EMP_ITEM;
	}
	protected int getCharges() {
		return CHARGES;
	}
	protected int getDelayTick() {
		return DELAY_TICK;
	}
	protected Pair<List<Consumer<Location>>,List<Consumer<Location>>> getPlayerVFX(){
		return Pair.from(PLAYER_VFX_1, PLAYER_VFX_2);
	}
	protected Pair<List<Consumer<Location>>,List<Consumer<Location>>> getPadVFX(){
		return Pair.from(PAD_VFX_1, PAD_VFX_2);
	}
	protected int getVfxDelayTick() {
		return VFX_DELAY_TICK;
	}
	protected int getCooldownTick() {
		return COOLDOWN_TICK;
	}
}
