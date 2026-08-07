package app.morphe.extension.youtube.patches;

import android.util.Log;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import app.morphe.extension.youtube.settings.Settings;

/**
 * Safe extension logic for skipping silence in ExoPlayer targeting SilenceSkippingAudioProcessor specifically.
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
        if (target == null || depth > 3) return;

        Class<?> clazz = target.getClass();
        while (clazz != null && clazz != Object.class) {
            String className = clazz.getName().toLowerCase();

            // Check if target is SilenceSkippingAudioProcessor (has byte[] buffer field and long skippedFrames field)
            boolean isSilenceProcessor = className.contains("silence");
            if (!isSilenceProcessor) {
                boolean hasByteArray = false;
                boolean hasLong = false;
                for (Field f : clazz.getDeclaredFields()) {
                    if (f.getType() == byte[].class) hasByteArray = true;
                    if (f.getType() == long.class) hasLong = true;
                }
                isSilenceProcessor = (hasByteArray && hasLong);
            }

            if (isSilenceProcessor) {
                Log.i(TAG, "Found SilenceSkippingAudioProcessor candidate: " + clazz.getName());
                // Safely invoke setEnabled(boolean) on the silence processor
                for (Method m : clazz.getDeclaredMethods()) {
                    if (m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == boolean.class) {
                        try {
                            m.setAccessible(true);
                            m.invoke(target, enabled);
                            Log.i(TAG, "Successfully invoked " + m.getName() + "(" + enabled + ") on " + clazz.getName());
                        } catch (Exception ex) {
                            Log.e(TAG, "Failed invoking " + m.getName(), ex);
                        }
                    }
                }
                for (Field f : clazz.getDeclaredFields()) {
                    if (f.getType() == boolean.class) {
                        try {
                            f.setAccessible(true);
                            f.setBoolean(target, enabled);
                            Log.i(TAG, "Set boolean field " + f.getName() + "=" + enabled + " on " + clazz.getName());
                        } catch (Exception ignored) {}
                    }
                }
            } else {
                // Recursively inspect sub-fields safely
                for (Field f : clazz.getDeclaredFields()) {
                    try {
                        f.setAccessible(true);
                        Object child = f.get(target);
                        if (child != null && !child.getClass().isPrimitive() && !child.getClass().getName().startsWith("java.lang.")) {
                            scanAndApply(child, enabled, depth + 1);
                        }
                    } catch (Exception ignored) {}
                }
            }

            clazz = clazz.getSuperclass();
        }
    }
}
