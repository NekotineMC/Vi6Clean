package fr.nekotine.vi6clean.impl.tool.personal;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import fr.nekotine.core.inventory.ItemStackBuilder;
import fr.nekotine.core.util.EntityUtil;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.vi6clean.impl.tool.Tool;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class Radar extends Tool{
	private final ItemStack UNPLACED = new ItemStackBuilder(Material.LIGHTNING_ROD)
			.unstackable()
			.name(Component.text("Radar", NamedTextColor.GOLD))
			.lore(RadarHandler.LORE)
			.build();
	private final ItemStack PLACED = ItemStackUtil.addEnchant(UNPLACED.clone(), Enchantment.QUICK_CHARGE, 1);
	private boolean placed = false;
	private boolean sneaking = false;
	private BlockDisplay bottom;
	private BlockDisplay mid;
	private BlockDisplay top;
	
	//
	
	@Override
	protected ItemStack makeInitialItemStack() {
		return ItemStackUtil.make(Material.LIGHTNING_ROD, Component.text("Radar", NamedTextColor.GOLD), RadarHandler.LORE);
	}
	@Override
	protected void cleanup() {
		if(placed) {
			bottom.remove();
			mid.remove();
			top.remove();
		}
	}
	
	//
	
	protected boolean tryPlace() {
		Location ploc = getOwner().getLocation();
		
		if(placed || !EntityUtil.IsOnGround(getOwner()) || ploc.getBlock().getType().isSolid() || ploc.add(0, 1, 0).getBlock().getType().isSolid())
			return false;
		placed = true;
			
		bottom = (BlockDisplay)getOwner().getWorld().spawnEntity(ploc.subtract(0, 0.5, 0), EntityType.BLOCK_DISPLAY);
		mid = (BlockDisplay)getOwner().getWorld().spawnEntity(ploc.add(0, 1, 0), EntityType.BLOCK_DISPLAY);
		top = (BlockDisplay)getOwner().getWorld().spawnEntity(ploc.add(0,1.925,0), EntityType.BLOCK_DISPLAY);

		float y = getOwner().getEyeLocation().getYaw();
		bottom.setRotation(y + 180, 0);
		mid.setRotation(y + 180, 0);
		top.setRotation(y + 180, 0);
		
		bottom.setBlock(Material.ORANGE_SHULKER_BOX.createBlockData());;
		mid.setBlock(Material.LIGHTNING_ROD.createBlockData());
		top.setBlock(Material.HEAVY_WEIGHTED_PRESSURE_PLATE.createBlockData());
		Transformation centered = new Transformation(
				new Vector3f(-0.5f, -0.5f, -0.5f),
				new Quaternionf(0,0,0,1), 
				new Vector3f(1,1,1),
				new Quaternionf(0, 0, 0, 1));

		bottom.setTransformation(centered);
		mid.setTransformation(centered);
		top.setTransformation(new Transformation(
				new Vector3f(-0.5f, -1.1f, -0.5f),
				new Quaternionf(0.35f, 0, 0, 0.937f), 
				new Vector3f(1, 2.2f, 1),
				new Quaternionf(0, 0, 0, 1)));
		
		
		//Faire un son
		
		getOwner().setCooldown(getItemStack().getType(), SonarHandler.DELAY_SECOND*20);
		
		updateItem();
		
		return true;
	}
	protected void updateItem() {
		if(placed) {
			setItemStack(PLACED);
		}else {
			setItemStack(UNPLACED);
		}
	}
	protected void detect() {
		//Animation + detection team ennemi + bruit
		if(placed) {
			placed = false;
			bottom.remove();
			mid.remove();
			top.remove();
			updateItem();
		}
	}
	protected void tickParticle() {
		if(placed || sneaking) {
			//Fait un cercle de particules
		}
		
	}
	protected boolean isPlaced() {
		return placed;
	}
	protected void setSneaking(boolean sneaking) {
		this.sneaking = sneaking;
	}
}
