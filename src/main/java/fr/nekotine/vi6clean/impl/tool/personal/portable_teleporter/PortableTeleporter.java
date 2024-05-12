package fr.nekotine.vi6clean.impl.tool.personal.portable_teleporter;

import java.util.ArrayList;

import fr.nekotine.core.glow.EntityGlowModule;
import fr.nekotine.core.glow.TeamColor;
import fr.nekotine.core.util.EventUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.type.StructureBlock;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.vi6clean.impl.tool.Tool;
import org.bukkit.util.Vector;

public class PortableTeleporter extends Tool {
	private class TeleportationPad implements Listener{
		protected static boolean canPlace(Location location) {
			return location.clone().subtract(0, 0.1, 0).getBlock().isSolid();
		}
		private static final float CARPET_SIZE = 0.06f;
		
		private final BlockDisplay display;
		private boolean teleporting;
		private final Vector eyeDirection;
		private int teleportationDelayTick;
		private int vfxDelayTick;
		
		protected TeleportationPad(Location location, Vector eyeDirection) {
			var padLoc = location.getBlock().getLocation()/*.subtract(0, TEMP, 0)*/;
			this.display = (BlockDisplay)padLoc.getWorld().spawnEntity(padLoc, EntityType.BLOCK_DISPLAY, SpawnReason.CUSTOM);
			this.eyeDirection = eyeDirection;

			var transformation = display.getTransformation();
			var scale = transformation.getScale();
			scale.set(1, CARPET_SIZE, 1);

			var bd = (StructureBlock)Material.STRUCTURE_BLOCK.createBlockData();
			bd.setMode(StructureBlock.Mode.CORNER);
			display.setBlock(bd);
			display.setTransformation(transformation);

			EventUtil.register(this);
			//EntityUtil.fixLighting(display);
		}
		protected void teleport() {
			var handler = Ioc.resolve(PortableTeleporterHandler.class);
			teleporting = true;
			teleportationDelayTick = handler.getDelayTick();
			vfxDelayTick = handler.getVfxDelayTick();
			if (getOwner() != null) {
				getOwner().setAllowFlight(true);
				getOwner().teleport(getOwner().getLocation().add(0,0.1,0));
				getOwner().setFlying(true);
				getOwner().setFlySpeed(0);
				getOwner().setVelocity(new Vector(0,0,0));
			}

		}

		@EventHandler
		public void onToggleFlight(PlayerToggleFlightEvent evt) {
			if(evt.getPlayer().equals(getOwner()) && teleporting && !evt.isFlying()) {
				getOwner().setVelocity(new Vector(0,0,0));
				evt.setCancelled(true);
			}
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
					disLoc.setDirection(eyeDirection);
					getOwner().teleport(disLoc);
					getOwner().setCooldown(getItemStack().getType(), Ioc.resolve(PortableTeleporterHandler.class).getCooldownTick());
					getOwner().setAllowFlight(false);
					getOwner().setFlying(false);
					getOwner().setFlySpeed(0.1F);
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

		protected void glow(){
			var color = this.equals(targeted) ? TeamColor.AQUA : TeamColor.DARK_PURPLE;
			Ioc.resolve(EntityGlowModule.class).glowEntityFor(display, getOwner(), color);
		}

		protected void unglow() {
			Ioc.resolve(EntityGlowModule.class).unglowEntityFor(display, getOwner());
		}

		protected BlockDisplay getDisplay() {
			return display;
		}
	}
	
	private final ArrayList<TeleportationPad> pads = new ArrayList<>();
	private TeleportationPad targeted;
	
	//
	
	@Override
	protected ItemStack makeInitialItemStack() {
		return Ioc.resolve(PortableTeleporterHandler.class).getItem(Ioc.resolve(PortableTeleporterHandler.class).getCharges());
	}
	@Override
	protected void cleanup() {
		pads.forEach(TeleportationPad::cleanup);
	}
	@Override
	protected void onEmpStart() {
		setItemStack(Ioc.resolve(PortableTeleporterHandler.class).getEmpItem());
	}
	@Override
	protected void onEmpEnd() {
		var handler = Ioc.resolve(PortableTeleporterHandler.class);
		setItemStack(handler.getItem(handler.getCharges() - pads.size()));
	}
	
	//
	
	protected boolean tryPlace() {
		var handler = Ioc.resolve(PortableTeleporterHandler.class);
		if(pads.size() == handler.getCharges()) return false;
		
		var pLoc = getOwner().getLocation();
		if(!TeleportationPad.canPlace(pLoc)) return false;
		
		pads.add(new TeleportationPad(pLoc, getOwner().getEyeLocation().getDirection()));
		setItemStack(handler.getItem(handler.getCharges() - pads.size()));
		return true;
	}
	protected boolean tryTeleport() {
		if(pads.stream().anyMatch(TeleportationPad::isTeleporting)) return false;
		if (targeted==null) return false;

		targeted.teleport();
		return true;
	}

	private boolean isTargeted(TeleportationPad pad) {
		var eyeLoc = getOwner().getEyeLocation();
		var start = eyeLoc.toVector();
		var dir = eyeLoc.getDirection();

		var bb = pad.getDisplay().getBoundingBox();
		var scale = pad.getDisplay().getTransformation().getScale();
		var x1= bb.getMinX();
		var y1= bb.getMinY();
		var z1= bb.getMinZ();

		return bb.resize(x1-0.25,y1-0.5,z1-0.25,x1+scale.x+0.25, y1+0.5, z1+scale.z+0.25)
				.rayTrace(start, dir, 100.0) != null;
	}
	protected void tick() {
		boolean check =
				(getOwner() != null) &&
				(getOwner().getInventory().getItemInMainHand().isSimilar(getItemStack())
						|| getOwner().getInventory().getItemInOffHand().isSimilar(getItemStack()));

		this.targeted = check ?
				pads.stream()
						.filter(this::isTargeted)
						.min((o1, o2) -> (int)
                        		(o1.getDisplay().getLocation().distanceSquared(getOwner().getLocation())
								- o2.getDisplay().getLocation().distanceSquared(getOwner().getLocation())))
						.orElse(null)
				: null;

		for (var tp : pads){
			tp.tickTeleportation();
			tp.tickVFX();

			if (check) {
				tp.glow();
			}else {
				tp.unglow();
			}
		}
	}
}
