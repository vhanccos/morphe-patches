package app.morphe.extension.youtube.patches;

import android.media.AudioTrack;
import android.media.PlaybackParams;

import java.lang.ref.WeakReference;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.youtube.settings.Settings;

/**
 * Extension logic for skipping silence / fast forwarding in silence using ExoPlayer / AudioTrack.
 */
public final class SkipSilencePatch {

    private static WeakReference<AudioTrack> audioTrackRef = new WeakReference<>(null);

    /**
     * Injection point.
     * Called when YouTube ExoPlayer initializes the AudioTrack wrapper.
     */
    public static void setAudioTrack(AudioTrack track) {
        if (track == null) return;
        audioTrackRef = new WeakReference<>(track);
        applySkipSilence();
    }

    /**
     * Toggles or sets skip silence state.
     */
    public static void setSkipSilenceEnabled(boolean enabled) {
        Settings.SKIP_SILENCE.save(enabled);
        applySkipSilence();
    }

    public static boolean isSkipSilenceEnabled() {
        return Settings.SKIP_SILENCE.get();
    }

    /**
     * Injection point.
     * Reset or re-apply skip silence when starting a new video.
     */
    public static void resetSkipSilence() {
        applySkipSilence();
    }

    public static void applySkipSilence() {
        try {
            final boolean enabled = isSkipSilenceEnabled();
            final AudioTrack track = audioTrackRef.get();
            if (track == null) return;

            try {
                PlaybackParams params = track.getPlaybackParams();
                if (params == null) {
                    params = new PlaybackParams();
                }
                float currentSpeed = params.getSpeed();
                if (enabled) {
                    if (currentSpeed <= 1.0f) {
                        params.setSpeed(2.0f);
                    }
                } else {
                    if (currentSpeed > 1.0f && currentSpeed <= 2.0f) {
                        params.setSpeed(1.0f);
                    }
                }
                track.setPlaybackParams(params);
            } catch (Exception ignored) {}
        } catch (Exception ex) {
            Logger.printException(() -> "applySkipSilence failure", ex);
        }
    }
}
