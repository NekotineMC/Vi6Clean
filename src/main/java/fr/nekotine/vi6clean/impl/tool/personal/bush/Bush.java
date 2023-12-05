package fr.nekotine.vi6clean.impl.tool.personal.bush;

import org.bukkit.Material;
import org.bukkit.configuration.Configuration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.status.effect.StatusEffect;
import fr.nekotine.core.status.effect.StatusEffectModule;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.vi6clean.constant.Vi6ToolLoreText;
import fr.nekotine.vi6clean.impl.status.effect.InvisibleStatusEffectType;
import fr.nekotine.vi6clean.impl.tool.Tool;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class Bush extends Tool{
	
	private boolean inBush;
	
	private boolean revealed;
	
	private BukkitTask fadeOffTask;
	
	private final int fadeOffDelay = Ioc.resolve(Configuration.class).getInt("tool.bush.fadeoff", 40);
	
	private final StatusEffect unlimitedInvisibility = new StatusEffect( InvisibleStatusEffectType.get(), -1);
	
	private final StatusEffect fadeoffInvisibility = new StatusEffect(InvisibleStatusEffectType.get(),fadeOffDelay);
	
	private final ItemStack VISIBLE_ITEM = ItemStackUtil.make(Material.WHITE_STAINED_GLASS_PANE,
			Component.text("Buisson furtif - ",NamedTextColor.GOLD).append(Component.text("Visible", NamedTextColor.WHITE)),
			Vi6ToolLoreText.BUSH.make());
	
	private final ItemStack INVISIBLE_ITEM = ItemStackUtil.make(Material.GLASS_PANE,
			Component.text("Buisson furtif - ",NamedTextColor.GOLD).append(Component.text("Invisible", NamedTextColor.GRAY)),
			Vi6ToolLoreText.BUSH.make());
	
	private final ItemStack REVEALED_ITEM = ItemStackUtil.make(Material.RED_STAINED_GLASS_PANE,
			Component.text("Buisson furtif - ",NamedTextColor.GOLD).append(Component.text("Découvert", NamedTextColor.RED)),
			Vi6ToolLoreText.BUSH.make());
	
	@Override
	protected ItemStack makeInitialItemStack() {
		return VISIBLE_ITEM;
	}
	
	public void setInBush(boolean inBush) {
		var owner = getOwner();
		if (this.inBush == inBush || revealed || owner == null) {
			return;
		}
		var statusEffectModule = Ioc.resolve(StatusEffectModule.class);
		if (inBush) {
			setItemStack(INVISIBLE_ITEM);
			statusEffectModule.addEffect(owner, unlimitedInvisibility);
		}else {
			statusEffectModule.addEffect(owner, fadeoffInvisibility);
			statusEffectModule.removeEffect(owner, unlimitedInvisibility);
			if (fadeOffTask != null) {
				fadeOffTask.cancel();
			}
			fadeOffTask = new BukkitRunnable() {
				
				@Override
				public void run() {
					if (revealed) {
						setItemStack(REVEALED_ITEM);
						return;
					}
					if (!inBush) {
						setItemStack(VISIBLE_ITEM);
					}
				}
			}.runTaskLater(Ioc.resolve(JavaPlugin.class), fadeOffDelay);
			owner.setCooldown(getItemStack().getType(), fadeOffDelay);
		}
		this.inBush = inBush;
	}
	
	public void setRevealed(boolean revealed) {
		var owner = getOwner();
		if (this.revealed == revealed || owner == null) {
			return;
		}
		if (revealed) {
			var statusEffectModule = Ioc.resolve(StatusEffectModule.class);
			statusEffectModule.removeEffect(owner, unlimitedInvisibility);
			statusEffectModule.removeEffect(owner, fadeoffInvisibility);
			setItemStack(REVEALED_ITEM);
			if (fadeOffTask != null) {
				fadeOffTask.cancel();
				fadeOffTask = null;
			}
		}else{
			if (inBush) {
				setItemStack(INVISIBLE_ITEM);
				var statusEffectModule = Ioc.resolve(StatusEffectModule.class);
				statusEffectModule.addEffect(owner, unlimitedInvisibility);
			}else {
				setItemStack(VISIBLE_ITEM);
			}
		}
		this.revealed = revealed;
	}
	
	@Override
	protected void cleanup() {
		var owner = getOwner();
		if (owner == null) {
			return;
		}
		var statusEffectModule = Ioc.resolve(StatusEffectModule.class);
		statusEffectModule.removeEffect(owner, unlimitedInvisibility);
		statusEffectModule.removeEffect(owner, fadeoffInvisibility);
	}
}
