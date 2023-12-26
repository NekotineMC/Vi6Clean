package fr.nekotine.vi6clean.impl.tool.personal.radar;

import java.util.LinkedList;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import fr.nekotine.core.inventory.ItemStackBuilder;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.text.TextModule;
import fr.nekotine.core.text.style.NekotineStyles;
import fr.nekotine.core.text.tree.Leaf;
import fr.nekotine.core.ticking.TickTimeStamp;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.tuple.Triplet;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.core.util.SpatialUtil;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

@ToolCode("radar")
public class RadarHandler extends ToolHandler<Radar>{
	private final double DETECTION_BLOCK_RANGE = getConfiguration().getDouble("range",20);
	private final double DETECTION_RANGE_SQUARED = DETECTION_BLOCK_RANGE * DETECTION_BLOCK_RANGE;
	private final int DELAY_TICK = (int)(20*getConfiguration().getDouble("delay",5));
	private final int COOLDOWN_TICK = (int)(20*getConfiguration().getDouble("cooldown",20));
	private final String DETECTION_SUCCESS = getConfiguration().getString("detection_success");
	private final String DETECTION_FAIL = getConfiguration().getString("detection_fail");

	private final ItemStack UNPLACED = new ItemStackBuilder(
			Material.CALIBRATED_SCULK_SENSOR)
			.unstackable()
			.name(getDisplayName())
			.lore(getLore())
			.build();
	private final ItemStack PLACED = ItemStackUtil.addEnchant(UNPLACED.clone(), Enchantment.QUICK_CHARGE, 1);
	private final ItemStack EMPED = new ItemStackBuilder(
			Material.SCULK_SENSOR)
			.unstackable()
			.name(getDisplayName().append(Component.text(" - ").append(Component.text("Brouillé", NamedTextColor.RED))))
			.lore(getLore())
			.build();
	
	protected static final LinkedList<Triplet<Double, Double, Double>> BALL = new LinkedList<Triplet<Double, Double, Double>>();
	protected static final LinkedList<Triplet<Double, Double, Double>> SPHERE = new LinkedList<Triplet<Double, Double, Double>>();
	protected static final Transformation TOP_TRANSFORMATION = new Transformation(
    		new Vector3f(0, 0, 0),
    		new AxisAngle4f((float) Math.PI, new Vector3f(0, 1, 0)),
            new Vector3f(1,1,1),
            new AxisAngle4f((float) Math.PI, new Vector3f(0, 1, 0))
    );
	
	//
	
	public RadarHandler() {
		super(Radar::new);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
		SpatialUtil.ball3DDensity(DETECTION_BLOCK_RANGE, 0.1f, SpatialUtil.SphereAlgorithm.FIBONACCI, 
				(offsetX, offsetY, offsetZ) -> {
					BALL.add(Triplet.from(offsetX, offsetY, offsetZ));
		});
		SpatialUtil.sphere3DDensity(DETECTION_BLOCK_RANGE, 0.1f, SpatialUtil.SphereAlgorithm.FIBONACCI,
				(offsetX, offsetY, offsetZ) -> {
					SPHERE.add(Triplet.from(offsetX, offsetY, offsetZ));
		});
	}
	
	//
	
	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		for(var tool : getTools()) {
			tool.tickCharge();
			tool.tickCooldown();
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
		if (EventUtil.isCustomAction(evt, CustomAction.HIT_ANY) && optionalTool.get().tryPlace()) {
			evt.setCancelled(true);
		}
	}
	@EventHandler
	private void onHandChange(PlayerItemHeldEvent evt) {
		var evtP = evt.getPlayer();
		ItemStack newHeld = evtP.getInventory().getItem(evt.getNewSlot());
		getTools().stream().filter(t -> evtP.equals(t.getOwner())).forEach(
				t -> t.setInHand(t.getItemStack().isSimilar(newHeld)));
	}

	//
	
	@Override
	protected void onAttachedToPlayer(Radar tool, Player player) {
	}
	@Override
	protected void onDetachFromPlayer(Radar tool, Player player) {
	}
	
	//
	
	public Component getDetectionMessage(int nbDetected) {
		String message = nbDetected>0 ? DETECTION_SUCCESS : DETECTION_FAIL;
		return Ioc.resolve(TextModule.class).message(Leaf.builder()
				.addStyle(Placeholder.unparsed("number", String.valueOf(nbDetected)))
				.addStyle(NekotineStyles.STANDART)
				.addLine(message)
				).buildFirst();
	}
	public double getDetectionRangeSquared() {
		return DETECTION_RANGE_SQUARED;
	}
	public int getDelayTick() {
		return DELAY_TICK;
	}
	public int getCooldownTick() {
		return COOLDOWN_TICK;
	}
	public ItemStack getUnplaced() {
		return UNPLACED;
	}
	public ItemStack getPlaced() {
		return PLACED;
	}
	public ItemStack getEmped() {
		return EMPED;
	}
}
