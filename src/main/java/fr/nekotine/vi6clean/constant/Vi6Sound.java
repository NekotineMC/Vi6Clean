package fr.nekotine.vi6clean.constant;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.Sound.Emitter;

/**
 * Sons du Vi6
 * 
 * @author XxGoldenbluexX
 *
 */
public enum Vi6Sound {

	// TODO Normaliser les catégories sonores (Sound.Source)
	
	SCANNER_SCAN(new Sound[] {
		Sound.sound(Key.key("block.beacon.power_select"), Sound.Source.VOICE, 1, 1)
	}),
	INVISNEAK_REVEALED(new Sound[] {
		Sound.sound(Key.key("block.lava.extinguish"), Sound.Source.MASTER, 0.1f, 2)
	}),
	OMNICAPTEUR_PLACE(new Sound[] {
	Sound.sound(Key.key("entity.vex.hurt"), Sound.Source.MASTER, 2, 0.1F),
	Sound.sound(Key.key("item.flintandsteel.use"), Sound.Source.MASTER, 2, 0.1F)
	}),
	OMNICAPTEUR_PICKUP(new Sound[] {
	Sound.sound(Key.key("block.ladder.hit"), Sound.Source.MASTER, 0.8f, 2F),
	Sound.sound(Key.key("block.note_block.hat"), Sound.Source.MASTER, 1f, 0.1F)
	}),
	OMNICAPTEUR_DETECT(new Sound[] {
	Sound.sound(Key.key("block.note_block.bell"), Sound.Source.MASTER, 0.3f, 0.1F),
	Sound.sound(Key.key("block.note_block.cow_bell"), Sound.Source.MASTER, 2, 0.5F),
	Sound.sound(Key.key("block.note_block.bass"), Sound.Source.MASTER, 2, 0.1F)
	});
	
	private Sound[] sounds;
	
	private Vi6Sound(Sound[] sounds) {
		this.sounds = sounds;
	}
	
	public void play(Audience audience) {
		for (var s : sounds) {
			audience.playSound(s);
		}
	}
	
	public void play(Audience audience, Emitter emitter) {
		for (var s : sounds) {
			audience.playSound(s, emitter);
		}
	}
	
	public void play(Audience audience, double x, double y, double z) {
		for (var s : sounds) {
			audience.playSound(s, x, y, z);
		}
	}
	
	public void play(Audience audience, Location location) {
		for (var s : sounds) {
			audience.playSound(s, location.getX(), location.getY(), location.getZ());
		}
	}
	
	public void play(Audience audience, Vector vector) {
		for (var s : sounds) {
			audience.playSound(s, vector.getX(), vector.getY(), vector.getZ());
		}
	}
	
}
