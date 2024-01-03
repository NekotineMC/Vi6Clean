package fr.nekotine.vi6clean.impl.tool.personal.smokepool;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Random;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.inventory.ItemStackBuilder;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.status.effect.StatusEffect;
import fr.nekotine.core.status.effect.StatusEffectModule;
import fr.nekotine.core.ticking.TickTimeStamp;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.impl.status.effect.InvisibleStatusEffectType;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import net.kyori.adventure.text.format.TextDecoration;

@ToolCode("smokepool")
public class SmokePoolHandler extends ToolHandler<SmokePool>{
	protected static final Random RNG = new Random();
	static {RNG.setSeed(System.currentTimeMillis());}
	
	private final double RADIUS = getConfiguration().getDouble("radius", 5);
	private final double SQUARED_RADIUS = RADIUS * RADIUS;
	private final double AIR = getConfiguration().getDouble("air", 78.5398);
	private final double DIAMETER = getConfiguration().getDouble("diameter", 31.4159);
	private final int DURATION_TICK = (int)(20*getConfiguration().getDouble("duration", 8));
	private final int COOLDOWN_TICK = (int)(20*getConfiguration().getDouble("cooldown", 20));
	private final StatusEffect INVISIBLE = new StatusEffect(InvisibleStatusEffectType.get(), DURATION_TICK);
	private final ItemStack ITEM = new ItemStackBuilder(
			Material.FIREWORK_STAR)
			.name(getDisplayName())
			.lore(getLore())
			.flags(ItemFlag.values())
			.build();
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
		if (evt.getHand() != EquipmentSlot.HAND) {
			return;
		}
		var evtP = evt.getPlayer();
		var optionalTool = getTools().stream().filter(t -> evtP.equals(t.getOwner()) && t.getItemStack().isSimilar(evt.getItem())).findFirst();
		if (optionalTool.isEmpty()) {
			return;
		}
		if (EventUtil.isCustomAction(evt, CustomAction.HIT_ANY) && optionalTool.get().cast()) {
			evt.setCancelled(true);
		}
	}
	
	//
	
	public int getDurationTick() {
		return DURATION_TICK;
	}
	public int getCooldownTick() {
		return COOLDOWN_TICK;
	}
	public ItemStack getItem() {
		return ITEM;
	}
	public ItemStack getCooldownItem() {
		return COOLDOWN_ITEM;
	}
	public StatusEffect getEffect() {
		return INVISIBLE;
	}
	public double getRadius() {
		return RADIUS;
	}
	public double getAir() {
		return AIR;
	}
	public double getDiameter() {
		return DIAMETER;
	}
}
