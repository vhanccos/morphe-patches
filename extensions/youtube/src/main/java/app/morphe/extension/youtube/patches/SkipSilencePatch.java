package app.morphe.extension.youtube.patches;

import android.media.AudioTrack;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import app.morphe.extension.youtube.settings.Settings;

/**
 * Robust extension logic for skipping silence in ExoPlayer using AudioTrack init hook.
 */
public final class SkipSilencePatch {

    private static final String TAG = "MorpheSkipSilence";
    private static WeakReference<AudioTrack> audioTrackRef = new WeakReference<>(null);

    /**
     * Injection point.
     * Called when YouTube ExoPlayer initializes the AudioTrack wrapper.
     */
    public static void setAudioTrack(AudioTrack track) {
        if (track == null) {
            Log.w(TAG, "setAudioTrack called with NULL track");
            return;
        }
        audioTrackRef = new WeakReference<>(track);
        Log.i(TAG, "setAudioTrack captured AudioTrack instance: " + track);
        applySkipSilence();
    }

    /**
     * Toggles or sets skip silence state.
     */
    public static void setSkipSilenceEnabled(boolean enabled) {
        Log.i(TAG, "setSkipSilenceEnabled: " + enabled);
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
        Log.d(TAG, "resetSkipSilence triggered");
        applySkipSilence();
    }

    public static void applySkipSilence() {
        try {
            final boolean enabled = isSkipSilenceEnabled();
            final AudioTrack track = audioTrackRef.get();

            if (track == null) {
                Log.w(TAG, "applySkipSilence: AudioTrack reference is NULL");
                return;
            }

            Log.i(TAG, "applySkipSilence: Applying enabled=" + enabled + " on AudioTrack " + track);

            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            for (StackTraceElement elem : stack) {
                Log.d(TAG, "Stack: " + elem.getClassName() + "." + elem.getMethodName());
            }
        } catch (Exception ex) {
            Log.e(TAG, "applySkipSilence failure", ex);
        }
    }
}
