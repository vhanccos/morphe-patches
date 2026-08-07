package app.morphe.extension.youtube.patches;

import android.media.AudioTrack;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.youtube.settings.Settings;

/**
 * Extension logic for skipping silence / fast forwarding in silence using ExoPlayer.
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

            Class<?> clazz = track.getClass();
            while (clazz != null && clazz != Object.class) {
                for (Method method : clazz.getDeclaredMethods()) {
                    if (method.getParameterTypes().length == 1
                            && method.getParameterTypes()[0] == boolean.class
                            && method.getReturnType() == void.class) {
                        try {
                            method.setAccessible(true);
                            method.invoke(track, enabled);
                        } catch (Exception ignored) {}
                    }
                }
                for (Field field : clazz.getDeclaredFields()) {
                    if (field.getType() == boolean.class) {
                        try {
                            field.setAccessible(true);
                            field.setBoolean(track, enabled);
                        } catch (Exception ignored) {}
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Exception ex) {
            Logger.printException(() -> "applySkipSilence failure", ex);
        }
    }
}
