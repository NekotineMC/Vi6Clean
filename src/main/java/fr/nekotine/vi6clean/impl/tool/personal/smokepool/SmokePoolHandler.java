package fr.nekotine.vi6clean.impl.tool.personal.smokepool;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Random;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import fr.nekotine.core.inventory.ItemStackBuilder;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.status.effect.StatusEffect;
import fr.nekotine.core.status.effect.StatusEffectModule;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.ticking.TickTimeStamp;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.impl.status.effect.invisibility.TrueInvisibilityStatusEffectType;
import fr.nekotine.vi6clean.impl.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.tool.personal.minifier.MinifierHandler;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import net.kyori.adventure.text.format.TextDecoration;

@ToolCode("smokepool")
public class SmokePoolHandler extends ToolHandler<SmokePoolHandler.SmokePool>{ // TODO Git Submodules and composite build
	protected static final Random RNG = new Random();
	static {RNG.setSeed(System.currentTimeMillis());}
	
	private final double RADIUS = getConfiguration().getDouble("radius", 5); 
	private final double SQUARED_RADIUS = RADIUS * RADIUS;
	private final double AIR = getConfiguration().getDouble("air", 78.5398);
	private final double DIAMETER = getConfiguration().getDouble("diameter", 31.4159);
	private final int DURATION_TICK = (int)(20*getConfiguration().getDouble("duration", 8));
	private final int COOLDOWN_TICK = (int)(20*getConfiguration().getDouble("cooldown", 20));
	private final StatusEffect INVISIBLE = new StatusEffect(TrueInvisibilityStatusEffectType.get(), DURATION_TICK);
	private final ItemStack COOLDOWN_ITEM = new ItemStackBuilder(
			Material.GRAY_DYE)
			.name(getDisplayName().decorate(TextDecoration.STRIKETHROUGH))
			.lore(getLore())
			.flags(ItemFlag.values())
			.build();
		
	//
	
	public SmokePoolHandler() {
		super(SmokePool::new);
	}
	@Override
	protected void onAttachedToPlayer(SmokePool tool, Player player) {
	}
	@Override
	protected void onDetachFromPlayer(SmokePool tool, Player player) {
	}
	
	//
	
	private Collection<Player> inRange(SmokePool pool) {
		var team = Ioc.resolve(WrappingModule.class).getWrapper(pool.getOwner(), PlayerWrapper.class).ourTeam();

		return team.stream()
		.filter(ennemi -> ennemi.getLocation().distanceSquared(pool.getPlacedLocation()) <= SQUARED_RADIUS)
		.collect(Collectors.toCollection(LinkedList::new));
	}
	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		for(var tool : getTools()) {
			tool.tickCooldown();
			tool.tickParticle();
			
			if(evt.timeStampReached(TickTimeStamp.QuartSecond)) {
				//Copied from OmniCaptorHandler
				if(!tool.isPlaced()) {
					continue;
				}
				var inRange = inRange(tool);
				var oldInRange = tool.getInside();
				if (inRange.size() <= 0 && oldInRange.size() <= 0) {
					continue;
				}
				
				var ite = oldInRange.iterator();
				var statusModule = Ioc.resolve(StatusEffectModule.class);
				while (ite.hasNext()) {
					var p = ite.next();
					if (inRange.contains(p)) {
						inRange.remove(p);
					}else {
						statusModule.removeEffect(p, INVISIBLE);
						ite.remove();
					}
				}
				for (var p : inRange) {
					statusModule.addEffect(p, INVISIBLE);
					oldInRange.add(p);
				}
			}
		}
	}
	@EventHandler
	private void onPlayerInterract(PlayerInteractEvent evt) {
		if (evt.getHand() != EquipmentSlot.HAND && !EventUtil.isCustomAction(evt, CustomAction.HIT_ANY)) {
			return;
		}
		var statusModule = Ioc.resolve(StatusFlagModule.class);
		var player = evt.getPlayer();
		var tool = getToolFromItem(evt.getItem());
		if (tool == null || statusModule.hasAny(player, EmpStatusFlag.get()) || player.getCooldown(Material.FIREWORK_STAR) <= 0) {
			return;
		}
		// TODO HERE
		var ownerScale = (float) player.getAttribute(Attribute.SCALE).getValue();
		if (ownerScale != 1) {
			var scaleAttr = man.getAttribute(Attribute.SCALE);
			scaleAttr.addModifier(new AttributeModifier(MinifierHandler.SCALE_ATTRIBUTE_KEY,ownerScale-1,Operation.MULTIPLY_SCALAR_1));
		}
		evt.setCancelled(true);
		if (placed || cooldownLeft > 0) {
			return false;
		}
		if (Ioc.resolve(StatusFlagModule.class).hasAny(getOwner(), EmpStatusFlag.get())) {
			return false;
		}
		var handler = Ioc.resolve(SmokePoolHandler.class);
		life = handler.getDurationTick();
		placed = true;
		placedLoc = getOwner().getLocation();
		Vi6Sound.SMOKEPOOL.play(placedLoc.getWorld(), placedLoc);
		getOwner().setCooldown(handler.getItem().getType(), handler.getDurationTick());
		return true;
	}

	@Override
	protected void onAttachedToPlayer(SmokePool tool) {
	}
	
	@Override
	protected void onDetachFromPlayer(SmokePool tool) {
	}
	
	@Override
	protected void onToolCleanup(SmokePool tool) {
	}
	
	@Override
	protected ItemStack makeItem(SmokePool tool) {
		return new ItemStackBuilder(
				Material.FIREWORK_STAR)
				.name(getDisplayName())
				.lore(getLore())
				.flags(ItemFlag.values())
				.build();;
	}
	
	public static class SmokePool extends Tool{

		public SmokePool(ToolHandler<?> handler) {
			super(handler);
		}
		
	}
	
}
