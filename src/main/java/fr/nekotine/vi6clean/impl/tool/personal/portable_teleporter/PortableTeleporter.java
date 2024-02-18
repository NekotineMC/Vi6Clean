package fr.nekotine.vi6clean.impl.tool.personal.portable_teleporter;

import java.util.ArrayList;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.type.StructureBlock;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.util.EntityUtil;
import fr.nekotine.vi6clean.impl.tool.Tool;

public class PortableTeleporter extends Tool{
	private class TeleportationPad {
		protected static final boolean canPlace(Location location) {
			return location.clone().subtract(0, 0.1, 0).getBlock().isSolid();
		}
		private static final float CARPET_SIZE = 0.06f;
		
		private BlockDisplay display;
		private boolean teleporting;
		private int teleportationDelayTick;
		private int vfxDelayTick;
		
		protected TeleportationPad(Location location) {
			var padLoc = location.getBlock().getLocation()/*.subtract(0, TEMP, 0)*/;
			this.display = (BlockDisplay)padLoc.getWorld().spawnEntity(padLoc, EntityType.BLOCK_DISPLAY, SpawnReason.CUSTOM);
			var transformation = display.getTransformation();
			transformation.getScale().set(1, CARPET_SIZE, 1);
			var bd = (StructureBlock)Material.STRUCTURE_BLOCK.createBlockData();
			bd.setMode(StructureBlock.Mode.CORNER);
			display.setBlock(bd);
			display.setTransformation(transformation);
			
			//EntityUtil.fixLighting(display);
		}
		protected void teleport() {
			var handler = Ioc.resolve(PortableTeleporterHandler.class);
			teleporting = true;
			teleportationDelayTick = handler.getDelayTick();
			vfxDelayTick = handler.getVfxDelayTick();
			if(getOwner() != null)
				EntityUtil.freeze(getOwner());
		}
		protected void cleanup() {
			display.remove();
		}
		protected void tickTeleportation() {
			if(!teleporting) return;
			if(--teleportationDelayTick == 0) {
				teleporting = false;
				if(getOwner() != null) {
					var disLoc = display.getLocation().add(0.5,0,0.5);
					getOwner().teleport(disLoc);
					EntityUtil.unfreeze(getOwner());
				}
				//Sound here
			}
		}
		protected void tickVFX() {
			if(!teleporting) return;
			if(--vfxDelayTick == 0) {
				var handler = Ioc.resolve(PortableTeleporterHandler.class);
				
				var PLAYER_VFX = handler.getPlayerVFX();
				var PLAYER_VFX_1 = PLAYER_VFX.a();
				var PLAYER_VFX_2 = PLAYER_VFX.b();
				
				var PAD_VFX = handler.getPadVFX();
				var PAD_VFX_1 = PAD_VFX.a();
				var PAD_VFX_2 = PAD_VFX.b();
				
				var TELEPORTATION_DELAY_TICK = handler.getDelayTick();
				var vfxMin = Math.min(PLAYER_VFX_1.size(), PAD_VFX_1.size());				
				var vfxCount = Math.ceil(((double)(TELEPORTATION_DELAY_TICK - teleportationDelayTick) / TELEPORTATION_DELAY_TICK) * vfxMin);
				var disLoc = display.getLocation().add(0.5,0,0.5);
				for(int i=0 ; i < vfxCount ; i++) {
					
					var otherEndIndex = vfxMin - i - 1;
					PAD_VFX_1.get(otherEndIndex).accept(disLoc);
					PAD_VFX_2.get(otherEndIndex).accept(disLoc);
					
					if(getOwner() != null) {
						var ownLoc = getOwner().getLocation();
						PLAYER_VFX_1.get(i).accept(ownLoc);
						PLAYER_VFX_2.get(i).accept(ownLoc);
					}
				}	
				vfxDelayTick = handler.getVfxDelayTick();
			}
		}
		protected boolean isTeleporting() {
			return teleporting;
		}
	}
	
	private final ArrayList<TeleportationPad> pads = new ArrayList<TeleportationPad>();
	
	//
	
	@Override
	protected ItemStack makeInitialItemStack() {
		return Ioc.resolve(PortableTeleporterHandler.class).getItem();
	}
	@Override
	protected void cleanup() {
		pads.forEach(tp -> tp.cleanup());
	}
	@Override
	protected void onEmpStart() {
		setItemStack(Ioc.resolve(PortableTeleporterHandler.class).getEmpItem());
	}
	@Override
	protected void onEmpEnd() {
		setItemStack(Ioc.resolve(PortableTeleporterHandler.class).getItem());
	}
	
	//
	
	protected boolean tryPlace() {
		var handler = Ioc.resolve(PortableTeleporterHandler.class);
		if(pads.size() == handler.getCharges()) return false;
		
		var pLoc = getOwner().getLocation();
		if(!TeleportationPad.canPlace(pLoc)) return false;
		
		pads.add(new TeleportationPad(pLoc));
		return true;
	}
	protected boolean tryTeleport(int padIndex) {
		if(pads.stream().anyMatch(tp -> tp.isTeleporting())) return false;
		var pad = pads.get(padIndex);
		pad.teleport();
		return false;
	}
	protected void tick() {
		pads.forEach(tp -> 
		{tp.tickTeleportation();tp.tickVFX();});
	}
}
