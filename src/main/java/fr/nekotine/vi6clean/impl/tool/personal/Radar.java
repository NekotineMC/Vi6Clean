package fr.nekotine.vi6clean.impl.tool.personal;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.joml.Matrix4f;

import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.vi6clean.impl.tool.Tool;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class Radar extends Tool{
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
		if(placed || !ploc.subtract(0, 0.1, 0).getBlock().getType().isSolid() || !ploc.add(0, 0.9, 0).getBlock().getType().isSolid())
			return false;
		
		placed = true;
			
		bottom = (BlockDisplay)getOwner().getWorld().spawnEntity(ploc, EntityType.BLOCK_DISPLAY);
		mid = (BlockDisplay)getOwner().getWorld().spawnEntity(ploc.add(0, 1, 0), EntityType.BLOCK_DISPLAY);
		top = (BlockDisplay)getOwner().getWorld().spawnEntity(ploc.add(0,1.7,0), EntityType.BLOCK_DISPLAY);

		bottom.setBlock(Material.ORANGE_SHULKER_BOX.createBlockData());;
		mid.setBlock(Material.LIGHTNING_ROD.createBlockData());
		top.setBlock(Material.HEAVY_WEIGHTED_PRESSURE_PLATE.createBlockData());
		top.setTransformationMatrix(new Matrix4f(1f,0f,0f,0f,0f,1.924f,-0.485f,0f,0f,1.067f,0.875f,0f,0f,0f,0f,1f));
		
		//Faire un son
		
		getOwner().setCooldown(getItemStack().getType(), SonarHandler.DELAY_SECOND*20);
		
		updateItem();
		
		return true;
	}
	protected void updateItem() {
		if(placed) {
			getItemStack().addUnsafeEnchantment(Enchantment.QUICK_CHARGE, 0);
		}else {
			getItemStack().removeEnchantment(Enchantment.QUICK_CHARGE);
		}
	}
	protected void detect() {
		//Animation + detection team ennemi + bruit
		
		placed = false;
		bottom.remove();
		mid.remove();
		top.remove();
		updateItem();
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
