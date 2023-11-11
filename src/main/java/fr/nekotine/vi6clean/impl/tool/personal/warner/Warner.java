package fr.nekotine.vi6clean.impl.tool.personal.warner;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import fr.nekotine.core.NekotineCore;
import fr.nekotine.core.util.SpatialUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.impl.map.artefact.Artefact;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import net.kyori.adventure.text.Component;

public class Warner extends Tool{
	private WarnerHandler handler;
	private boolean placed = false;
	private Artefact watched;
	private int warn_delay = 0;
	private boolean inHand = false;
	private boolean sneaking = false;
	private int n = 0;
	private ItemDisplay eye_item1;
	private ItemDisplay eye_item2;
	
	//
	
	protected boolean place(WarnerHandler handler) {
		this.handler = handler;
		if(placed)
			return false;
		
		var art = handler.getCloseArtefact(getOwner().getLocation());
		if(art.isEmpty())
			return false;
		
		watched = art.get();
		var watchedLocation = watched.getBlockPosition().toLocation(getOwner().getWorld());
		eye_item1 = (ItemDisplay)getOwner().getWorld().spawnEntity(watchedLocation.clone().add(0.5, 0.5, 0.5), EntityType.ITEM_DISPLAY);
		eye_item1.setItemStack(new ItemStack(Material.ENDER_EYE));
		eye_item1.setTransformation(getTransform(n));
		eye_item2 = (ItemDisplay)getOwner().getWorld().spawnEntity(watchedLocation.clone().add(0.5, 0.5, 0.5), EntityType.ITEM_DISPLAY);
		eye_item2.setItemStack(new ItemStack(Material.ENDER_EYE));
		eye_item2.setTransformation(getTransform(n + 4));
		
		Vi6Sound.WARNER_POSE.play(watchedLocation.getWorld(), watchedLocation);
		
		setItemStack(WarnerHandler.PLACED(watched.getName()));
		
		placed = true;
		return true;
	}
	protected void tickWarning() {
		if(placed && watched.isCaptured() && ++warn_delay >= WarnerHandler.WARN_DELAY_SECOND * 20) {
			Component message = WarnerHandler.BUILD_WARN_MESSAGE(watched.getName());
			NekotineCore.MODULES.get(WrappingModule.class).getWrapper(getOwner(), PlayerWrapper.class).ourTeam().forEach(
				p -> {Vi6Sound.WARNER_TRIGGER.play(p); p.sendMessage(message);}
			);
			cleanup();
			handler.detachFromOwner(this);
			handler.remove(this);
		}
	}
	protected void tickDisplay() {
		if(placed) {
			n = (n+1) % 8;
			eye_item1.setInterpolationDelay(0);
			eye_item1.setInterpolationDuration(11);
			eye_item1.setTransformation(getTransform(n));	
			eye_item2.setInterpolationDelay(0);
			eye_item2.setInterpolationDuration(11);
			eye_item2.setTransformation(getTransform(n + 4));
		}
	}
	protected void tickParticles() {
		if(!placed && inHand && sneaking) {
			var loc = getOwner().getLocation();
			var x = loc.getX();
			var y = loc.getY();
			var z = loc.getZ();
			SpatialUtil.circle2DDensity(WarnerHandler.PLACE_RANGE, 5, 0,(offsetX, offsetZ) -> {
			getOwner().spawnParticle(Particle.FIREWORKS_SPARK, x + offsetX, y, z + offsetZ, 1, 0, 0, 0, 0, null);});
		}
	}
	protected void setInHand(boolean inHand) {
		this.inHand = inHand;
	}
	protected  void setSneaking(boolean sneaking) {
		this.sneaking = sneaking;
	}
	protected Artefact getWatched() {
		return watched;
	}
	
	//
	
	private Transformation getTransform(int n) {
		float angle = (float)(n * Math.PI / 4);
		return new Transformation(
			new Vector3f((float)Math.sin(angle) * WarnerHandler.DISPLAY_DISTANCE, 0, (float)Math.cos(angle) * WarnerHandler.DISPLAY_DISTANCE), 
			new AxisAngle4f(angle, new Vector3f(0,1,0)), 
			new Vector3f(WarnerHandler.DISPLAY_SCALE, WarnerHandler.DISPLAY_SCALE, WarnerHandler.DISPLAY_SCALE),
			new AxisAngle4f());
	}
	
	//
	
	@Override
	protected ItemStack makeInitialItemStack() {
		return WarnerHandler.UNPLACED();
	}
	
	//

	@Override
	protected void cleanup() {
		if(placed) {
			eye_item1.remove();
			eye_item2.remove();
		}
	}
}
