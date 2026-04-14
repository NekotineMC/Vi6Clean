package fr.nekotine.vi6clean.impl.tool.personal.tazer;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
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
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EntityUtil;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.util.InventoryUtil;
import fr.nekotine.core.util.SpatialUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.impl.status.effect.TazedStatusEffectType;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpEndEvent;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpStartEvent;
import fr.nekotine.vi6clean.impl.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.SwingAnimation;
import io.papermc.paper.datacomponent.item.SwingAnimation.Animation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

@ToolCode("tazer")
public class TazerHandler extends ToolHandler<TazerHandler.Tazer>{

	private final int COOLDOWN_TICK = (int)(20*getConfiguration().getDouble("cooldown", 10));
	
	private final int TAZED_DURATION_TICK = (int)(20*getConfiguration().getDouble("tazed_duration", 1));
	
	private final StatusEffect tazedEffect = new StatusEffect(TazedStatusEffectType.get(), TAZED_DURATION_TICK);
	
	public TazerHandler() {
		super(Tazer::new);
	}
	
	@EventHandler
	private void onPlayerInterract(PlayerInteractEvent evt) {
		if (evt.getHand() != EquipmentSlot.HAND || !EventUtil.isCustomAction(evt, CustomAction.HIT_ANY)) {
			return;
		}
		var player = evt.getPlayer();
		var statusFlagModule = Ioc.resolve(StatusFlagModule.class);
		var tool = getToolFromItem(evt.getItem());
		if (tool == null || player.getCooldown(NamespacedKey.fromString(getToolCode()+'/'+tool.getId(), Ioc.resolve(JavaPlugin.class))) > 0 || statusFlagModule.hasAny(player, EmpStatusFlag.get())) {
			return;
		}
		// SHOT
		
		var optWrap = Ioc.resolve(WrappingModule.class).getWrapperOptional(player, PlayerWrapper.class);
		if (optWrap.isEmpty()) {
			return;
		}
		var eyeLoc = player.getEyeLocation();
		var eyeDir = eyeLoc.getDirection();
		var world = player.getWorld();
		var range = 100d;
		var trace = world.rayTrace(eyeLoc, eyeDir, range, FluidCollisionMode.NEVER, true,1.0, e -> !e.equals(player) && e instanceof LivingEntity);
		if (trace == null) {
			return; // No hit, le joueur à tiré en l'air
		}
		var hite = trace.getHitEntity();
		if (hite != null && hite instanceof LivingEntity hit) {
			EntityUtil.fakeDamage(hit);
			if (optWrap.get().ennemiTeamInMap().anyMatch(e -> e.equals(hit))) {
				Ioc.resolve(StatusEffectModule.class).addEffect(hit, tazedEffect);
			}
		}
		var hitp = trace.getHitPosition();
		if (hitp != null) {
			range = hitp.distance(eyeLoc.toVector());
		}
		SpatialUtil.line3DFromDir(eyeLoc.toVector(), eyeLoc.getDirection(), range, 4,
				(vec) -> world.spawnParticle(Particle.FIREWORK, vec.getX(), vec.getY(), vec.getZ(), 0,
						0, 0, 0, 0f));
		player.setCooldown(NamespacedKey.fromString(getToolCode()+'/'+tool.getId(), Ioc.resolve(JavaPlugin.class)), COOLDOWN_TICK);
		
		evt.setCancelled(true);
	}

	@Override
	protected void onAttachedToPlayer(Tazer tool) {
	}

	@Override
	protected void onDetachFromPlayer(Tazer tool) {
	}

	@Override
	protected void onToolCleanup(Tazer tool) {
	}

	@Override
	protected ItemStack makeItem(Tazer tool) {
		return new ItemStackBuilder(
				Material.SHEARS)
				.name(getDisplayName())
				.lore(getLore())
				.flags(ItemFlag.values())
				.postApply(item -> item.setData(DataComponentTypes.SWING_ANIMATION, SwingAnimation.swingAnimation().type(Animation.NONE)))
				.build();
	}
	
	@EventHandler
	private void onEmpStart(EntityEmpStartEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				item.setData(DataComponentTypes.ITEM_MODEL, Material.IRON_INGOT.key());
				item.editMeta(m -> m.displayName(getDisplayName().decorate(TextDecoration.STRIKETHROUGH).append(Component.text(" - ")).append(Component.text("Brouillé" , NamedTextColor.RED))));
			});
		}
	}
	
	@EventHandler
	private void onEmpStop(EntityEmpEndEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				item.resetData(DataComponentTypes.ITEM_MODEL); // back to default model
				item.editMeta(m -> m.displayName(getDisplayName()));
			});
		}
	}
	
	public static class Tazer extends Tool{

		public Tazer(ToolHandler<?> handler) {
			super(handler);
		}
		
	}
	
}
