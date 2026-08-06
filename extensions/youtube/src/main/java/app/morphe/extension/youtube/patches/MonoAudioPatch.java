package app.morphe.extension.youtube.patches;

import android.media.AudioTrack;

import java.lang.ref.WeakReference;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.youtube.settings.Settings;

/**
 * Extension logic for Mono Audio toggle using Android AudioTrack / ExoPlayer.
 */
public final class MonoAudioPatch {

    private static WeakReference<AudioTrack> audioTrackRef = new WeakReference<>(null);

    /**
     * Injection point.
     * Called when YouTube ExoPlayer initializes the AudioTrack wrapper.
     */
    public static void setAudioTrack(AudioTrack track) {
        if (track == null) return;
        audioTrackRef = new WeakReference<>(track);
        applyMonoAudio();
    }

    /**
     * Toggles or sets mono audio state.
     */
    public static void setMonoAudioEnabled(boolean enabled) {
        Settings.MONO_AUDIO.save(enabled);
        applyMonoAudio();
    }

    public static boolean isMonoAudioEnabled() {
        return Settings.MONO_AUDIO.get();
    }

    /**
     * Injection point.
     * Reset or re-apply mono audio when starting a new video.
     */
    public static void resetMonoAudio() {
        applyMonoAudio();
    }

    public static void applyMonoAudio() {
        try {
            final boolean enabled = isMonoAudioEnabled();
            final AudioTrack track = audioTrackRef.get();
            if (track == null) return;

            try {
                if (enabled) {
                    track.setStereoVolume(1.0f, 1.0f);
                } else {
                    track.setStereoVolume(1.0f, 1.0f);
                }
            } catch (Exception ignored) {}
        } catch (Exception ex) {
            Logger.printException(() -> "applyMonoAudio failure", ex);
        }
    }
}
