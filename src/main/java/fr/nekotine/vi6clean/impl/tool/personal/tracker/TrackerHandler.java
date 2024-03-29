package fr.nekotine.vi6clean.impl.tool.personal.tracker;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;

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
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

@ToolCode("tracker")
public class TrackerHandler extends ToolHandler<Tracker>{
	//private static final int REFRESH_DELAY_SECOND = 2;
	protected static final double RAY_DISTANCE = 100;
	protected static final int RAY_SIZE = 0;
	private final ItemStack GUN_ITEM() {
		return new ItemStackBuilder(
		Material.CROSSBOW)
		.name(getDisplayName().append(Component.text(" - ").append(Component.text("Armé",NamedTextColor.AQUA))))
		.lore(getLore())
		.unstackable()
		.flags(ItemFlag.values())
		.build();
	}
	private final ItemStack EMP_ITEM() {
		return new ItemStackBuilder(
				Material.RECOVERY_COMPASS)
				.name(getDisplayName().append(Component.text(" - ").append(Component.text("Brouillé",NamedTextColor.RED))))
				.lore(getLore())
				.unstackable()
				.flags(ItemFlag.values())
				.build();
	}
	
	//private int n = 0;
	public TrackerHandler() {
		super(Tracker::new);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
	}
	@Override
	protected void onAttachedToPlayer(Tracker tool, Player player) {
	}
	@Override
	protected void onDetachFromPlayer(Tracker tool, Player player) {
	}
	
	//
	
	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		if(evt.timeStampReached(TickTimeStamp.Second)/* && ++n>=REFRESH_DELAY_SECOND*/) {
			//n = 0;
			for (var tool : getTools()) {
				tool.tickRefresh();
			}
		}
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
		if (EventUtil.isCustomAction(evt, CustomAction.HIT_ANY) && optionalTool.get().shoot(this)) {
			evt.setCancelled(true);
		}
	}
	
	public ItemStack getGunItem() {
		return GUN_ITEM();
	}
	public ItemStack getEmpItem() {
		return EMP_ITEM();
	}
	public final ItemStack getCompassItem(Player owner, Location hitLoc) {
		var distance = owner.getLocation().distance(hitLoc);
		Component name = Ioc.resolve(TextModule.class).message(Leaf.builder()
				.addLine("<gold>Traceur</gold> - <red>Distance: <aqua><distance>m")
				.addStyle(Placeholder.unparsed("distance", String.valueOf((int)distance)))
				.addStyle(NekotineStyles.STANDART)).buildFirst();	
				
		var item = new ItemStackBuilder(Material.COMPASS)
		.name(name)
		.lore(getLore())
		.unstackable()
		.flags(ItemFlag.values())
		.build();

		var meta = (CompassMeta)item.getItemMeta();
		meta.setLodestoneTracked(false);
		meta.setLodestone(hitLoc);
		item.setItemMeta(meta);

		return item;
	}
}
