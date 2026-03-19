// Controls playing/pausing songs.

import javax.sound.sampled.*;
import java.io.File;

public class AudioController {
    // Keep reference to the clip so it doesn't get garbage collected
    private Clip currentClip;
    public boolean clipPaused = false;

    // Optional callback fired on the audio thread when a song finishes naturally
    private Runnable onSongEnd = null;

    public void setOnSongEnd(Runnable callback) {
        this.onSongEnd = callback;
    }

    public Clip getcurrentClip() {
        return currentClip;
    }

    public void playSound(String filePath) {
        try {
            if (filePath.endsWith(".wav")) {
                // Save current volume before closing the old clip so it persists to the new one
                float savedVolume = 0.1f;
                if (currentClip != null && currentClip.isOpen()) {
                    savedVolume = getVolume();
                    currentClip.close();
                }

                AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(new File(filePath));
                currentClip = AudioSystem.getClip();
                currentClip.open(audioInputStream);

                // Reapply the saved volume to the new clip
                setVolume(savedVolume);

                // Fire onSongEnd callback when the clip stops naturally (not from pause/seek)
                currentClip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP) {
                        Clip c = (Clip) event.getLine();
                        // Only treat it as a natural end if we're at (or past) the end of the clip
                        if (!clipPaused && c.getMicrosecondPosition() >= c.getMicrosecondLength() - 10_000) {
                            System.out.println("Audio finished playing naturally");
                            if (onSongEnd != null) onSongEnd.run();
                        }
                    }
                });

                currentClip.start();
                System.out.println("Audio started playing");
                clipPaused = false;

                // NOTE: Thread.sleep removed — Clip plays on its own audio thread
                // and blocking here prevented the progress timer from ever starting.

            } else {
                System.out.println("Unsupported audio format. Please use .wav files.");
            }
        } catch (Exception ex) {
            System.out.println("Error playing sound: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public void pause() {
        if (currentClip != null) {
            currentClip.stop();

            if (!clipPaused) {
                clipPaused = true;
            }
        }
    }

    public void resume() {
        if (currentClip != null) {
            currentClip.start();
            if (clipPaused) {
                clipPaused = false;
            }
        }
    }

    /**
     * Sets the playback position in microseconds.
     * Mutes audio during the seek to prevent the loud click/pop,
     * then restores volume immediately after repositioning.
     */
    public void setPosition(long microseconds) {
        if (currentClip != null && currentClip.isOpen()) {
            long maxPosition = currentClip.getMicrosecondLength();

            if (microseconds < 0) microseconds = 0;
            else if (microseconds > maxPosition) microseconds = maxPosition;

            // Save volume, mute, seek, restore — avoids the loud scrub pop
            float savedVolume = getVolume();
            setVolume(0.0001f); // effectively silent but avoids log10(0) = -Inf
            currentClip.setMicrosecondPosition(microseconds);
            setVolume(savedVolume);

            // Ensure playback continues if it was running
            if (!clipPaused && !currentClip.isRunning()) {
                currentClip.start();
            }
        }
    }

    // https://stackoverflow.com/questions/40514910/set-volume-of-java-clip
    // Converts the logarithmic dB scale to a linear value for simplicity.
    // Usable range: 0.0 (silent) to 0.3 (loud enough for normal listening).
    // The old range of 2.0 made anything above ~10% painfully loud.

    public float getVolume() {
        if (currentClip != null && currentClip.isOpen()) {
            FloatControl gainControl = (FloatControl) currentClip.getControl(FloatControl.Type.MASTER_GAIN);
            return (float) Math.pow(10f, gainControl.getValue() / 20f);
        }
        return 0.1f; // sensible default matching the new slider midpoint
    }

    public void setVolume(float volume) {
        if (currentClip != null && currentClip.isOpen()) {
            // Clamp to valid range — max 0.3 to keep volume sane across the full slider
            volume = Math.max(0.0001f, Math.min(volume, 0.3f));
            FloatControl gainControl = (FloatControl) currentClip.getControl(FloatControl.Type.MASTER_GAIN);
            gainControl.setValue(20f * (float) Math.log10(volume));
        }
    }
}