package app.morphe.extension.youtube.patches;

import android.util.Log;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import app.morphe.extension.youtube.settings.Settings;

/**
 * Robust extension logic for skipping silence in ExoPlayer by capturing YouTube's cxj / AudioSink wrapper.
 */
public final class SkipSilencePatch {

    private static final String TAG = "MorpheSkipSilence";
    private static WeakReference<Object> audioSinkRef = new WeakReference<>(null);

    /**
     * Injection point.
     * Receives p0 (cxj instance) from AudioTrackWrapperInitFingerprint.
     */
    public static void setAudioSink(Object audioSink) {
        if (audioSink == null) {
            Log.w(TAG, "setAudioSink called with NULL object");
            return;
        }
        audioSinkRef = new WeakReference<>(audioSink);
        Log.i(TAG, "setAudioSink captured wrapper object: " + audioSink.getClass().getName());
        applySkipSilence();
    }

    public static void setSkipSilenceEnabled(boolean enabled) {
        Log.i(TAG, "setSkipSilenceEnabled: " + enabled);
        Settings.SKIP_SILENCE.save(enabled);
        applySkipSilence();
    }

    public static boolean isSkipSilenceEnabled() {
        return Settings.SKIP_SILENCE.get();
    }

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
            Log.e(TAG, "applySkipSilence failure", ex);
        }
    }

    private static void scanAndApply(Object target, boolean enabled, int depth) {
        if (target == null || depth > 4) return;

        Class<?> clazz = target.getClass();
        while (clazz != null && clazz != Object.class) {
            Log.d(TAG, "Depth " + depth + " inspecting class: " + clazz.getName());

            // 1. Try any boolean setter on target
            for (Method m : clazz.getDeclaredMethods()) {
                if (m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == boolean.class) {
                    try {
                        m.setAccessible(true);
                        m.invoke(target, enabled);
                        Log.i(TAG, "Invoked " + m.getName() + "(" + enabled + ") on " + clazz.getName());
                    } catch (Exception ex) {
                        Log.d(TAG, "Method " + m.getName() + " skipped: " + ex.getMessage());
                    }
                }
            }

            // 2. Recursively inspect fields
            for (Field f : clazz.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object child = f.get(target);
                    if (child != null && !child.getClass().isPrimitive() && !child.getClass().getName().startsWith("java.lang.")) {
                        Log.d(TAG, "Depth " + depth + " field " + f.getName() + " -> " + child.getClass().getName());
                        scanAndApply(child, enabled, depth + 1);
                    }
                } catch (Exception ignored) {}
            }

            clazz = clazz.getSuperclass();
        }
    }
}
