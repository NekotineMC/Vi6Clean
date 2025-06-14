package fr.nekotine.vi6clean.voicechat;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class Vi6VoiceChatPlugin implements VoicechatPlugin {

    private Map<Player, AudioPlayer> audioPlayers = new HashMap<>();

    private Map<Player, short[]> audioData = new HashMap<>();

    private Random rand = new Random();

    private OpusDecoder decoder;

    @Override
    public String getPluginId() {
        return "vi6clean";
    }

    @Override
    public void initialize(VoicechatApi api) {
        VoicechatPlugin.super.initialize(api);
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        VoicechatPlugin.super.registerEvents(registration);
        registration.registerEvent(MicrophonePacketEvent.class, this::onMic);
    }

    private void onMic(MicrophonePacketEvent evt){
        // The connection might be null if the event is caused by other means
        if (evt.getSenderConnection() == null) {
            return;
        }
        var senderConnection = evt.getSenderConnection();
        // Cast the generic player object of the voice chat API to an actual bukkit player
        // This object should always be a bukkit player object on bukkit based servers
        if (!(senderConnection.getPlayer().getPlayer() instanceof Player player)) {
            return;
        }

        var group = evt.getSenderConnection().getGroup();

        // Check if the player sending the audio is actually in a group
        if (group != null) {
            return;
        }

        if (player.getInventory().getItemInMainHand().getType() != Material.LEATHER_HORSE_ARMOR){
            return;
        }

        var api = evt.getVoicechat();

        if (decoder == null){
            decoder = api.createDecoder();
        }

        // start effect

        var packet = evt.getPacket();
        var packetData = packet.getOpusEncodedData();
        var soundData = decoder.decode(packetData);
        //--
        // intermittent
        var severity = 5; // 0-100

        for (int i = 0; i < soundData.length; i++) {
            if (rand.nextFloat(100) < severity) { // 0-100
                soundData[i] *= 0;
            }
        }
        //LowQuality
        short[] result = new short[soundData.length];
        for (int i = 0; i < soundData.length; i += severity) {// 0-100
            int sum = 0;
            for (int j = 0; j < severity; j++) {
                sum += soundData[i + j];
            }
            int avg = sum / severity;
            for (int j = 0; j < severity; j++) {
                result[i + j] = (short) avg;
            }
        }
        //--

        // end effect

        var game = Ioc.resolve(Vi6Game.class);
        for (var p : game.getGuards()){
            if (p.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            var connection = api.getConnectionOf(p.getUniqueId());
            // Check if the player is actually connected to the voice chat
            if (connection == null) {
                continue;
            }

            // Send a static audio packet of the microphone data to the connection of each player
            audioData.put(p,result);
            var audioPlayer = audioPlayers.computeIfAbsent(p, pl -> api.createAudioPlayer(
                    api.createStaticAudioChannel(p.getUniqueId(),connection.getPlayer().getServerLevel(),connection),
                    api.createEncoder(),
                    () -> {
                        var a = audioData.get(pl);
                        if (a == null){
                            audioPlayers.get(pl).stopPlaying();
                            audioPlayers.remove(pl);
                        }
                        audioData.remove(pl);
                        return a;
                    }
            ));
            if (!audioPlayer.isPlaying()){
                audioPlayer.startPlaying();
            }
            //api.sendStaticSoundPacketTo(connection, packet.staticSoundPacketBuilder().build());
        }
    }

    public static short[] combineAudio(List<short[]> audioParts) {
        short[] result = new short[960];
        int sample;
        for (int i = 0; i < result.length; i++) {
            sample = 0;
            for (short[] audio : audioParts) {
                if (audio == null) {
                    sample += 0;
                } else {
                    sample += audio[i];
                }
            }
            if (sample > Short.MAX_VALUE) {
                result[i] = Short.MAX_VALUE;
            } else if (sample < Short.MIN_VALUE) {
                result[i] = Short.MIN_VALUE;
            } else {
                result[i] = (short) sample;
            }
        }
        return result;
    }

}
