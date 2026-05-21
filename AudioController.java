import javax.sound.sampled.*;
import java.io.File;

public class AudioController {
    private volatile Clip currentClip;
    public boolean clipPaused = false;
    private float savedVolume = 0.1f;

    private Runnable onSongEnd = null;

    public void setOnSongEnd(Runnable callback) {
        this.onSongEnd = callback;
    }

    public Clip getcurrentClip() {
        return currentClip;
    }

    /**
     * Closes the current clip safely, fully releasing the OS audio line.
     * This must be called whenever playback ends (naturally or manually) so
     * other apps (browsers, etc.) can acquire the audio device.
     */
    private void closeCurrentClip() {
        Clip clip = currentClip;
        if (clip == null) return;
        try {
            if (clip.isRunning()) clip.stop();
            clip.flush();
            clip.close();
        } catch (Exception ignored) {}
    }

    /**
     * Stops playback and immediately releases the audio line so other
     * applications (e.g. browsers playing YouTube/Instagram) can use it.
     */
    public void stop() {
        clipPaused = false;
        closeCurrentClip();
        currentClip = null;
        System.out.println("Audio stopped and line released");
    }

    public void playSound(String filePath) {
        Thread audioThread = new Thread(() -> {
            try {
                if (!filePath.endsWith(".wav")) {
                    System.out.println("Unsupported audio format. Please use .wav files.");
                    return;
                }

                float currentVolume = savedVolume;
                if (currentClip != null && currentClip.isOpen()) {
                    currentVolume = getVolume();
                    savedVolume = currentVolume;
                }
                closeCurrentClip();
                currentClip = null;

                AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(new File(filePath));
                AudioFormat format = audioInputStream.getFormat();

                // Convert non-PCM formats (e.g. ulaw/alaw) so Java can play them
                if (!format.getEncoding().equals(AudioFormat.Encoding.PCM_SIGNED)) {
                    AudioFormat pcm = new AudioFormat(
                            AudioFormat.Encoding.PCM_SIGNED,
                            format.getSampleRate(),
                            16,
                            format.getChannels(),
                            format.getChannels() * 2,
                            format.getSampleRate(),
                            false);
                    audioInputStream = AudioSystem.getAudioInputStream(pcm, audioInputStream);
                    format = pcm;
                }

                DataLine.Info info = new DataLine.Info(Clip.class, format);
                if (!AudioSystem.isLineSupported(info)) {
                    System.out.println("Audio line not supported for this format.");
                    return;
                }

                Clip clip;
                try {
                    clip = (Clip) AudioSystem.getLine(info);
                } catch (LineUnavailableException ex) {
                    // Another app is holding the audio device — wait and retry once
                    System.out.println("Audio line busy, retrying in 500ms…");
                    Thread.sleep(500);
                    clip = (Clip) AudioSystem.getLine(info);
                }

                currentClip = clip;
                currentClip.open(audioInputStream);

                setVolume(currentVolume);

                currentClip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP) {
                        Clip c = (Clip) event.getLine();
                        if (!clipPaused && c.getMicrosecondPosition() >= c.getMicrosecondLength() - 10_000) {
                            System.out.println("Audio finished playing naturally");
                            // ── FIX: close the clip so the OS audio line is released
                            // immediately after the song ends. Without this, the audio
                            // device stays locked and other apps (browsers, etc.) cannot
                            // play any sound until this player is closed.
                            closeCurrentClip();
                            currentClip = null;
                            if (onSongEnd != null) onSongEnd.run();
                        }
                    }
                });

                currentClip.start();
                clipPaused = false;
                System.out.println("Audio started playing");

            } catch (LineUnavailableException ex) {
                System.out.println("Audio line unavailable after retry: " + ex.getMessage());
            } catch (Exception ex) {
                System.out.println("Error playing sound: " + ex.getMessage());
                ex.printStackTrace();
            }
        }, "audio-player-thread");

        audioThread.setDaemon(true);
        audioThread.start();
    }

    public void pause() {
        if (currentClip != null && currentClip.isRunning()) {
            currentClip.stop();
            clipPaused = true;
        }
    }

    public void resume() {
        if (currentClip != null) {
            currentClip.start();
            clipPaused = false;
        }
    }

    public void setPosition(long microseconds) {
        if (currentClip != null && currentClip.isOpen()) {
            long maxPosition = currentClip.getMicrosecondLength();
            if (microseconds < 0) microseconds = 0;
            else if (microseconds > maxPosition) microseconds = maxPosition;

            float savedVolume = getVolume();
            setVolume(0.0001f);
            currentClip.setMicrosecondPosition(microseconds);
            setVolume(savedVolume);

            if (!clipPaused && !currentClip.isRunning()) {
                currentClip.start();
            }
        }
    }

    public float getVolume() {
        if (currentClip != null && currentClip.isOpen()) {
            try {
                FloatControl gainControl = (FloatControl) currentClip.getControl(FloatControl.Type.MASTER_GAIN);
                return (float) Math.pow(10f, gainControl.getValue() / 20f);
            } catch (IllegalArgumentException ignored) {}
        }
        return 0.1f;
    }

    public void setVolume(float volume) {
        volume = Math.max(0.0001f, Math.min(volume, 0.3f));
        savedVolume = volume;
        if (currentClip != null && currentClip.isOpen()) {
            try {
                FloatControl gainControl = (FloatControl) currentClip.getControl(FloatControl.Type.MASTER_GAIN);
                gainControl.setValue(20f * (float) Math.log10(volume));
            } catch (IllegalArgumentException ignored) {}
        }
    }
}