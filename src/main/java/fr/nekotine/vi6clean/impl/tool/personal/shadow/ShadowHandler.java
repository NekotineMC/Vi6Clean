package fr.nekotine.vi6clean.impl.tool.personal.shadow;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
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
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

@ToolCode("shadow")
public class ShadowHandler extends ToolHandler<Shadow>{
	private final double SHADOW_KILL_RANGE_BLOCK = getConfiguration().getDouble("kill_range",1);
	private final ItemStack DISPONIBLE_ITEM () {return new ItemStackBuilder(
			Material.WITHER_SKELETON_SKULL)
			.name(getDisplayName().append(Component.text(" - ")).append(Component.text("Disponible", NamedTextColor.BLUE)))
			.lore(getLore())
			.unstackable()
			.flags(ItemFlag.values())
			.build();
	}
	private final ItemStack PLACED_ITEM () {return new ItemStackBuilder(
			Material.SKELETON_SKULL)
			.name(getDisplayName().append(Component.text(" - ")).append(Component.text("Placée", NamedTextColor.GRAY)))
			.lore(getLore())
			.unstackable()
			.flags(ItemFlag.values())
			.build();
	}
	private ItemStack EMP_ITEM () {return new ItemStackBuilder(
			Material.PLAYER_HEAD)
			.name(getDisplayName().append(Component.text(" - ")).append(Component.text("Brouillée", NamedTextColor.RED)))
			.lore(getLore())
			.unstackable()
			.flags(ItemFlag.values())
			.skull("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWM3NzcyYzdjZGNkZGI2Yjc5ZDU1MjVmOWRjZWJjNzQ4YWFiZGFlMzhkOWUzOGVlYTdmZTc4YTUwMWRlNmVkZSJ9fX0=")
			.build();
	}
	
	public ShadowHandler() {
		super(Shadow::new);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
	}
	
	@Override
	protected void onAttachedToPlayer(Shadow tool, Player player) {
	}

	@Override
	protected void onDetachFromPlayer(Shadow tool, Player player) {
		tool.tryUse();
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
		if (EventUtil.isCustomAction(evt, CustomAction.HIT_ANY)) {
			if (tool.tryUse()) {
				evt.setCancelled(true);
				remove(tool);
			}else if (tool.tryPlace()) {
				evt.setCancelled(true);
			}
		}
	}
	
	@EventHandler
	private void onPlayerMove(PlayerMoveEvent evt) {
		var player = evt.getPlayer();
		var ite = getTools().iterator();
		var wrappingModule = Ioc.resolve(WrappingModule.class);
		while (ite.hasNext()) {
			var tool = ite.next();
			var wrap = wrappingModule.getWrapperOptional(tool.getOwner(), PlayerWrapper.class);
			if (tool.getPlaced() == null || !wrap.isPresent() || !wrap.get().ennemiTeamInMap().anyMatch(p -> p.equals(evt.getPlayer()))) {
				continue;
			}
			if (tool.getPlaced().getLocation().distanceSquared(player.getLocation()) <= SHADOW_KILL_RANGE_BLOCK) {
				tool.getOwner().damage(1000, evt.getPlayer());
				Vi6Sound.SHADOW_KILL.play(player.getWorld());
				remove(tool);
			}
		}
	}
	
	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		if (evt.timeStampReached(TickTimeStamp.QuartSecond)){
			for (var tool : getTools()) {
				tool.lowTick();
			}
		}
	}
	
	public double getShadowKillRangeBlock() {
		return SHADOW_KILL_RANGE_BLOCK;
	}
	public ItemStack getDisponibleItem() {
		return DISPONIBLE_ITEM();
	}
	public ItemStack getPlacedItem() {
		return PLACED_ITEM();
	}
	public ItemStack getEmpItem() {
		return EMP_ITEM();
	}
}
