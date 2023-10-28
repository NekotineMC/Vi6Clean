package fr.nekotine.vi6clean.impl.tool.personal;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import fr.nekotine.core.NekotineCore;
import fr.nekotine.core.inventory.ItemStackBuilder;
import fr.nekotine.core.ticking.TickTimeStamp;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.tuple.Triplet;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.core.util.SpatialUtil;
import fr.nekotine.vi6clean.constant.Vi6ToolLoreText;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.tool.ToolType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public class RadarHandler extends ToolHandler<Radar>{
	protected static final int DETECTION_BLOCK_RANGE = 20;
	protected static final int DETECTION_RANGE_SQUARED = DETECTION_BLOCK_RANGE * DETECTION_BLOCK_RANGE;
	protected static final int DELAY_SECOND = 5;
	
	protected static final ItemStack UNPLACED = new ItemStackBuilder(Material.LIGHTNING_ROD)
			.unstackable()
			.name(Component.text("Radar", NamedTextColor.GOLD))
			.lore(RadarHandler.LORE)
			.build();
	protected static final ItemStack PLACED = ItemStackUtil.addEnchant(UNPLACED.clone(), Enchantment.QUICK_CHARGE, 1);
	protected static final LinkedList<Triplet<Double, Double, Double>> BALL = new LinkedList<Triplet<Double, Double, Double>>();
	protected static final LinkedList<Triplet<Double, Double, Double>> SPHERE = new LinkedList<Triplet<Double, Double, Double>>();
	protected static final Transformation TOP_TRANSFORMATION = new Transformation(
    		new Vector3f(0, 0, 0),
    		new AxisAngle4f((float) Math.PI, new Vector3f(0, 1, 0)),
            new Vector3f(1,1,1),
            new AxisAngle4f((float) Math.PI, new Vector3f(0, 1, 0))
    );
	
	//
	
	public static final List<Component> LORE = Vi6ToolLoreText.RADAR.make(
			Placeholder.unparsed("range", DETECTION_BLOCK_RANGE+" block"),
			Placeholder.parsed("delay", DELAY_SECOND+" secondes")
	);
	
	//
	
	public RadarHandler() {
		super(ToolType.RADAR, Radar::new);
		NekotineCore.MODULES.tryLoad(TickingModule.class);
		SpatialUtil.ball3DDensity(RadarHandler.DETECTION_BLOCK_RANGE, 0.5, 
				(offsetX, offsetY, offsetZ) -> {
					BALL.add(Triplet.from(offsetX, offsetY, offsetZ));
		});
		SpatialUtil.sphere3DDensity(RadarHandler.DETECTION_BLOCK_RANGE, 1, 
				(offsetX, offsetY, offsetZ) -> {
					SPHERE.add(Triplet.from(offsetX, offsetY, offsetZ));
		});
	}
	
	//
	
	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		for(var tool : getTools()) {
			tool.tickCharge();
			if(evt.timeStampReached(TickTimeStamp.QuartSecond))
				tool.tickParticle();
			if(evt.timeStampReached(TickTimeStamp.Second))
				tool.tickSound();
		}
	}
	
	@EventHandler
	private void onPlayerToggleSneak(PlayerToggleSneakEvent evt) {
		var tools = getTools().stream().filter(t -> evt.getPlayer().equals(t.getOwner())).collect(Collectors.toUnmodifiableSet());
		for (var tool : tools) {
			tool.setSneaking(evt.isSneaking());
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
		if (EventUtil.isCustomAction(evt, CustomAction.INTERACT_ANY) && optionalTool.get().tryPlace()) {
			evt.setCancelled(true);
		}
	}

	//
	
	@Override
	protected void onAttachedToPlayer(Radar tool, Player player) {
	}
	@Override
	protected void onDetachFromPlayer(Radar tool, Player player) {
	}
}
