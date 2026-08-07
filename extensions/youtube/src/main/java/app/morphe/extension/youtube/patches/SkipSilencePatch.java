package app.morphe.extension.youtube.patches;

import android.util.Log;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import app.morphe.extension.youtube.settings.Settings;

/**
 * Robust extension logic for skipping silence in ExoPlayer with recursive audio wrapper object scanning.
 */
public final class SkipSilencePatch {

    private static final String TAG = "MorpheSkipSilence";
    private static WeakReference<Object> audioSinkRef = new WeakReference<>(null);

    /**
     * Injection point.
     * Called when YouTube ExoPlayer initializes the AudioTrack wrapper / audio output object.
     */
    public static void setAudioSink(Object audioSink) {
        if (audioSink == null) {
            Log.w(TAG, "setAudioSink called with NULL object");
            return;
        }
        audioSinkRef = new WeakReference<>(audioSink);
        Log.i(TAG, "setAudioSink captured root object: " + audioSink.getClass().getName());
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
            final Object root = audioSinkRef.get();

            if (root == null) {
                Log.w(TAG, "applySkipSilence: audioSink reference is NULL (not captured yet)");
                return;
            }

            Log.i(TAG, "applySkipSilence: Applying enabled=" + enabled + " on root " + root.getClass().getName());
            scanAndApply(root, enabled, 0);
        } catch (Exception ex) {
            Log.e(TAG, "applySkipSilence: Critical exception occurred", ex);
        }
    }

    private static void scanAndApply(Object target, boolean enabled, int depth) {
        if (target == null || depth > 3) return;

        Class<?> clazz = target.getClass();
        while (clazz != null && clazz != Object.class) {
            // 1. Invoke single-boolean methods on target containing "silence" or matching SilenceSkippingAudioProcessor
            for (Method m : clazz.getDeclaredMethods()) {
                if (m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == boolean.class) {
                    String name = m.getName().toLowerCase();
                    if (name.contains("silence") || clazz.getName().toLowerCase().contains("silence")) {
                        try {
                            m.setAccessible(true);
                            m.invoke(target, enabled);
                            Log.i(TAG, "Successfully invoked " + m.getName() + "(" + enabled + ") on " + clazz.getName());
                        } catch (Exception ex) {
                            Log.e(TAG, "Failed invoking " + m.getName() + " on " + clazz.getName(), ex);
                        }
                    }
                }
            }

            // 2. Recursively inspect sub-fields
            for (Field f : clazz.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object child = f.get(target);
                    if (child != null && !child.getClass().isPrimitive() && !child.getClass().getName().startsWith("java.lang.")) {
                        String childClassName = child.getClass().getName().toLowerCase();
                        if (childClassName.contains("silence") || childClassName.contains("sink") || childClassName.contains("processor") || childClassName.contains("audio")) {
                            scanAndApply(child, enabled, depth + 1);
                        }
                    }
                } catch (Exception ignored) {}
            }

            clazz = clazz.getSuperclass();
        }
    }
}
